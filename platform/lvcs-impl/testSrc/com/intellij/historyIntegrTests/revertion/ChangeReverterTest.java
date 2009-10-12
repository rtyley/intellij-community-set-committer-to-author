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

import com.intellij.history.Clock;
import com.intellij.history.core.changes.Change;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.revertion.ChangeReverter;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.Date;

public class ChangeReverterTest extends ChangeReverterTestCase {
  public void testRevertCreation() throws IOException {
    VirtualFile f = root.createChildData(null, "f.txt");

    revertLastChange(f);
    assertNull(root.findChild("f.txt"));
  }

  public void testRevertChangeSetWithSeveralChanges() throws IOException {
    final VirtualFile[] ff = new VirtualFile[2];
    CommandProcessor.getInstance().executeCommand(myProject, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        ff[0] = root.createChildData(null, "f1.txt");
        ff[1] = root.createChildData(null, "f2.txt");
      }
    }, "", null);

    revertLastChange(ff[0]);
    assertNull(root.findChild("f1.txt"));
    assertNull(root.findChild("f2.txt"));
  }

  public void testDoesNotRevertAnotherChanges() throws IOException {
    VirtualFile f1 = root.createChildData(null, "f1.txt");
    root.createChildData(null, "f2.txt");

    revertLastChange(f1);

    assertNull(root.findChild("f1.txt"));
    assertNotNull(root.findChild("f2.txt"));
  }

  public void testRevertSubsequentChanges() throws IOException {
    VirtualFile f = root.createChildData(null, "f.txt");
    f.setBinaryContent(new byte[]{1}, -1, 123);
    f.rename(null, "ff.txt");
    f.setBinaryContent(new byte[]{2}, -1, 456);

    revertChange(f, 1); // rename

    assertEquals("f.txt", f.getName());
    assertEquals(1, f.contentsToByteArray()[0]);
  }

  public void testRevertSubsequentMovements() throws IOException {
    VirtualFile f = root.createChildData(null, "f.txt");
    VirtualFile dir = root.createChildDirectory(null, "dir");

    f.setBinaryContent(new byte[]{1}, -1, 123);
    f.move(null, dir);

    revertChange(f, 1); // content change

    assertEquals(root, f.getParent());
    assertEquals(0, f.contentsToByteArray().length);
    assertEquals(dir, root.findChild("dir"));
  }

  public void testRevertSubsequentParentMovement() throws IOException {
    VirtualFile dir1 = root.createChildDirectory(null, "dir1");
    VirtualFile dir2 = root.createChildDirectory(null, "dir2");
    VirtualFile f = dir1.createChildData(null, "f.txt");

    f.setBinaryContent(new byte[]{1}, -1, 123);
    dir1.move(null, dir2);

    revertChange(f, 1); // content change

    assertEquals(0, f.contentsToByteArray().length);
    assertEquals(root, dir1.getParent());
    assertEquals(0, dir2.getChildren().length);
  }

  public void testRevertCreationOfParentInWhichFileWasMoved() throws IOException {
    VirtualFile dir1 = root.createChildDirectory(null, "dir1");

    VirtualFile f = dir1.createChildData(null, "f.txt");
    f.setBinaryContent(new byte[]{1}, -1, 123);

    VirtualFile dir2 = root.createChildDirectory(null, "dir2");
    dir1.move(null, dir2);

    revertChange(f, 1); // content change

    assertEquals(0, f.contentsToByteArray().length);
    assertEquals(root, dir1.getParent());
    assertNull(root.findChild("dir2"));
  }

  public void testRevertDeletionOfContentRootWithFiles() throws Exception {
    VirtualFile newRoot = addContentRootWithFiles("f.txt");
    String path = newRoot.getPath();

    newRoot.delete(null);

    revertLastChangeSet();

    VirtualFile restoredRoot = findFile(path);
    assertNotNull(restoredRoot);

    VirtualFile restoredFile = restoredRoot.findChild("f.txt");
    assertNotNull(restoredFile);

    // should keep history, but due to order of events, in which RootsChanged
    // event arrives before FileCreated event, i could not think out easy way
    // to make it work so far.
    assertEquals(1, getVcsRevisionsFor(restoredRoot).size());
    assertEquals(1, getVcsRevisionsFor(restoredFile).size());
  }

  private void revertLastChangeSet() throws IOException {
    Change cs = getVcs().getChangeList().getChanges().get(0);
    ChangeReverter r = createReverter(cs);
    r.revert();
  }

  private VirtualFile findFile(String path) {
    return LocalFileSystem.getInstance().findFileByPath(path);
  }

  public void testRevertDeletion() throws Exception {
    VirtualFile f = root.createChildData(null, "f.txt");
    f.delete(null);

    revertLastChange(root);

    f = root.findChild("f.txt");
    assertNotNull(f);
    assertEquals(2, getVcsRevisionsFor(f).size());
  }

  public void testRevertMovementAfterDeletion() throws Exception {
    VirtualFile dir1 = root.createChildDirectory(null, "dir1");
    VirtualFile dir2 = root.createChildDirectory(null, "dir2");
    VirtualFile f = dir1.createChildData(null, "f.txt");

    f.delete(null);
    dir1.move(null, dir2);

    revertChange(dir1, 1); // deletion

    assertEquals(root, dir1.getParent());
    assertNotNull(dir1.findChild("f.txt"));
  }

  public void testRevertDeletionOnPreviousParentAfterMovement() throws Exception {
    VirtualFile dir1 = root.createChildDirectory(null, "dir1");
    VirtualFile dir2 = root.createChildDirectory(null, "dir2");
    VirtualFile f = dir1.createChildData(null, "f.txt");

    f.move(null, dir2);
    dir1.delete(null);

    revertLastChange(f); // movement

    dir1 = root.findChild("dir1");
    assertNotNull(dir1);
    assertEquals(dir1, f.getParent());
  }

  public void testRestoringNecessaryDirectoriesDuringSubsequentMovementsRevert() throws Exception {
    VirtualFile dir1 = root.createChildDirectory(null, "dir1");
    VirtualFile dir2 = root.createChildDirectory(null, "dir2");
    VirtualFile dir3 = root.createChildDirectory(null, "dir3");
    VirtualFile f = dir2.createChildData(null, "f.txt");

    dir1.move(null, dir3);
    f.move(null, dir1);
    dir2.delete(null);

    revertChange(dir1, 1); // movement
    // should revert dir deletion and file movement

    dir2 = root.findChild("dir2");
    assertNotNull(dir2);
    assertEquals(dir2, f.getParent());
    assertEquals(root, dir1.getParent());
  }

  public void testRevertSubsequentalFileMovementFromDir() throws IOException {
    VirtualFile dir1 = root.createChildDirectory(null, "dir1");
    VirtualFile dir2 = root.createChildDirectory(null, "dir2");
    VirtualFile f = dir1.createChildData(null, "f.txt");

    dir1.move(null, dir2);
    f.move(null, dir2);

    revertChange(dir1, 1); // movement

    assertEquals(root, dir1.getParent());
    assertEquals(dir1, f.getParent());
  }

  public void testRevertSeveralSubsequentalFileMovementsFromDir() throws IOException {
    VirtualFile dir1 = root.createChildDirectory(null, "dir1");
    VirtualFile dir2 = root.createChildDirectory(null, "dir2");
    VirtualFile dir3 = root.createChildDirectory(null, "dir3");
    VirtualFile f = dir1.createChildData(null, "f.txt");

    dir1.move(null, dir2);
    f.move(null, dir2);
    f.move(null, dir3);

    revertChange(dir1, 1); // movement

    assertEquals(root, dir1.getParent());
    assertEquals(dir1, f.getParent());
  }

  public void testRevertFullChangeSet() throws IOException {
    getVcs().beginChangeSet();
    VirtualFile f1 = root.createChildData(null, "f1.txt");
    root.createChildData(null, "f2.txt");
    getVcs().endChangeSet(null);

    revertLastChange(f1);

    assertNull(root.findChild("f1.txt"));
    assertNull(root.findChild("f2.txt"));
  }

  public void testRevertFullSubsequentChangeSet() throws IOException {
    VirtualFile dir = root.createChildDirectory(null, "dir");
    VirtualFile f1 = root.createChildData(null, "f1.txt");

    getVcs().beginChangeSet();
    f1.move(null, dir);
    dir.createChildData(null, "f2.txt");
    getVcs().endChangeSet(null);

    revertChange(f1, 1);

    assertNotNull(root.findChild("dir"));
    assertNull(root.findChild("f1.txt"));
    assertNull(dir.findChild("f1.txt"));
    assertNull(dir.findChild("f2.txt"));
  }

  public void testDoesNotRevertPrecedingChanges() throws IOException {
    VirtualFile f = root.createChildData(null, "f.txt");
    f.setBinaryContent(new byte[]{1}, -1, 123);
    f.setBinaryContent(new byte[]{2}, -1, 456);

    revertLastChange(f);
    assertEquals(f, root.findChild("f.txt"));
    assertEquals(1, f.contentsToByteArray()[0]);
  }

  public void testRevertLabelChange() throws IOException {
    VirtualFile f = root.createChildData(null, "f.txt");
    getVcs().putUserLabel("abc");
    f.rename(null, "ff.txt");

    revertChange(f, 1);

    assertEquals(f, root.findChild("f.txt"));
    assertNull(root.findChild("ff.txt"));
  }

  public void testChangeSetNameAfterRevert() throws IOException {
    getVcs().beginChangeSet();
    VirtualFile f = root.createChildData(null, "f.txt");
    getVcs().endChangeSet("file changed");

    revertLastChange(f);

    Revision r = getVcsRevisionsFor(root).get(0);
    assertEquals("Revert of 'file changed'", r.getCauseChangeName());
  }

  public void testChangeSetNameAfterRevertUnnamedChange() throws IOException {
    Clock.setCurrentTimestamp(new Date(2003, 00, 01, 12, 30).getTime());
    getVcs().beginChangeSet();
    VirtualFile f = root.createChildData(null, "f.txt");
    getVcs().endChangeSet(null);

    revertLastChange(f);

    Revision r = getVcsRevisionsFor(root).get(0);
    assertEquals("Revert of change made 01.01.03 12:30", r.getCauseChangeName());
  }
}
