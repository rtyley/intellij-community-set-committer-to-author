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

package com.intellij.util.indexing;

import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.TObjectLongHashMap;
import gnu.trove.TObjectLongProcedure;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 25, 2007
 */
public class IndexingStamp {
  private IndexingStamp() {
  }

  /**
   * The class is meant to be accessed from synchronized block only 
   */
  private static class Timestamps {
    private static final FileAttribute PERSISTENCE = new FileAttribute("__index_stamps__", 1, false);
    private TObjectLongHashMap<ID<?, ?>> myIndexStamps;
    private boolean myIsDirty = false;

    private Timestamps(@Nullable DataInputStream stream) throws IOException {
      if (stream != null) {
        try {
          int count = DataInputOutputUtil.readINT(stream);
          if (count > 0) {
            myIndexStamps = new TObjectLongHashMap<ID<?, ?>>(count);
            for (int i = 0; i < count; i++) {
                ID<?, ?> id = ID.findById(DataInputOutputUtil.readINT(stream));
                long timestamp = DataInputOutputUtil.readTIME(stream);
                if (id != null) {
                  myIndexStamps.put(id, timestamp);
                }
              }
          }
        }
        finally {
          stream.close();
        }
      }
    }

    private void writeToStream(final DataOutputStream stream) throws IOException {
      if (myIndexStamps != null) {
        final int size = myIndexStamps.size();
        final int[] count = new int[]{0};
        DataInputOutputUtil.writeINT(stream, size);
        myIndexStamps.forEachEntry(new TObjectLongProcedure<ID<?, ?>>() {
          @Override
          public boolean execute(final ID<?, ?> id, final long timestamp) {
            try {
              DataInputOutputUtil.writeINT(stream, id.getUniqueId());
              DataInputOutputUtil.writeTIME(stream, timestamp);
              count[0]++;
              return true;
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        });
        assert count[0] == size;
      }
    }

    public long get(ID<?, ?> id) {
      return myIndexStamps != null? myIndexStamps.get(id) : 0L;
    }

    public void set(ID<?, ?> id, long tmst) {
      try {
        if (myIndexStamps == null) {
          myIndexStamps = new TObjectLongHashMap<ID<?, ?>>(5);
        }
        myIndexStamps.put(id, tmst);
      }
      finally {
        myIsDirty = true;
      }
    }

    public boolean isDirty() {
      return myIsDirty;
    }
  }

  private static final ConcurrentHashMap<VirtualFile, Timestamps> myTimestampsCache = new ConcurrentHashMap<VirtualFile, Timestamps>();
  private static final int CAPACITY = 100;
  private static final ArrayBlockingQueue<VirtualFile> myFinishedFiles = new ArrayBlockingQueue<VirtualFile>(CAPACITY);

  public static boolean isFileIndexed(VirtualFile file, ID<?, ?> indexName, final long indexCreationStamp) {
    try {
      return getIndexStamp(file, indexName) == indexCreationStamp;
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (!(cause instanceof IOException)) {
        throw e; // in case of IO exceptions consider file unindexed
      }
    }

    return false;
  }

  public static long getIndexStamp(VirtualFile file, ID<?, ?> indexName) {
    synchronized (file) {
      Timestamps stamp = createOrGetTimeStamp(file);
      if (stamp != null) return stamp.get(indexName);
      return 0;
    }
  }

  private static Timestamps createOrGetTimeStamp(VirtualFile file) {
    if (file instanceof NewVirtualFile && file.isValid()) {
      Timestamps timestamps = myTimestampsCache.get(file);
      if (timestamps == null) {
        synchronized (myTimestampsCache) { // avoid synchroneous reads TODO:
          timestamps = myTimestampsCache.get(file);
          if (timestamps == null) {
            final DataInputStream stream = Timestamps.PERSISTENCE.readAttribute(file);
            try {
              timestamps = new Timestamps(stream);
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
            myTimestampsCache.put(file, timestamps);
          }
        }
      }
      return timestamps;
    }
    return null;
  }

  public static void update(final VirtualFile file, final ID<?, ?> indexName, final long indexCreationStamp) {
    synchronized (file) {
      try {
        Timestamps stamp = createOrGetTimeStamp(file);
        if (stamp != null) stamp.set(indexName, indexCreationStamp);
      }
      catch (InvalidVirtualFileAccessException ignored /*ok to ignore it here*/) {
      }
    }
  }

  public static void flushCache(@Nullable VirtualFile finishedFile) {
    if (finishedFile == null || !myFinishedFiles.offer(finishedFile)) {
      VirtualFile[] files = null;
      synchronized (myFinishedFiles) {
        int size = myFinishedFiles.size();
        if ((finishedFile == null && size > 0) || size == CAPACITY) {
          files = myFinishedFiles.toArray(new VirtualFile[size]);
          myFinishedFiles.clear();
        }
      }

      if (files != null) {
        for(VirtualFile file:files) {
          synchronized (file) {
            Timestamps timestamp = myTimestampsCache.remove(file);
            if (timestamp == null) continue;
            synchronized (myTimestampsCache) {
              try {
                if (timestamp.isDirty() && file.isValid()) {
                  final DataOutputStream sink = Timestamps.PERSISTENCE.writeAttribute(file);
                  timestamp.writeToStream(sink);
                  sink.close();
                }
              }
              catch (IOException e) {
                throw new RuntimeException(e);
              }
            }
          }
        }
      }
      if (finishedFile != null) myFinishedFiles.offer(finishedFile);
    }
  }
}
