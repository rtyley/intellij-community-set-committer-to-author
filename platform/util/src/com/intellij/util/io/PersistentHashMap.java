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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.LimitedPool;
import com.intellij.util.containers.SLRUCache;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 18, 2007
 */
public class PersistentHashMap<Key, Value> extends PersistentEnumerator<Key>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.PersistentHashMap");

  private PersistentHashMapValueStorage myValueStorage;
  private final DataExternalizer<Value> myValueExternalizer;
  private static final long NULL_ADDR = 0;
  private static final int NULL_SIZE = 0;

  @NonNls
  public static final String DATA_FILE_EXTENSION = ".values";
  private final File myFile;
  private int myGarbageSize;
  private static final int VALUE_REF_OFFSET = RECORD_SIZE;
  private final byte[] myRecordBuffer = new byte[RECORD_SIZE + 8 + 4];

  private static class AppendStream extends DataOutputStream {
    private AppendStream() {
      super(new ByteArrayOutputStream());
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

        final byte[] bytes = value.toByteArray();

        headerRecord.size += bytes.length;
        headerRecord.address = myValueStorage.appendBytes(bytes, headerRecord.address);

        updateValueId(id, headerRecord);

        myStreamPool.recycle(value);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  };

  public PersistentHashMap(final File file, KeyDescriptor<Key> keyDescriptor, DataExternalizer<Value> valueExternalizer) throws IOException {
    this(file, keyDescriptor, valueExternalizer, 1024 * 4);
  }
  
  public PersistentHashMap(final File file, KeyDescriptor<Key> keyDescriptor, DataExternalizer<Value> valueExternalizer, final int initialSize) throws IOException {
    super(checkDataFiles(file), keyDescriptor, initialSize);
    try {
      myFile = file;
      myValueExternalizer = valueExternalizer;
      myValueStorage = PersistentHashMapValueStorage.create(getDataFile(myFile).getPath());
      myGarbageSize = getMetaData();

      if (makesSenceToCompact()) {
        compact();
      }
    }
    catch (IOException e) {
      throw e; // rethrow
    }
    catch (Throwable t) {
      LOG.error(t);
      throw new CorruptedException(file);
    }
  }

  public File getBaseFile() {
    return myFile;
  }

  private boolean makesSenceToCompact() {
    final long filesize = getDataFile(myFile).length();
    return filesize > 5 * 1024 * 1024 && myGarbageSize * 2 > filesize; // file is longer than 5MB and more than 50% of data is garbage
  }

  private static File checkDataFiles(final File file) {
    if (!file.exists()) {
      final File dataFile = getDataFile(file);
      final String baseName = dataFile.getName();
      final File[] files = dataFile.getParentFile().listFiles(new FileFilter() {
        public boolean accept(final File pathname) {
          return pathname.getName().startsWith(baseName);
        }
      });
      if (files != null) {
        for (File f : files) {
          FileUtil.delete(f);
        }
      }
    }
    return file;
  }

  private static File getDataFile(final File file) {
    return new File(file.getParentFile(), file.getName() + DATA_FILE_EXTENSION);
  }

  public synchronized void put(Key key, Value value) throws IOException {
    myAppendCache.remove(key);
    
    final int id = enumerate(key);
    AppendStream record = new AppendStream();
    myValueExternalizer.save(record, value);
    byte[] bytes = record.toByteArray();

    HeaderRecord header = readValueId(id);
    if (header != null) {
      myGarbageSize += header.size;
    }
    else {
      header = new HeaderRecord();
    }

    header.size = bytes.length;
    header.address = myValueStorage.appendBytes(bytes, 0);

    updateValueId(id, header);
  }

  public interface ValueDataAppender {
    void append(DataOutput out) throws IOException;
  }
  
  public synchronized void appendData(Key key, ValueDataAppender appender) throws IOException {
    appender.append(myAppendCache.get(key));
  }

  public synchronized boolean processKeys(Processor<Key> processor) throws IOException {
    myAppendCache.clear();
    return iterateData(processor);
  }

  public synchronized Collection<Key> getAllKeysWithExistingMapping() throws IOException {
    return getAllDataObjects(new DataFilter() {
      public boolean accept(final int id) {
        try {
          return readValueId(id).address != NULL_ADDR;
        }
        catch (IOException ignored) {
        }
        return true;
      }
    });
  }

  public synchronized Value get(Key key) throws IOException {
    myAppendCache.remove(key);
    final int id = tryEnumerate(key);
    if (id == NULL_ID) {
      return null;
    }
    final HeaderRecord header = readValueId(id);
    if (header.address == NULL_ID) {
      return null;
    }

    byte[] data = new byte[header.size];
    long newAddress = myValueStorage.readBytes(header.address, data);
    if (newAddress != header.address) {
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

  public synchronized boolean containsMapping(Key key) throws IOException {
    myAppendCache.remove(key);
    final int id = tryEnumerate(key);
    if (id == NULL_ID) {
      return false;
    }
    return readValueId(id).address != NULL_ID;

  }

  public synchronized void remove(Key key) throws IOException {
    myAppendCache.remove(key);
    final int id = tryEnumerate(key);
    if (id == NULL_ID) {
      return;
    }

    final HeaderRecord record = readValueId(id);
    if (record != null) {
      myGarbageSize += record.size;
    }

    updateValueId(id, new HeaderRecord());
  }

  protected void markClean() throws IOException {
    putMetaData(myGarbageSize);
    super.markClean();
  }

  public synchronized void force() {
    myAppendCache.clear();
    myValueStorage.force();

    super.force();
  }

  public synchronized void close() throws IOException {
    myAppendCache.clear();
    try {
      super.close();
    }
    finally {
      myValueStorage.dispose();
    }
  }
  
  // made public for tests
  public synchronized void compact() throws IOException {
    final long now = System.currentTimeMillis();
    final String newPath = getDataFile(myFile).getPath() + ".new";
    final PersistentHashMapValueStorage newStorage = PersistentHashMapValueStorage.create(newPath);
    myValueStorage.switchToCompactionMode();
    
    traverseAllRecords(new RecordsProcessor() {
      public boolean process(final int keyId) throws IOException {
        final HeaderRecord record = readValueId(keyId);
        if (record.address != NULL_ADDR) {
          byte[] bytes = new byte[record.size];
          myValueStorage.readBytes(record.address, bytes);
          record.address = newStorage.appendBytes(bytes, 0);
          updateValueId(keyId, record);
        }
        return true;
      }
    });

    myValueStorage.dispose();
    newStorage.dispose();

    FileUtil.rename(new File(newPath), getDataFile(myFile));
    
    myValueStorage = PersistentHashMapValueStorage.create(getDataFile(myFile).getPath());
    LOG.info("Compacted " + myFile.getPath() + " in " + (System.currentTimeMillis() - now) + "ms.");
    myGarbageSize = 0;
  }

  private HeaderRecord readValueId(final int keyId) throws IOException {
    HeaderRecord result = new HeaderRecord();
    result.address = myStorage.getLong(keyId + VALUE_REF_OFFSET);
    result.size = myStorage.getInt(keyId + VALUE_REF_OFFSET + 8);
    return result;
  }

  private void updateValueId(final int keyId, HeaderRecord value) throws IOException {
    myStorage.putLong(keyId + VALUE_REF_OFFSET, value.address);
    myStorage.putInt(keyId + VALUE_REF_OFFSET + 8, value.size);
  }

  @Override
  protected byte[] getRecordBuffer() {
    return myRecordBuffer;
  }

  @Override
  protected void setupRecord(final int hashCode, final int dataOffset, final byte[] buf) {
    super.setupRecord(hashCode, dataOffset, buf);
    for (int i = VALUE_REF_OFFSET; i < myRecordBuffer.length; i++) {
      buf[i] = 0;
    }
  }

  private static class HeaderRecord {
    long address;
    int size;
  }
}
