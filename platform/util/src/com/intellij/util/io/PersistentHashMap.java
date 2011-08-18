/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.LimitedPool;
import com.intellij.util.containers.SLRUCache;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 18, 2007
 */
public class PersistentHashMap<Key, Value> extends PersistentEnumeratorDelegate<Key>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.PersistentHashMap");

  private PersistentHashMapValueStorage myValueStorage;
  private final DataExternalizer<Value> myValueExternalizer;
  private static final long NULL_ADDR = 0;
  private static final int INITIAL_INDEX_SIZE;
  static {
    String property = System.getProperty("idea.initialIndexSize");
    INITIAL_INDEX_SIZE = property == null ? 4 * 1024 : Integer.valueOf(property);
  }

  @NonNls
  public static final String DATA_FILE_EXTENSION = ".values";
  private long myLiveAndGarbageKeysCounter; // first four bytes contain live keys count (updated via LIVE_KEY_MASK), last four bytes - number of dead keys
  private static final long LIVE_KEY_MASK = (1L << 32);
  private static final long USED_LONG_VALUE_MASK = 1L << 62;
  private static final int POSITIVE_VALUE_SHIFT = 1;
  private final int myParentValueRefOffset;
  private final byte[] myRecordBuffer;
  private final byte[] mySmallRecordBuffer;
  private final boolean myCanReEnumerate;
  private int myWatermarkId;
  private boolean myIntAddressForNewRecord;
  private static final boolean doHardConsistencyChecks = false;

  private static class AppendStream extends DataOutputStream {
    private AppendStream() {
      super(new BufferExposingByteArrayOutputStream());
    }

    public int getBufferSize() {
      return ((ByteArrayOutputStream)out).size();
    }
    
    public void writeTo(OutputStream stream) throws IOException {
      ((ByteArrayOutputStream)out).writeTo(stream);
    }

    public void reset() {
      ((ByteArrayOutputStream)out).reset();
    }

    public byte[] toByteArray() {
      return ((ByteArrayOutputStream)out).toByteArray();
    }
    
    public ByteSequence getInternalBuffer() {
      final BufferExposingByteArrayOutputStream _out = (BufferExposingByteArrayOutputStream)out;
      return new ByteSequence(_out.getInternalBuffer(), 0, _out.size());
    }
  }

  private final LimitedPool<AppendStream> myStreamPool = new LimitedPool<AppendStream>(10, new LimitedPool.ObjectFactory<AppendStream>() {
    public AppendStream create() {
      return new AppendStream();
    }

    public void cleanup(final AppendStream appendStream) {
      appendStream.reset();
    }
  });  

  private final SLRUCache<Key, AppendStream> myAppendCache = new SLRUCache<Key, AppendStream>(16 * 1024, 4 * 1024) {
    @NotNull
    public AppendStream createValue(final Key key) {
      return myStreamPool.alloc();
    }

    protected void onDropFromCache(final Key key, final AppendStream value) {
      try {
        final ByteSequence bytes = value.getInternalBuffer();
        final int id = enumerate(key);
        HeaderRecord oldHeaderRecord = readValueId(id);

        HeaderRecord headerRecord = new HeaderRecord(
          myValueStorage.appendBytes(bytes, oldHeaderRecord.address)
        );

        updateValueId(id, headerRecord, oldHeaderRecord, key, 0);
        if (oldHeaderRecord == HeaderRecord.EMPTY) myLiveAndGarbageKeysCounter += LIVE_KEY_MASK;

        myStreamPool.recycle(value);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  };

  private boolean canUseIntAddressForNewRecord(long size) {
    return myCanReEnumerate ? size + POSITIVE_VALUE_SHIFT < Integer.MAX_VALUE: false;
  }

  private final LowMemoryWatcher myAppendCacheFlusher = LowMemoryWatcher.register(new LowMemoryWatcher.ForceableAdapter() {
    public void force() {
      //System.out.println("Flushing caches: " + myFile.getPath());
      synchronized (PersistentHashMap.this) {
        synchronized (PersistentEnumerator.ourLock) {
          clearAppenderCaches();
        }
      }
    }
  });
  
  public PersistentHashMap(final File file, KeyDescriptor<Key> keyDescriptor, DataExternalizer<Value> valueExternalizer) throws IOException {
    this(file, keyDescriptor, valueExternalizer, INITIAL_INDEX_SIZE);
  }
  
  public PersistentHashMap(final File file, KeyDescriptor<Key> keyDescriptor, DataExternalizer<Value> valueExternalizer, final int initialSize) throws IOException {
    super(checkDataFiles(file), keyDescriptor, initialSize);

    final PersistentEnumeratorBase.RecordBufferHandler<PersistentEnumeratorBase> recordHandler = myEnumerator.getRecordHandler();
    myParentValueRefOffset = recordHandler.getRecordBuffer(myEnumerator).length;
    myRecordBuffer = new byte[myParentValueRefOffset + 8];
    mySmallRecordBuffer = new byte[myParentValueRefOffset + 4];

    myEnumerator.setRecordHandler(new PersistentEnumeratorBase.RecordBufferHandler<PersistentEnumeratorBase>() {
      @Override
      int recordWriteOffset(PersistentEnumeratorBase enumerator, byte[] buf) {
        return recordHandler.recordWriteOffset(enumerator, buf);
      }

      @NotNull
      @Override
      byte[] getRecordBuffer(PersistentEnumeratorBase enumerator) {
        return myIntAddressForNewRecord ? mySmallRecordBuffer : myRecordBuffer;
      }

      @Override
      void setupRecord(PersistentEnumeratorBase enumerator, int hashCode, int dataOffset, byte[] buf) {
        recordHandler.setupRecord(enumerator, hashCode, dataOffset, buf);
        for (int i = myParentValueRefOffset; i < buf.length; i++) {
          buf[i] = 0;
        }
      }
    });

    myEnumerator.setMarkCleanCallback(
      new Flushable() {
        @Override
        public void flush() throws IOException {
          myEnumerator.putMetaData(myLiveAndGarbageKeysCounter);
          myEnumerator.putMetaData2(myWatermarkId);
        }
      }
    );

    try {
      myValueExternalizer = valueExternalizer;
      myValueStorage = PersistentHashMapValueStorage.create(getDataFile(file).getPath());
      myLiveAndGarbageKeysCounter = myEnumerator.getMetaData();
      myWatermarkId = (int)myEnumerator.getMetaData2();
      myCanReEnumerate = myEnumerator.canReEnumerate();

      if (makesSenseToCompact()) {
        compact();
      }
    }
    catch (IOException e) {
      throw e; // rethrow
    }
    catch (Throwable t) {
      LOG.error(t);
      throw new PersistentEnumerator.CorruptedException(file);
    }
  }

  public int getGarbageSize() {
    return (int)myLiveAndGarbageKeysCounter;
  }

  public File getBaseFile() {
    return myEnumerator.myFile;
  }

  private boolean makesSenseToCompact() {
    final long fileSize = getDataFile(myEnumerator.myFile).length();
    if (fileSize > 5 * 1024 * 1024) { // file is longer than 5MB and more than 50% of keys is garbage
      int liveKeys = (int)(myLiveAndGarbageKeysCounter / LIVE_KEY_MASK);
      int oldKeys = (int)(myLiveAndGarbageKeysCounter & 0xFFFFFFFF);
      return oldKeys > liveKeys;
    }
    return false;
  }

  private static File checkDataFiles(final File file) {
    if (!file.exists()) {
      deleteFilesStartingWith(getDataFile(file));
    }
    return file;
  }

  public static void deleteFilesStartingWith(File prefixFile) {
    final String baseName = prefixFile.getName();
    final File[] files = prefixFile.getParentFile().listFiles(new FileFilter() {
      public boolean accept(final File pathName) {
        return pathName.getName().startsWith(baseName);
      }
    });
    if (files != null) {
      for (File f : files) {
        FileUtil.delete(f);
      }
    }
  }

  private static File getDataFile(final File file) {
    return new File(file.getParentFile(), file.getName() + DATA_FILE_EXTENSION);
  }

  public synchronized void put(Key key, Value value) throws IOException {
    synchronized (PersistentEnumerator.ourLock) {
      myEnumerator.markDirty(true);
      myAppendCache.remove(key);

      final AppendStream record = new AppendStream();
      myValueExternalizer.save(record, value);
      final ByteSequence bytes = record.getInternalBuffer();
      final int id = enumerate(key);

      HeaderRecord oldheader = readValueId(id);
      if (oldheader != HeaderRecord.EMPTY) myLiveAndGarbageKeysCounter++;
      myLiveAndGarbageKeysCounter += LIVE_KEY_MASK;

      HeaderRecord header = new HeaderRecord(myValueStorage.appendBytes(bytes, 0));

      updateValueId(id, header, oldheader, key, 0);
    }
  }

  @Override
  public synchronized int enumerate(Key name) throws IOException {
    myIntAddressForNewRecord = canUseIntAddressForNewRecord(myValueStorage.getSize());
    return super.enumerate(name);
  }

  public interface ValueDataAppender {
    void append(DataOutput out) throws IOException;
  }
  
  public synchronized void appendData(Key key, ValueDataAppender appender) throws IOException {
    synchronized (PersistentEnumerator.ourLock) {
      myEnumerator.markDirty(true);
      
      final AppendStream stream = myAppendCache.get(key);
      appender.append(stream);
    }
  }

  /**
   * Process all keys registered in the map. Note that keys which were removed after {@link #compact()} call will be processed as well. Use
   * {@link #processKeysWithExistingMapping(com.intellij.util.Processor)} to process only keys with existing mappings
   */
  public synchronized boolean processKeys(Processor<Key> processor) throws IOException {
    synchronized (PersistentEnumerator.ourLock) {
      myAppendCache.clear();
      return myEnumerator.iterateData(processor);
    }
  }

  public Collection<Key> getAllKeysWithExistingMapping() throws IOException {
    final List<Key> values = new ArrayList<Key>();
    processKeysWithExistingMapping(new CommonProcessors.CollectProcessor<Key>(values));
    return values;
  }

  public synchronized boolean processKeysWithExistingMapping(Processor<Key> processor) throws IOException {
    synchronized (PersistentEnumerator.ourLock) {
      return myEnumerator.processAllDataObject(processor, new PersistentEnumerator.DataFilter() {
        public boolean accept(final int id) {
          return readValueId(id).address != NULL_ADDR;
        }
      });
    }
  }

  public synchronized Value get(Key key) throws IOException {
    synchronized (PersistentEnumerator.ourLock) {
      myAppendCache.remove(key);
      final int id = tryEnumerate(key);
      if (id == PersistentEnumerator.NULL_ID) {
        return null;
      }
      final HeaderRecord oldHeader = readValueId(id);
      if (oldHeader.address == PersistentEnumerator.NULL_ID) {
        return null;
      }
      
      Pair<Long, byte[]> readResult = myValueStorage.readBytes(oldHeader.address);
      if (readResult.first != null && readResult.first != oldHeader.address) {
        myEnumerator.markDirty(true);

        updateValueId(id, new HeaderRecord(readResult.first), oldHeader, key, 0);
        if (oldHeader != HeaderRecord.EMPTY) myLiveAndGarbageKeysCounter++;
      }

      final DataInputStream input = new DataInputStream(new ByteArrayInputStream(readResult.second));
      try {
        return myValueExternalizer.read(input);
      }
      finally {
        input.close();
      }
    }
  }

  public synchronized boolean containsMapping(Key key) throws IOException {
    synchronized (PersistentEnumerator.ourLock) {
      myAppendCache.remove(key);
      final int id = tryEnumerate(key);
      if (id == PersistentEnumerator.NULL_ID) {
        return false;
      }
      return readValueId(id).address != NULL_ADDR;
    }
  }

  public synchronized void remove(Key key) throws IOException {
    synchronized (PersistentEnumerator.ourLock) {
      myAppendCache.remove(key);
      final int id = tryEnumerate(key);
      if (id == PersistentEnumerator.NULL_ID) {
        return;
      }
      myEnumerator.markDirty(true);

      final HeaderRecord record = readValueId(id);
      if (record != HeaderRecord.EMPTY) myLiveAndGarbageKeysCounter++;

      updateValueId(id, HeaderRecord.EMPTY, record, key, 0);
    }
  }

  public final synchronized void markDirty() throws IOException {
    myEnumerator.markDirty(true);
  }

  public synchronized void force() {
    synchronized (PersistentEnumerator.ourLock) {
      try {
        clearAppenderCaches();
      }
      finally {
        super.force();
      }
    }
  }

  private void clearAppenderCaches() {
    myAppendCache.clear();
    myValueStorage.force();
  }

  public synchronized void close() throws IOException {
    synchronized (PersistentEnumerator.ourLock) {
      try {
        myAppendCacheFlusher.stop();
        myAppendCache.clear();
        myValueStorage.dispose();
      }
      finally {
        super.close();
      }
    }
  }
  
  // made public for tests
  public synchronized void compact() throws IOException {
    synchronized (PersistentEnumerator.ourLock) {
      final long now = System.currentTimeMillis();
      final String newPath = getDataFile(myEnumerator.myFile).getPath() + ".new";
      final PersistentHashMapValueStorage newStorage = PersistentHashMapValueStorage.create(newPath);
      myValueStorage.switchToCompactionMode();
      myLiveAndGarbageKeysCounter = 0;

      traverseAllRecords(new PersistentEnumerator.RecordsProcessor() {
        public boolean process(final int keyId) throws IOException {
          final HeaderRecord record = readValueId(keyId);
          if (record.address != NULL_ADDR) {            
            Pair<Long, byte[]> readResult = myValueStorage.readBytes(record.address);
            HeaderRecord value = new HeaderRecord(newStorage.appendBytes(new ByteSequence(readResult.second), 0));
            updateValueId(keyId, value, record, null, getCurrentKey());
            myLiveAndGarbageKeysCounter += LIVE_KEY_MASK;
          }
          return true;
        }
      });

      myValueStorage.dispose();
      newStorage.dispose();

      FileUtil.rename(new File(newPath), getDataFile(myEnumerator.myFile));

      myValueStorage = PersistentHashMapValueStorage.create(getDataFile(myEnumerator.myFile).getPath());
      LOG.info("Compacted " + myEnumerator.myFile.getPath() + " in " + (System.currentTimeMillis() - now) + "ms.");

      myEnumerator.putMetaData(myLiveAndGarbageKeysCounter);
    }
  }

  private HeaderRecord readValueId(final int keyId) {
    long address = myEnumerator.myStorage.getInt(keyId + myParentValueRefOffset);
    if (address == 0 || address == -POSITIVE_VALUE_SHIFT) {
      return HeaderRecord.EMPTY;
    }

    if (address < 0) {
      address = -address - POSITIVE_VALUE_SHIFT;
    } else {
      int value = myEnumerator.myStorage.getInt(keyId + myParentValueRefOffset + 4);
      address = ((address << 32) + value) & ~USED_LONG_VALUE_MASK;
    }

    return new HeaderRecord(address);
  }

  private int smallKeys;
  private int largeKeys;
  private int transformedKeys;
  private int requests;

  private int updateValueId(int keyId, HeaderRecord value, HeaderRecord oldValue, @Nullable Key key, int processingKey) throws IOException {
    final boolean newKey = oldValue == null || oldValue.address == NULL_ADDR;
    if (newKey) ++requests;
    boolean defaultSizeInfo = true;

    if (myCanReEnumerate) {
      if (canUseIntAddressForNewRecord(value.address)) {
        defaultSizeInfo = false;
        myEnumerator.myStorage.putInt(keyId + myParentValueRefOffset, -(int)(value.address + POSITIVE_VALUE_SHIFT));
        if (newKey) ++smallKeys;
      } else {
        if (newKey && myWatermarkId == 0) myWatermarkId = keyId;
        if (keyId < myWatermarkId && (oldValue == null || canUseIntAddressForNewRecord(oldValue.address))) {
          // keyId is result of enumerate, if we do reenumerate then it is no longer accessible unless somebody cached it
          myIntAddressForNewRecord = false;
          keyId = myEnumerator.reenumerate(key == null ? myEnumerator.getValue(keyId, processingKey) : key);
          ++transformedKeys;
        }
      }
    }

    if (defaultSizeInfo) {
      myEnumerator.myStorage.putLong(keyId + myParentValueRefOffset, value.address | USED_LONG_VALUE_MASK);
      if (newKey) ++largeKeys;
    }

    if (newKey && requests % IOStatistics.KEYS_FACTOR == 0 && IOStatistics.DEBUG) {
      IOStatistics.dump("small:"+smallKeys + ", large:" + largeKeys + ", transformed:"+transformedKeys +
                        ",@"+getBaseFile().getPath());
    }
    if (doHardConsistencyChecks) {
      HeaderRecord checkRecord = readValueId(keyId);
      if (checkRecord.address != value.address) {
        assert false:value.address;
      }
    }
    return keyId;
  }

  private static class HeaderRecord {
    final long address;

    HeaderRecord(long address) {
      this.address = address;
    }

    static final HeaderRecord EMPTY = new HeaderRecord(NULL_ADDR);
  }
}
