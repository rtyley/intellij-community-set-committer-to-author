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
public class PersistentHashMap<Key, Value> extends PersistentEnumeratorDelegate<Key> implements PersistentMap<Key, Value> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.PersistentHashMap");

  private PersistentHashMapValueStorage myValueStorage;
  protected final DataExternalizer<Value> myValueExternalizer;
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
  @NotNull private final byte[] myRecordBuffer;
  @NotNull private final byte[] mySmallRecordBuffer;
  private final boolean myCanReEnumerate;
  private int myWatermarkId;
  private boolean myIntAddressForNewRecord;
  private static final boolean doHardConsistencyChecks = false;

  private static class AppendStream extends DataOutputStream {
    private AppendStream() {
      super(new BufferExposingByteArrayOutputStream());
    }

    private void reset() {
      ((UnsyncByteArrayOutputStream)out).reset();
    }
    
    @NotNull
    private BufferExposingByteArrayOutputStream getInternalBuffer() {
      return (BufferExposingByteArrayOutputStream)out;      
    }
  }

  private final LimitedPool<AppendStream> myStreamPool = new LimitedPool<AppendStream>(10, new LimitedPool.ObjectFactory<AppendStream>() {
    @Override
    @NotNull
    public AppendStream create() {
      return new AppendStream();
    }

    @Override
    public void cleanup(@NotNull final AppendStream appendStream) {
      appendStream.reset();
    }
  });  

  private final SLRUCache<Key, AppendStream> myAppendCache = new SLRUCache<Key, AppendStream>(16 * 1024, 4 * 1024) {
    @Override
    @NotNull
    public AppendStream createValue(final Key key) {
      return myStreamPool.alloc();
    }

    @Override
    protected void onDropFromCache(final Key key, @NotNull final AppendStream value) {
      synchronized (PersistentEnumerator.ourLock) {
        try {
          final BufferExposingByteArrayOutputStream bytes = value.getInternalBuffer();
          final int id = enumerate(key);
          long oldHeaderRecord = readValueId(id);

          long headerRecord = myValueStorage.appendBytes(bytes.getInternalBuffer(), 0, bytes.size(), oldHeaderRecord);

          updateValueId(id, headerRecord, oldHeaderRecord, key, 0);
          if (oldHeaderRecord == NULL_ADDR) {
            myLiveAndGarbageKeysCounter += LIVE_KEY_MASK;
          }

          myStreamPool.recycle(value);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
  };

  private boolean canUseIntAddressForNewRecord(long size) {
    return myCanReEnumerate ? size + POSITIVE_VALUE_SHIFT < Integer.MAX_VALUE: false;
  }

  private final LowMemoryWatcher myAppendCacheFlusher = LowMemoryWatcher.register(new LowMemoryWatcher.ForceableAdapter() {
    @Override
    public void force() {
      //System.out.println("Flushing caches: " + myFile.getPath());
      dropMemoryCaches();
    }
  });

  public PersistentHashMap(@NotNull final File file, @NotNull KeyDescriptor<Key> keyDescriptor, @NotNull DataExternalizer<Value> valueExternalizer) throws IOException {
    this(file, keyDescriptor, valueExternalizer, INITIAL_INDEX_SIZE);
  }
  
  public PersistentHashMap(@NotNull final File file, @NotNull KeyDescriptor<Key> keyDescriptor, @NotNull DataExternalizer<Value> valueExternalizer, final int initialSize) throws IOException {
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
      void setupRecord(PersistentEnumeratorBase enumerator, int hashCode, int dataOffset, @NotNull byte[] buf) {
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

  public void dropMemoryCaches() {
    synchronized (myEnumerator) {
      synchronized (PersistentEnumerator.ourLock) {
        clearAppenderCaches();
      }
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

  @NotNull
  private static File checkDataFiles(@NotNull final File file) {
    if (!file.exists()) {
      deleteFilesStartingWith(getDataFile(file));
    }
    return file;
  }

  public static void deleteFilesStartingWith(@NotNull File prefixFile) {
    final String baseName = prefixFile.getName();
    final File[] files = prefixFile.getParentFile().listFiles(new FileFilter() {
      @Override
      public boolean accept(@NotNull final File pathName) {
        return pathName.getName().startsWith(baseName);
      }
    });
    if (files != null) {
      for (File f : files) {
        FileUtil.delete(f);
      }
    }
  }

  @NotNull
  private static File getDataFile(@NotNull final File file) {
    return new File(file.getParentFile(), file.getName() + DATA_FILE_EXTENSION);
  }

  @Override
  public final void put(Key key, Value value) throws IOException {
    synchronized (myEnumerator) {
      doPut(key, value);
    }
  }

  protected void doPut(Key key, Value value) throws IOException {
    synchronized (PersistentEnumerator.ourLock) {
      myEnumerator.markDirty(true);
      myAppendCache.remove(key);

      final AppendStream record = new AppendStream();
      myValueExternalizer.save(record, value);
      final BufferExposingByteArrayOutputStream bytes = record.getInternalBuffer();
      final int id = enumerate(key);

      long oldheader = readValueId(id);
      if (oldheader != NULL_ADDR) {
        myLiveAndGarbageKeysCounter++;
      }
      else {
        myLiveAndGarbageKeysCounter += LIVE_KEY_MASK;
      }

      long header = myValueStorage.appendBytes(bytes.getInternalBuffer(), 0, bytes.size(), 0);

      updateValueId(id, header, oldheader, key, 0);
    }
  }

  @Override
  public final int enumerate(Key name) throws IOException {
    synchronized (myEnumerator) {
      myIntAddressForNewRecord = canUseIntAddressForNewRecord(myValueStorage.getSize());
      return super.enumerate(name);
    }
  }

  public interface ValueDataAppender {
    void append(DataOutput out) throws IOException;
  }
  
  public final void appendData(Key key, @NotNull ValueDataAppender appender) throws IOException {
    synchronized (myEnumerator) {
      doAppendData(key, appender);
    }
  }

  protected void doAppendData(Key key, @NotNull ValueDataAppender appender) throws IOException {
    myEnumerator.markDirty(true);

    final AppendStream stream = myAppendCache.get(key);
    appender.append(stream);
  }

  /**
   * Process all keys registered in the map. Note that keys which were removed after {@link #compact()} call will be processed as well. Use
   * {@link #processKeysWithExistingMapping(com.intellij.util.Processor)} to process only keys with existing mappings
   */
  @Override
  public final boolean processKeys(Processor<Key> processor) throws IOException {
    synchronized (myEnumerator) {
      myAppendCache.clear();
      return myEnumerator.iterateData(processor);
    }
  }

  @NotNull
  public Collection<Key> getAllKeysWithExistingMapping() throws IOException {
    final List<Key> values = new ArrayList<Key>();
    processKeysWithExistingMapping(new CommonProcessors.CollectProcessor<Key>(values));
    return values;
  }

  public final boolean processKeysWithExistingMapping(Processor<Key> processor) throws IOException {
    synchronized (myEnumerator) {
      myAppendCache.clear();
      return myEnumerator.processAllDataObject(processor, new PersistentEnumerator.DataFilter() {
        @Override
        public boolean accept(final int id) {
          return readValueId(id) != NULL_ADDR;
        }
      });
    }
  }

  @Override
  public final Value get(Key key) throws IOException {
    synchronized (myEnumerator) {
      return doGet(key);
    }
  }

  @Nullable
  protected Value doGet(Key key) throws IOException {
    synchronized (PersistentEnumerator.ourLock) {
      myAppendCache.remove(key);
      final int id = tryEnumerate(key);
      if (id == PersistentEnumerator.NULL_ID) {
        return null;
      }
      final long oldHeader = readValueId(id);
      if (oldHeader == PersistentEnumerator.NULL_ID) {
        return null;
      }

      Pair<Long, byte[]> readResult = myValueStorage.readBytes(oldHeader);
      if (readResult.first != null && readResult.first != oldHeader) {
        myEnumerator.markDirty(true);

        updateValueId(id, readResult.first, oldHeader, key, 0);
        myLiveAndGarbageKeysCounter++;
      }

      final DataInputStream input = new DataInputStream(new UnsyncByteArrayInputStream(readResult.second));
      try {
        return myValueExternalizer.read(input);
      }
      finally {
        input.close();
      }
    }
  }

  public final boolean containsMapping(Key key) throws IOException {
    synchronized (myEnumerator) {
      return doContainsMapping(key);
    }
  }

  protected boolean doContainsMapping(Key key) throws IOException {
    synchronized (PersistentEnumerator.ourLock) {
      myAppendCache.remove(key);
      final int id = tryEnumerate(key);
      if (id == PersistentEnumerator.NULL_ID) {
        return false;
      }
      return readValueId(id) != NULL_ADDR;
    }
  }

  public final void remove(Key key) throws IOException {
    synchronized (myEnumerator) {
      doRemove(key);
    }
  }

  protected void doRemove(Key key) throws IOException {
    synchronized (PersistentEnumerator.ourLock) {
      myAppendCache.remove(key);
      final int id = tryEnumerate(key);
      if (id == PersistentEnumerator.NULL_ID) {
        return;
      }
      myEnumerator.markDirty(true);

      final long record = readValueId(id);
      if (record != NULL_ADDR) {
        myLiveAndGarbageKeysCounter++;
      }

      updateValueId(id, NULL_ADDR, record, key, 0);
    }
  }

  @Override
  public final void markDirty() throws IOException {
    synchronized (myEnumerator) {
      myEnumerator.markDirty(true);
    }
  }

  @Override
  public final void force() {
    synchronized (myEnumerator) {
      doForce();
    }
  }

  protected void doForce() {
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

  @Override
  public final void close() throws IOException {
    synchronized (myEnumerator) {
      doClose();
    }
  }

  protected void doClose() throws IOException {
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
  public void compact() throws IOException {
    synchronized (myEnumerator) {
      final long now = System.currentTimeMillis();
      final String newPath = getDataFile(myEnumerator.myFile).getPath() + ".new";
      final PersistentHashMapValueStorage newStorage = PersistentHashMapValueStorage.create(newPath);
      myValueStorage.switchToCompactionMode();
      myLiveAndGarbageKeysCounter = 0;

      traverseAllRecords(new PersistentEnumerator.RecordsProcessor() {
        @Override
        public boolean process(final int keyId) throws IOException {
          final long record = readValueId(keyId);
          if (record != NULL_ADDR) {
            Pair<Long, byte[]> readResult = myValueStorage.readBytes(record);
            long value = newStorage.appendBytes(new ByteSequence(readResult.second), 0);
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

  private long readValueId(final int keyId) {
    long address = myEnumerator.myStorage.getInt(keyId + myParentValueRefOffset);
    if (address == 0 || address == -POSITIVE_VALUE_SHIFT) {
      return NULL_ADDR;
    }

    if (address < 0) {
      address = -address - POSITIVE_VALUE_SHIFT;
    } else {
      int value = myEnumerator.myStorage.getInt(keyId + myParentValueRefOffset + 4);
      address = ((address << 32) + value) & ~USED_LONG_VALUE_MASK;
    }

    return address;
  }

  private int smallKeys;
  private int largeKeys;
  private int transformedKeys;
  private int requests;

  private int updateValueId(int keyId, long value, long oldValue, @Nullable Key key, int processingKey) throws IOException {
    final boolean newKey = oldValue == NULL_ADDR;
    if (newKey) ++requests;
    boolean defaultSizeInfo = true;

    if (myCanReEnumerate) {
      if (canUseIntAddressForNewRecord(value)) {
        defaultSizeInfo = false;
        myEnumerator.myStorage.putInt(keyId + myParentValueRefOffset, -(int)(value + POSITIVE_VALUE_SHIFT));
        if (newKey) ++smallKeys;
      } else {
        if (newKey && myWatermarkId == 0) myWatermarkId = keyId;
        if (keyId < myWatermarkId && (oldValue == NULL_ADDR || canUseIntAddressForNewRecord(oldValue))) {
          // keyId is result of enumerate, if we do reenumerate then it is no longer accessible unless somebody cached it
          myIntAddressForNewRecord = false;
          keyId = myEnumerator.reenumerate(key == null ? myEnumerator.getValue(keyId, processingKey) : key);
          ++transformedKeys;
        }
      }
    }

    if (defaultSizeInfo) {
      myEnumerator.myStorage.putLong(keyId + myParentValueRefOffset, value | USED_LONG_VALUE_MASK);
      if (newKey) ++largeKeys;
    }

    if (newKey && requests % IOStatistics.KEYS_FACTOR == 0 && IOStatistics.DEBUG) {
      IOStatistics.dump("small:"+smallKeys + ", large:" + largeKeys + ", transformed:"+transformedKeys +
                        ",@"+getBaseFile().getPath());
    }
    if (doHardConsistencyChecks) {
      long checkRecord = readValueId(keyId);
      if (checkRecord != value) {
        assert false:value;
      }
    }
    return keyId;
  }
}
