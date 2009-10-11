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

import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.revertion.ChangeReverter;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChangeReverterROStatusClearingTest extends ChangeReverterTestCase {
  public void testClearingROStatus() throws Exception {
    getVcs().beginChangeSet();
    VirtualFile f1 = root.createChildData(null, "f1.txt");
    VirtualFile f2 = root.createChildData(null, "f2.txt");
    getVcs().endChangeSet(null);

    List<VirtualFile> files = checkCanRevertAndGetFilesToClearROStatus(root, 0);
    assertEquals(2, files.size());
    assertTrue(files.contains(f1));
    assertTrue(files.contains(f2));
  }

  public void testClearingROStatusOnlyFromExistedFiles() throws Exception {
    getVcs().beginChangeSet();
    VirtualFile f1 = root.createChildData(null, "f1.txt");
    VirtualFile f2 = root.createChildData(null, "f2.txt");
    getVcs().endChangeSet(null);

    f2.delete(null);

    List<VirtualFile> files = checkCanRevertAndGetFilesToClearROStatus(root, 1);
    assertEquals(1, files.size());
    assertTrue(files.contains(f1));
  }

  public void testClearingROStatusOnlyFromAffectedFiles() throws Exception {
    VirtualFile f1 = root.createChildData(null, "f1.txt");
    root.createChildData(null, "f2.txt");

    List<VirtualFile> files = checkCanRevertAndGetFilesToClearROStatus(f1, 0);
    assertEquals(1, files.size());
    assertTrue(files.contains(f1));
  }

  public void testClearingROStatusOnlyFromMovedFiles() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "dir");
    VirtualFile f = root.createChildData(null, "f.txt");

    f.move(null, dir);

    List<VirtualFile> files = checkCanRevertAndGetFilesToClearROStatus(root, 1);
    assertEquals(1, files.size());
    assertTrue(files.contains(f));
  }

  public void testClearingROStatusFromFilesUnderDirectory() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "dir");
    VirtualFile f1 = dir.createChildData(null, "f1.txt");
    VirtualFile f2 = dir.createChildData(null, "f2.txt");

    dir.rename(null, "newName");

    List<VirtualFile> files = checkCanRevertAndGetFilesToClearROStatus(root, 0);
    assertEquals(2, files.size());
    assertTrue(files.contains(f1));
    assertTrue(files.contains(f2));
  }

  public void testClearingROStatusFromSameFileOnlyOnce() throws Exception {
    VirtualFile f = root.createChildData(null, "f.txt");

    f.setBinaryContent(new byte[]{1});
    f.setBinaryContent(new byte[]{2});
    f.setBinaryContent(new byte[]{3});

    List<VirtualFile> files = checkCanRevertAndGetFilesToClearROStatus(f, 2);
    assertEquals(1, files.size());
    assertTrue(files.contains(f));
  }

  public void testClearingROStatusFromAllFilesInTheChain() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "dir");
    VirtualFile f1 = root.createChildData(null, "f1.txt");
    VirtualFile f2 = root.createChildData(null, "f2.txt");

    f1.move(null, dir);
    dir.rename(null, "newName");
    f2.move(null, dir);

    List<VirtualFile> files = checkCanRevertAndGetFilesToClearROStatus(dir, 2);
    assertEquals(2, files.size());
    assertTrue(files.contains(f1));
    assertTrue(files.contains(f2));
  }

  public void testDoesNotClearROStatusFromPrevioulsyChangedFiles() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "dir");
    VirtualFile f = dir.createChildData(null, "f.txt");

    f.move(null, root);
    dir.rename(null, "newName");

    assertTrue(checkCanRevertAndGetFilesToClearROStatus(dir, 0).isEmpty());
  }

  public void testCanNotRevertIfUserDidNotClearROStatus() throws Exception {
    VirtualFile f = root.createChildData(null, "f.txt");
    f.setBinaryContent(new byte[1]);

    gateway = new IdeaGateway(myProject) {
      @Override
      public boolean ensureFilesAreWritable(List<VirtualFile> ff) {
        return false;
      }
    };

    assertCanNotRevert(f, 0, "some files are read-only");
  }

  private List<VirtualFile> checkCanRevertAndGetFilesToClearROStatus(VirtualFile f, int changeIndex) throws IOException {
    final List<VirtualFile> result = new ArrayList<VirtualFile>();
    gateway = new IdeaGateway(myProject) {
      @Override
      public boolean ensureFilesAreWritable(List<VirtualFile> ff) {
        result.addAll(ff);
        return true;
      }
    };

    ChangeReverter r = createReverter(f, changeIndex);
    assertTrue(r.checkCanRevert().isEmpty());

    return result;
  }
}