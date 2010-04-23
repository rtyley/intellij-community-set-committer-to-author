/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.util.io.storage;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.Forceable;
import com.intellij.util.io.PagePool;
import com.intellij.util.io.RandomAccessDataFile;
import gnu.trove.TIntArrayList;

import java.io.File;
import java.io.IOException;

public abstract class AbstractRecordsTable implements Disposable, Forceable {
  private static final int HEADER_MAGIC_OFFSET = 0;
  private static final int HEADER_VERSION_OFFSET = 4;
  protected static final int DEFAULT_HEADER_SIZE = 8;

  private static final int VERSION = 5;
  private static final int CONNECTED_MAGIC = 0x12ad34e4;
  private static final int SAFELY_CLOSED_MAGIC = 0x1f2f3f4f + VERSION;

  private static final int ADDRESS_OFFSET = 0;
  private static final int SIZE_OFFSET = ADDRESS_OFFSET + 8;
  private static final int CAPACITY_OFFSET = SIZE_OFFSET + 4;

  protected static final int DEFAULT_RECORD_SIZE = CAPACITY_OFFSET + 4;

  protected final RandomAccessDataFile myStorage;

  private TIntArrayList myFreeRecordsList = null;
  private boolean myIsDirty = false;

  public AbstractRecordsTable(final File storageFilePath, final PagePool pool) throws IOException {
    myStorage = new RandomAccessDataFile(storageFilePath, pool);
    if (myStorage.length() == 0) {
      myStorage.put(0, new byte[getHeaderSize()], 0, getHeaderSize());
      myIsDirty = true;
    }
    else {
      if (myStorage.getInt(HEADER_MAGIC_OFFSET) != getSafelyClosedMagic()) {
        myStorage.dispose();
        throw new IOException("Records table for '" + storageFilePath + "' haven't been closed correctly. Rebuild required.");
      }
    }
  }

  private int getSafelyClosedMagic() {
    return SAFELY_CLOSED_MAGIC + getImplVersion();
  }

  protected int getHeaderSize() {
    return DEFAULT_HEADER_SIZE;
  }

  protected abstract int getImplVersion();

  protected abstract int getRecordSize();

  protected abstract byte[] getZeros();

  public void markDirty() {
    if (!myIsDirty) {
      myIsDirty = true;
      myStorage.putInt(HEADER_MAGIC_OFFSET, CONNECTED_MAGIC);
    }
  }

  public int createNewRecord() throws IOException {
    markDirty();
    ensureFreeRecordsScanned();

    if (myFreeRecordsList.isEmpty()) {
      int result = getRecordsCount() + 1;
      cleanRecord(result);
      return result;
    }
    else {
      final int result = myFreeRecordsList.remove(myFreeRecordsList.size() - 1);
      assert getSize(result) == -1;
      setSize(result, 0);
      return result;
    }
  }

  public int getRecordsCount() {
    try {
      return doGetRecordsCount();
    }
    catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private int doGetRecordsCount() throws IOException {
    int recordsLength = (int)myStorage.length() - getHeaderSize();
    if ((recordsLength % getRecordSize()) != 0) {
      throw new IOException("Corrupted records");
    }

    return recordsLength / getRecordSize();
  }

  private void ensureFreeRecordsScanned() {
    if (myFreeRecordsList == null) {
      myFreeRecordsList = scanForFreeRecords();
    }
  }

  private TIntArrayList scanForFreeRecords() {
    final TIntArrayList result = new TIntArrayList();
    for (int i = 1; i <= getRecordsCount(); i++) {
      if (getSize(i) == -1) {
        result.add(i);
      }
    }
    return result;
  }

  private void cleanRecord(int record) {
    myStorage.put(getOffset(record, 0), getZeros(), 0, getRecordSize());
  }

  public long getAddress(int record) {
    return myStorage.getLong(getOffset(record, ADDRESS_OFFSET));
  }

  public void setAddress(int record, long address) {
    markDirty();
    myStorage.putLong(getOffset(record, ADDRESS_OFFSET), address);
  }

  public int getSize(int record) {
    return myStorage.getInt(getOffset(record, SIZE_OFFSET));
  }

  public void setSize(int record, int size) {
    markDirty();
    myStorage.putInt(getOffset(record, SIZE_OFFSET), size);
  }

  public int getCapacity(int record) {
    return myStorage.getInt(getOffset(record, CAPACITY_OFFSET));
  }

  public void setCapacity(int record, int capacity) {
    markDirty();
    myStorage.putInt(getOffset(record, CAPACITY_OFFSET), capacity);
  }

  protected int getOffset(int record, int section) {
    assert record > 0;
    return getHeaderSize() + (record - 1) * getRecordSize() + section;
  }

  public void deleteRecord(final int record) {
    ensureFreeRecordsScanned();
    setSize(record, -1);
    clearDeletedRecord(record);
    myFreeRecordsList.add(record);
  }

  protected abstract void clearDeletedRecord(int record);

  public int getVersion() {
    return myStorage.getInt(HEADER_VERSION_OFFSET);
  }

  public void setVersion(final int expectedVersion) {
    markDirty();
    myStorage.putInt(HEADER_VERSION_OFFSET, expectedVersion);
  }

  public void dispose() {
    markClean();
    myStorage.dispose();
  }

  public void force() {
    markClean();
    myStorage.force();
  }

  public boolean flushSome(int maxPages) {
    myStorage.flushSomePages(maxPages);
    if (!myStorage.isDirty()) {
      force();
      return true;
    }
    return false;
  }

  public boolean isDirty() {
    return myIsDirty;
  }

  private void markClean() {
    if (myIsDirty) {
      myIsDirty = false;
      myStorage.putInt(HEADER_MAGIC_OFFSET, getSafelyClosedMagic());
    }
  }
}