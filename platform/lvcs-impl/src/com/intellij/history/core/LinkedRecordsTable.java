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
package com.intellij.history.core;

import com.intellij.openapi.components.impl.stores.StorageUtil;
import com.intellij.util.io.PagePool;
import com.intellij.util.io.storage.AbstractRecordsTable;

import java.io.File;
import java.io.IOException;

public class LinkedRecordsTable extends AbstractRecordsTable {
  private static final int VERSION = 3;

  private static final int ID_COUNTER_OFFSET = DEFAULT_HEADER_SIZE;
  private static final int FIRST_RECORD_OFFSET = ID_COUNTER_OFFSET + 8;
  private static final int LAST_RECORD_OFFSET = FIRST_RECORD_OFFSET + 4;
  private static final int FS_TIMESTAMP_OFFSET = LAST_RECORD_OFFSET + 4;
  private static final int HEADER_SIZE = FS_TIMESTAMP_OFFSET + 8;

  private static final int PREV_RECORD_OFFSET = DEFAULT_RECORD_SIZE;
  private static final int NEXT_RECORD_OFFSET = DEFAULT_RECORD_SIZE + 4;
  private static final int TIMESTAMP_OFFSET = DEFAULT_RECORD_SIZE + 8;

  private static final int RECORD_SIZE = TIMESTAMP_OFFSET + 8;
  private static final byte[] ZEROS = new byte[RECORD_SIZE];

  public LinkedRecordsTable(final File storageFilePath, final PagePool pool) throws IOException {
    super(storageFilePath, pool);
  }

  @Override
  protected int getHeaderSize() {
    return HEADER_SIZE;
  }

  @Override
  protected int getRecordSize() {
    return RECORD_SIZE;
  }

  @Override
  protected int getImplVersion() {
    return VERSION;
  }

  @Override
  protected byte[] getZeros() {
    return ZEROS;
  }

  public void setFSTimestamp(long timestamp) {
    markDirty();
    myStorage.putLong(FS_TIMESTAMP_OFFSET, timestamp);
  }

  public long getFSTimestamp() {
    return myStorage.getLong(FS_TIMESTAMP_OFFSET);
  }

  public void setFirstRecord(int record) {
    markDirty();
    myStorage.putInt(FIRST_RECORD_OFFSET, record);
  }

  public int getFirstRecord() {
    markDirty();
    return myStorage.getInt(FIRST_RECORD_OFFSET);
  }

  public void setLastRecord(int record) {
    markDirty();
    myStorage.putInt(LAST_RECORD_OFFSET, record);
  }

  public int getLastRecord() {
    markDirty();
    return myStorage.getInt(LAST_RECORD_OFFSET);
  }

  public void setPrevRecord(int record, int prevRecord) {
    markDirty();
    myStorage.putInt(getOffset(record, PREV_RECORD_OFFSET), prevRecord);
  }

  public int getPrevRecord(int record) {
    return myStorage.getInt(getOffset(record, PREV_RECORD_OFFSET));
  }

  public void setNextRecord(int record, int nextRecord) {
    markDirty();
    myStorage.putInt(getOffset(record, NEXT_RECORD_OFFSET), nextRecord);
  }

  public int getNextRecord(int record) {
    return myStorage.getInt(getOffset(record, NEXT_RECORD_OFFSET));
  }

  public void setTimestamp(int record, long timestamp) {
    markDirty();
    myStorage.putLong(getOffset(record, TIMESTAMP_OFFSET), timestamp);
  }

  public long getTimestamp(int record) {
    return myStorage.getLong(getOffset(record, TIMESTAMP_OFFSET));
  }

  public long nextId() {
    markDirty();
    long result = myStorage.getLong(ID_COUNTER_OFFSET);
    myStorage.putLong(ID_COUNTER_OFFSET, result + 1);
    return result;
  }
}

