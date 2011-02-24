/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 * @author peter
 */
public class PerIndexDocumentVersionMap {
  private volatile int mapVersion;
  private static class IdVersionInfo {
    private final ID<?,?> id;
    private int mapVersion;
    private long docVersion;

    private IdVersionInfo(@NotNull ID<?, ?> id, long docVersion, int mapVersion) {
      this.docVersion = docVersion;
      this.mapVersion = mapVersion;
      this.id = id;
    }
  }

  private static final Key<List<IdVersionInfo>> KEY = Key.create("UnsavedDocIdVersionInfo");
  public long getAndSet(@NotNull Document document, @NotNull ID<?, ?> indexId, long value) {
    List<IdVersionInfo> list = document.getUserData(KEY);
    if (list == null) {
      list = ((UserDataHolderEx)document).putUserDataIfAbsent(KEY, new ArrayList<IdVersionInfo>());
    }

    synchronized (list) {
      for (IdVersionInfo info : list) {
        if (info.id == indexId) {
          long old = info.docVersion;
          if (info.mapVersion != mapVersion) {
            old = 0;
            info.mapVersion = mapVersion;
          }
          info.docVersion = value;
          return old;
        }
      }
      list.add(new IdVersionInfo(indexId, value, mapVersion));
      return 0;
    }
  }

  public void clear() {
    mapVersion++;
  }
}
