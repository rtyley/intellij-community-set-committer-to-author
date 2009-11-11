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

package com.intellij.historyIntegrTests;


import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.DeleteChange;
import com.intellij.history.core.changes.StructuralChange;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.io.ReadOnlyAttributeUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class FileListeningTest extends IntegrationTestCase {
  public void testCreatingFiles() throws Exception {
    VirtualFile f = createFile("file.txt");
    assertEquals(1, getRevisionsFor(f).size());
  }

  public void testCreatingDirectories() throws Exception {
    VirtualFile f = createDirectory("dir");
    assertEquals(1, getRevisionsFor(f).size());
  }

  public void testIgnoringFilteredFileTypes() throws Exception {
    int before = getRevisionsFor(myRoot).size();
    createFile("file.hprof");

    assertEquals(before, getRevisionsFor(myRoot).size());
  }

  public void testIgnoringFilteredDirectories() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    createDirectory(FILTERED_DIR_NAME);
    assertEquals(before, getRevisionsFor(myRoot).size());
  }

  public void testIgnoringFilesRecursively() throws Exception {
    addExcludedDir(myRoot.getPath() + "/dir/subdir");
    addContentRoot(createModule("foo"), myRoot.getPath() + "/dir/subdir/subdir2");

    String dir1 = createDirectoryExternally("dir");
    String f1 = createFileExternally("dir/f.txt");
    createFileExternally("dir/f.class");
    createFileExternally("dir/subdir/f.txt");
    String dir2 = createDirectoryExternally("dir/subdir/subdir2");
    String f2 = createFileExternally("dir/subdir/subdir2/f.txt");

    LocalFileSystem.getInstance().refresh(false);

    List<Change> changes = getVcs().getChangeListInTests().getChangesInTests().get(0).getChanges();
    assertEquals(4, changes.size());
    assertEquals(dir1, ((StructuralChange)changes.get(0)).getPath());
    assertEquals(dir2, ((StructuralChange)changes.get(1)).getPath());
    assertEquals(f2, ((StructuralChange)changes.get(2)).getPath());
    assertEquals(f1, ((StructuralChange)changes.get(3)).getPath());
  }

  public void testChangingFileContent() throws Exception {
    VirtualFile f = createFile("file.txt");
    assertEquals(1, getRevisionsFor(f).size());

    f.setBinaryContent(new byte[]{1});
    assertEquals(2, getRevisionsFor(f).size());

    f.setBinaryContent(new byte[]{2});
    assertEquals(3, getRevisionsFor(f).size());
  }

  public void testRenamingFile() throws Exception {
    VirtualFile f = createFile("file.txt");
    assertEquals(1, getRevisionsFor(f).size());

    f.rename(null, "file2.txt");
    assertEquals(2, getRevisionsFor(f).size());
  }

  public void testRenamingFileOnlyAfterRenamedEvent() throws Exception {
    final VirtualFile f = createFile("old.txt");

    final int[] log = new int[2];
    VirtualFileListener l = new VirtualFileAdapter() {
      public void beforePropertyChange(VirtualFilePropertyEvent e) {
        log[0] = getRevisionsFor(f).size();
      }

      public void propertyChanged(VirtualFilePropertyEvent e) {
        log[1] = getRevisionsFor(f).size();
      }
    };

    assertEquals(1, getRevisionsFor(f).size());

    addFileListenerDuring(l, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        f.rename(null, "new.txt");
      }
    });

    assertEquals(1, log[0]);
    assertEquals(2, log[1]);
  }

  public void testRenamingFilteredFileToNonFiltered() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    VirtualFile f = createFile("file.hprof");
    assertEquals(before, getRevisionsFor(myRoot).size());

    f.rename(null, "file.txt");
    assertEquals(before + 1, getRevisionsFor(myRoot).size());

    assertEquals(2, getRevisionsFor(f).size());
  }

  public void testRenamingNonFilteredFileToFiltered() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    VirtualFile f = createFile("file.txt");
    assertEquals(before + 1, getRevisionsFor(myRoot).size());

    f.rename(null, "file.hprof");
    assertEquals(before + 2, getRevisionsFor(myRoot).size());
  }

  public void testRenamingFilteredDirectoriesToNonFiltered() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    VirtualFile f = createFile(FILTERED_DIR_NAME);
    assertEquals(before, getRevisionsFor(myRoot).size());

    f.rename(null, "not_filtered");
    assertEquals(before + 1, getRevisionsFor(myRoot).size());

    assertEquals(2, getRevisionsFor(f).size());
  }

  public void testRenamingNonFilteredDirectoriesToFiltered() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    VirtualFile f = createDirectory("not_filtered");
    assertEquals(before + 1, getRevisionsFor(myRoot).size());

    f.rename(null, FILTERED_DIR_NAME);
    assertEquals(before + 2, getRevisionsFor(myRoot).size());
  }

  public void testChangingROStatusForFile() throws Exception {
    VirtualFile f = createFile("f.txt");
    assertEquals(1, getRevisionsFor(f).size());

    ReadOnlyAttributeUtil.setReadOnlyAttribute(f, true);
    assertEquals(2, getRevisionsFor(f).size());

    ReadOnlyAttributeUtil.setReadOnlyAttribute(f, false);
    assertEquals(3, getRevisionsFor(f).size());
  }
  
  public void testIgnoringROStstusChangeForUnversionedFiles() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    VirtualFile f = createFile("f.hprof");
    ReadOnlyAttributeUtil.setReadOnlyAttribute(f, true); // shouldn't throw

    assertEquals(before, getRevisionsFor(myRoot).size());
  }
  
  public void testIgnoringChangeOfROStatusForDirectory() throws Exception {
    VirtualFile dir = createDirectory("dir");
    assertEquals(1, getRevisionsFor(dir).size());

    ReadOnlyAttributeUtil.setReadOnlyAttribute(dir, true);
    assertEquals(1, getRevisionsFor(dir).size());
  }

  public void testDeletion() throws Exception {
    VirtualFile f = createDirectory("f.txt");

    int before = getRevisionsFor(myRoot).size();

    f.delete(null);
    assertEquals(before + 1, getRevisionsFor(myRoot).size());
  }

  public void testDeletionOfFilteredDirectoryDoesNotThrowsException() throws Exception {
    int before = getRevisionsFor(myRoot).size();

    VirtualFile f = createDirectory(FILTERED_DIR_NAME);
    f.delete(null);
    assertEquals(before, getRevisionsFor(myRoot).size());
  }

  public void testDeletionDoesNotVersionIgnoredFilesRecursively() throws Exception {
    String dir1 = createDirectoryExternally("dir");
    String f1 = createFileExternally("dir/f.txt");
    createFileExternally("dir/f.class");
    createFileExternally("dir/subdir/f.txt");
    String dir2 = createDirectoryExternally("dir/subdir/subdir2");
    String f2 = createFileExternally("dir/subdir/subdir2/f.txt");

    LocalFileSystem.getInstance().refresh(false);

    addExcludedDir(myRoot.getPath() + "/dir/subdir");
    addContentRoot(myRoot.getPath() + "/dir/subdir/subdir2");

    LocalFileSystem.getInstance().findFileByPath(dir1).delete(this);

    List<Change> changes = getVcs().getChangeListInTests().getChangesInTests().get(0).getChanges();
    assertEquals(1, changes.size());
    Entry e = ((DeleteChange)changes.get(0)).getDeletedEntry();
    assertEquals(2, e.getChildren().size());
    assertEquals("f.txt", e.getChildren().get(0).getName());
    assertEquals("subdir", e.getChildren().get(1).getName());
    assertEquals(1, e.getChildren().get(1).getChildren().size());
    assertEquals("subdir2", e.getChildren().get(1).getChildren().get(0).getName());
  }

  public void testCreationAndDeletionOfUnversionedFile() throws IOException {
    addExcludedDir(myRoot.getPath() + "/dir");
    
    Module m = createModule("foo");
    addContentRoot(m, myRoot.getPath() + "/dir/subDir");

    createFileExternally("dir/subDir/file.txt");
    LocalFileSystem.getInstance().refresh(false);

    FileUtil.delete(new File(myRoot.getPath() + "/dir"));
    LocalFileSystem.getInstance().refresh(false);

    createFileExternally("dir/subDir/file.txt");
    LocalFileSystem.getInstance().refresh(false);

    List<Revision> revs = getRevisionsFor(myRoot);
    assertEquals(4, revs.size());
    assertNotNull(revs.get(0).getEntry().findEntry("dir/subDir/file.txt"));
    assertNull(revs.get(1).getEntry().findEntry("dir/subDir/file.txt"));
    assertNotNull(revs.get(2).getEntry().findEntry("dir/subDir/file.txt"));
    assertNull(revs.get(3).getEntry().findEntry("dir/subDir/file.txt"));
  }

  public void testCreationAndDeletionOfFileUnderUnversionedDir() throws IOException {
    addExcludedDir(myRoot.getPath() + "/dir");

    Module m = createModule("foo");
    addContentRoot(m, myRoot.getPath() + "/dir/subDir");

    createFileExternally("dir/subDir/file.txt");
    LocalFileSystem.getInstance().refresh(false);

    FileUtil.delete(new File(myRoot.getPath() + "/dir/subDir"));
    LocalFileSystem.getInstance().refresh(false);

    createFileExternally("dir/subDir/file.txt");
    LocalFileSystem.getInstance().refresh(false);

    List<Revision> revs = getRevisionsFor(myRoot);
    assertEquals(4, revs.size());
    assertNotNull(revs.get(0).getEntry().findEntry("dir/subDir/file.txt"));
    assertNull(revs.get(1).getEntry().findEntry("dir/subDir"));
    assertNotNull(revs.get(2).getEntry().findEntry("dir/subDir/file.txt"));
    assertNull(revs.get(3).getEntry().findEntry("dir/subDir"));
  }

}
