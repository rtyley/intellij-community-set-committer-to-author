/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl.win32;

import com.intellij.openapi.util.io.win32.FileInfo;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
class Win32FsCache {
  private final IdeaWin32 myKernel = IdeaWin32.getInstance();
  private final Map<String, FileInfo> myCache = new THashMap<String, FileInfo>();

  void clearCache() {
    myCache.clear();
  }

  @NotNull
  String[] list(@NotNull String path) {
    FileInfo[] fileInfo = myKernel.listChildren(path);
    if (fileInfo == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    ArrayList<String> names = new ArrayList<String>(fileInfo.length);
    for (FileInfo info : fileInfo) {
      String name = info.getName();
      myCache.put(path + "/" + name, info);
      names.add(name);
    }

    return ArrayUtil.toStringArray(names);
  }

  @Nullable
  FileInfo getInfo(@NotNull VirtualFile file) {
    String path = file.getPath();
    FileInfo info = myCache.get(path);
    if (info == null) {
      info = myKernel.getInfo(path);
      if (info == null) {
        return null;
      }
      myCache.put(path, info);
    }
    return info;
  }
}
