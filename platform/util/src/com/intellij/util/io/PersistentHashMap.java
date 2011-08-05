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
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.LimitedPool;
import com.intellij.util.containers.SLRUCache;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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
  private int myGarbageSize;
  private final int myParentValueRefOffset;
  private final byte[] myRecordBuffer;

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
        final int id = enumerate(key);
        HeaderRecord headerRecord = readValueId(id);

        final ByteSequence bytes = value.getInternalBuffer();

        headerRecord.size += bytes.getLength();
        headerRecord.address = myValueStorage.appendBytes(bytes, headerRecord.address);

        updateValueId(id, headerRecord);

        myStreamPool.recycle(value);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  };

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
    myRecordBuffer = new byte[myParentValueRefOffset + 8 + 4];
    
    myEnumerator.setRecordHandler(new PersistentEnumeratorBase.RecordBufferHandler<PersistentEnumeratorBase>() {
      @Override
      int recordWriteOffset(PersistentEnumeratorBase enumerator, byte[] buf) {
        return recordHandler.recordWriteOffset(enumerator, buf);
      }

      @Override
      byte[] getRecordBuffer(PersistentEnumeratorBase enumerator) {
        return myRecordBuffer;
      }

      @Override
      void setupRecord(PersistentEnumeratorBase enumerator, int hashCode, int dataOffset, byte[] buf) {
        recordHandler.setupRecord(enumerator, hashCode, dataOffset, buf);
        for (int i = myParentValueRefOffset; i < myRecordBuffer.length; i++) {
          buf[i] = 0;
        }
      }
    });

    myEnumerator.setMarkCleanCallback(
      new Flushable() {
        @Override
        public void flush() throws IOException {
          myEnumerator.putMetaData(myGarbageSize);
        }
      }
    );

    try {
      myValueExternalizer = valueExternalizer;
      myValueStorage = PersistentHashMapValueStorage.create(getDataFile(file).getPath());
      myGarbageSize = myEnumerator.getMetaData();

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
    return myGarbageSize;
  }

  public File getBaseFile() {
    return myEnumerator.myFile;
  }

  private boolean makesSenseToCompact() {
    final long fileSize = getDataFile(myEnumerator.myFile).length();
    return fileSize > 5 * 1024 * 1024 && myGarbageSize * 2 > fileSize; // file is longer than 5MB and more than 50% of data is garbage
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

      final int id = enumerate(key);
      final AppendStream record = new AppendStream();
      myValueExternalizer.save(record, value);
      final ByteSequence bytes = record.getInternalBuffer();

      HeaderRecord header = readValueId(id);
      myGarbageSize += header.size;

      header.size = bytes.getLength();
      header.address = myValueStorage.appendBytes(bytes, 0);

      updateValueId(id, header);
    }
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
      final HeaderRecord header = readValueId(id);
      if (header.address == PersistentEnumerator.NULL_ID) {
        return null;
      }

      byte[] data = new byte[header.size];
      long newAddress = myValueStorage.readBytes(header.address, data);
      if (newAddress != header.address) {
        myEnumerator.markDirty(true);
        header.address = newAddress;
        updateValueId(id, header);
        myGarbageSize += header.size;
      }

      final DataInputStream input = new DataInputStream(new ByteArrayInputStream(data));
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
      return readValueId(id).address != PersistentEnumerator.NULL_ID;
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
      myGarbageSize += record.size;

      updateValueId(id, new HeaderRecord());
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

      traverseAllRecords(new PersistentEnumerator.RecordsProcessor() {
        public boolean process(final int keyId) throws IOException {
          final HeaderRecord record = readValueId(keyId);
          if (record.address != NULL_ADDR) {
            final byte[] bytes = new byte[record.size];
            myValueStorage.readBytes(record.address, bytes);
            record.address = newStorage.appendBytes(new ByteSequence(bytes), 0);
            updateValueId(keyId, record);
          }
          return true;
        }
      });

      myValueStorage.dispose();
      newStorage.dispose();

      FileUtil.rename(new File(newPath), getDataFile(myEnumerator.myFile));

      myValueStorage = PersistentHashMapValueStorage.create(getDataFile(myEnumerator.myFile).getPath());
      LOG.info("Compacted " + myEnumerator.myFile.getPath() + " in " + (System.currentTimeMillis() - now) + "ms.");
      myGarbageSize = 0;
      myEnumerator.putMetaData(0);
    }
  }

  private HeaderRecord readValueId(final int keyId) {
    HeaderRecord result = new HeaderRecord();
    result.address = myEnumerator.myStorage.getLong(keyId + myParentValueRefOffset);
    result.size = myEnumerator.myStorage.getInt(keyId + myParentValueRefOffset + 8);
    return result;
  }

  private void updateValueId(final int keyId, HeaderRecord value) {
    myEnumerator.myStorage.putLong(keyId + myParentValueRefOffset, value.address);
    myEnumerator.myStorage.putInt(keyId + myParentValueRefOffset + 8, value.size);
  }

  private static class HeaderRecord {
    long address;
    int size;
  }
}
