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
package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffTool;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.Arrays;

class BinaryDiffTool implements DiffTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.external.BinaryDiffTool");
  public static final DiffTool INSTANCE = new BinaryDiffTool();
  public void show(DiffRequest data) {
    DiffContent[] contents = data.getContents();
    try {
      compareBinaryFiles(contents[0].getBytes(), contents[1].getBytes());
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static void compareBinaryFiles(byte[] currentContent, byte[] upToDateContent) {
    if (Arrays.equals(currentContent, upToDateContent)) {
      Messages.showMessageDialog(DiffBundle.message("binary.files.are.identical.message"),
                                 DiffBundle.message("files.are.identical.dialog.title"), Messages.getInformationIcon());
    }
    else {
      Messages.showMessageDialog(DiffBundle.message("binary.files.are.different.message"),
                                 DiffBundle.message("files.are.different.dialog.title"), Messages.getInformationIcon());
    }
  }

  public boolean canShow(DiffRequest data) {
    DiffContent[] contents = data.getContents();
    if (contents.length != 2) return false;
    for (int i = 0; i < contents.length; i++) {
      DiffContent content = contents[i];
      VirtualFile file = content.getFile();
      if (file != null && file.isDirectory()) return false;
    }
    return true;
  }
}
