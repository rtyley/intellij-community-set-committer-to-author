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
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class FilePathUtil {
  private FilePathUtil() {
  }

  public static boolean isNested(final Collection<FilePath> roots, final FilePath root) {
    return isNested(roots, root.getIOFile());
  }

  public static boolean isNested(final Collection<FilePath> roots, final File root) {
    for (FilePath filePath : roots) {
      final File ioFile = filePath.getIOFile();
      if (ioFile.equals(root)) continue;
      if (FileUtil.isAncestor(ioFile, root, true)) {
        return true;
      }
    }
    return false;
  }
}
