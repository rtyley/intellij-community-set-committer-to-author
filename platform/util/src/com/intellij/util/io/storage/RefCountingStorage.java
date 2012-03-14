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

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.io.PagePool;
import com.intellij.util.io.UnsyncByteArrayInputStream;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class RefCountingStorage extends AbstractStorage {
  private final Map<Integer, Future<?>> myPendingWriteRequests = new ConcurrentHashMap<Integer, Future<?>>();
  private int myPendingWriteRequestsSize;
  private final ThreadPoolExecutor myPendingWriteRequestsExecutor = new ThreadPoolExecutor(1, 1, Long.MAX_VALUE, TimeUnit.DAYS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
    @Override
    public Thread newThread(Runnable runnable) {
      return new Thread(runnable, "RefCountingStorage write content helper");
    }
  });

  private final boolean myDoNotZipCaches = Boolean.valueOf(System.getProperty("idea.doNotZipCaches")).booleanValue();
  private static final int MAX_PENDING_WRITE_SIZE = 20 * 1024 * 1024;

  public RefCountingStorage(String path) throws IOException {
    super(path);
  }

  @Override
  protected byte[] readBytes(int record) throws IOException {

    if (myDoNotZipCaches) return super.readBytes(record);
    waitForPendingWriteForRecord(record);

    synchronized (myLock) {

      byte[] result = super.readBytes(record);
      InflaterInputStream in = new InflaterInputStream(new UnsyncByteArrayInputStream(result));
      try {
        return StreamUtil.loadFromStream(in);
      }
      finally {
        in.close();
      }
    }
  }

  private void waitForPendingWriteForRecord(int record) {
    Future<?> future = myPendingWriteRequests.get(record);
    if (future != null) {
      try {
        future.get();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  protected void appendBytes(int record, ByteSequence bytes) throws IOException {
    throw new IncorrectOperationException("Appending is not supported");
  }

  @Override
  public void writeBytes(final int record, final ByteSequence bytes, final boolean fixedSize) throws IOException {

    if (myDoNotZipCaches) {
      super.writeBytes(record, bytes, fixedSize);
      return;
    }

    waitForPendingWriteForRecord(record);

    synchronized (myLock) {
      myPendingWriteRequestsSize += bytes.getLength();
      if (myPendingWriteRequestsSize > MAX_PENDING_WRITE_SIZE) {
        zipAndWrite(bytes, record, fixedSize);
      } else {
        myPendingWriteRequests.put(record, myPendingWriteRequestsExecutor.submit(new Callable<Object>() {
          @Override
          public Object call() throws IOException {
            zipAndWrite(bytes, record, fixedSize);
            return null;
          }
        }));
      }
    }
  }

  private void zipAndWrite(ByteSequence bytes, int record, boolean fixedSize) throws IOException {
    BufferExposingByteArrayOutputStream s = new BufferExposingByteArrayOutputStream();
    DeflaterOutputStream out = new DeflaterOutputStream(s);
    try {
      out.write(bytes.getBytes(), bytes.getOffset(), bytes.getLength());
    }
    finally {
      out.close();
    }

    synchronized (myLock) {
      doWrite(record, fixedSize, s);
      myPendingWriteRequestsSize -= bytes.getLength();
      myPendingWriteRequests.remove(record);
    }
  }

  private void doWrite(int record, boolean fixedSize, BufferExposingByteArrayOutputStream s) throws IOException {
    super.writeBytes(record, new ByteSequence(s.getInternalBuffer(), 0, s.size()), fixedSize);
  }

  @Override
  protected AbstractRecordsTable createRecordsTable(PagePool pool, File recordsFile) throws IOException {
    return new RefCountingRecordsTable(recordsFile, pool);
  }

  public int acquireNewRecord() throws IOException {
    synchronized (myLock) {
      int record = myRecordsTable.createNewRecord();
      ((RefCountingRecordsTable)myRecordsTable).incRefCount(record);
      return record;
    }
  }

  public void acquireRecord(int record) {
    waitForPendingWriteForRecord(record);
    synchronized (myLock) {
      ((RefCountingRecordsTable)myRecordsTable).incRefCount(record);
    }
  }

  public void releaseRecord(int record) throws IOException {
    waitForPendingWriteForRecord(record);
    synchronized (myLock) {
      if (((RefCountingRecordsTable)myRecordsTable).decRefCount(record)) {
        doDeleteRecord(record);
      }
    }
  }

  public int getRefCount(int record) {
    waitForPendingWriteForRecord(record);
    synchronized (myLock) {
      return ((RefCountingRecordsTable)myRecordsTable).getRefCount(record);
    }
  }

  @Override
  public void force() {
    flushPendingWrites();
    super.force();
  }

  @Override
  public boolean isDirty() {
    return myPendingWriteRequests.size() > 0 || super.isDirty();
  }

  @Override
  public boolean flushSome() {
    flushPendingWrites();
    return super.flushSome();
  }

  @Override
  public void dispose() {
    flushPendingWrites();
    super.dispose();
  }

  @Override
  public void checkSanity(int record) {
    flushPendingWrites();
    super.checkSanity(record);
  }

  private void flushPendingWrites() {
    for(Map.Entry<Integer, Future<?>> entry:myPendingWriteRequests.entrySet()) {
      try {
        entry.getValue().get();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
