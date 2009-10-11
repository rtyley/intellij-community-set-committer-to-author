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

package com.intellij.historyIntegrTests.revertion;

import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;

import java.io.IOException;

public class ChangeReverterOptimizationTest extends ChangeReverterTestCase {
  public void testApplyingOnlyLastContent() throws Exception {
    VirtualFile f = root.createChildData(null, "f.txt");
    f.setBinaryContent(new byte[]{1}, -1, 1000);
    f.setBinaryContent(new byte[]{2}, -1, 2000);
    f.setBinaryContent(new byte[]{3}, -1, 3000);
    f.setBinaryContent(new byte[]{4}, -1, 4000);

    LoggingVirtualFileAdapter a = new LoggingVirtualFileAdapter();
    addFileListenerDuring(a, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        revertChange(root, 2);
      }
    });

    assertEquals("contentChanged ", a.getLog());
    assertEquals(1, f.contentsToByteArray()[0]);
    assertEquals(1000, f.getTimeStamp());
  }

  public void testApplyingRightContentEvenIfFileWasMoved() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "dir");
    VirtualFile f = root.createChildData(null, "f.txt");

    f.setBinaryContent(new byte[]{1});
    f.setBinaryContent(new byte[]{2});
    f.move(null, dir);
    f.setBinaryContent(new byte[]{3});

    LoggingVirtualFileAdapter a = new LoggingVirtualFileAdapter();
    addFileListenerDuring(a, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        revertChange(root, 2);
      }
    });

    assertEquals("fileMoved contentChanged ", a.getLog());
    assertEquals(1, f.contentsToByteArray()[0]);
  }

  public void testDoesNotSetContentIfFileIsToBeDeleted() throws Exception {
    VirtualFile f = root.createChildData(null, "f.txt");
    f.setBinaryContent(new byte[]{1});

    LoggingVirtualFileAdapter a = new LoggingVirtualFileAdapter();
    addFileListenerDuring(a, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        revertChange(root, 1);
      }
    });

    assertEquals("fileDeleted ", a.getLog());
  }

  private static class LoggingVirtualFileAdapter extends VirtualFileAdapter {
    private String myLog = "";

    @Override
    public void contentsChanged(VirtualFileEvent e) {
      myLog += "contentChanged ";
    }

    @Override
    public void fileMoved(VirtualFileMoveEvent e) {
      myLog += "fileMoved ";
    }

    @Override
    public void fileDeleted(VirtualFileEvent e) {
      myLog += "fileDeleted ";
    }

    public String getLog() {
      return myLog;
    }
  }
}