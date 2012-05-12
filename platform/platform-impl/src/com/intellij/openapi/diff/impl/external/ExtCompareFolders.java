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
package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
class ExtCompareFolders extends BaseExternalTool {
  public static final BaseExternalTool INSTANCE = new ExtCompareFolders();

  private ExtCompareFolders() {
    super(DiffManagerImpl.ENABLE_FOLDERS, DiffManagerImpl.FOLDERS_TOOL);
  }

  @Override
  public boolean isAvailable(DiffRequest request) {
    final DiffContent[] contents = request.getContents();
    if (contents.length != 2) return false;
    if (externalize(request, 0) == null) return false;
    if (externalize(request, 1) == null) return false;
    return true;
  }

  @Nullable
  protected ContentExternalizer externalize(DiffRequest request, int index) {
    final VirtualFile file = request.getContents()[index].getFile();

    if (!isLocalDirectory(file)) {
      return null;
    }

    return LocalFileExternalizer.tryCreate(file);
  }

  private static boolean isLocalDirectory(VirtualFile file) {
    final VirtualFile local = getLocalFile(file);
    return local != null && local.isDirectory();
  }
}
