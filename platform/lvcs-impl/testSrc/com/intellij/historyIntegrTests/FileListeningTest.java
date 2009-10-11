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


import com.intellij.history.core.ContentFactory;
import com.intellij.history.core.Paths;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.vfs.*;
import com.intellij.util.io.ReadOnlyAttributeUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class FileListeningTest extends IntegrationTestCase {
  public void testCreatingFiles() throws Exception {
    VirtualFile f = root.createChildData(null, "file.txt");

    Entry e = getVcs().findEntry(f.getPath());
    assertNotNull(e);
    assertFalse(e.isDirectory());
  }

  public void testCreatingDirectories() throws Exception {
    VirtualFile f = root.createChildDirectory(null, "dir");

    Entry e = getVcs().findEntry(f.getPath());
    assertNotNull(e);
    assertTrue(e.isDirectory());
  }

  public void testCreationOfROFile() throws Exception {
    File newFile1 = new File(createFileExternally("f1.txt"));
    File newFile2 = new File(createFileExternally("f2.txt"));
    newFile1.setReadOnly();

    VirtualFile f1 = getFS().refreshAndFindFileByIoFile(newFile1);
    VirtualFile f2 = getFS().refreshAndFindFileByIoFile(newFile2);

    assertTrue(hasVcsEntry(f1));
    assertTrue(hasVcsEntry(f2));
    assertTrue(getVcsEntry(f1).isReadOnly());
    assertFalse(getVcsEntry(f2).isReadOnly());
  }

  public void testIgnoringFilteredFileTypes() throws Exception {
    VirtualFile f = root.createChildData(null, "file.class");
    assertFalse(hasVcsEntry(f));
  }

  public void testIgnoringFilteredDirectories() throws Exception {
    VirtualFile f = root.createChildDirectory(null, FILTERED_DIR_NAME);
    assertFalse(hasVcsEntry(f));
  }

  public void testIgnoringExcludedDirectoriesAfterItsRecreation() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "dir");
    assertTrue(hasVcsEntry(dir));

    addExcludedDir(dir);
    assertFalse(hasVcsEntry(dir));

    int revCount = getVcsRevisionsFor(root).size();

    dir.delete(null);
    assertEquals(revCount, getVcsRevisionsFor(root).size());

    dir = root.createChildDirectory(null, "dir");

    // bug: excluded dir was created during fileCrated event
    // end removed bu rootsChanges event right away
    assertEquals(revCount, getVcsRevisionsFor(root).size());
    assertFalse(hasVcsEntry(dir));
  }

  public void ignoreTestChangingContentOfDeletedFileDoesNotThrowException() throws Exception {
    // todo try to write reliable test for exception handling during file events and update
    final VirtualFile f = root.createChildData(null, "f.txt");

    VirtualFileListener l = new VirtualFileAdapter() {
      @Override
      public void beforeContentsChange(VirtualFileEvent e) {
        new File(e.getFile().getPath()).delete();
      }
    };

    addFileListenerDuring(l, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        f.setBinaryContent(new byte[]{1});
      }
    });

    assertFalse(getVcs().getEntry(f.getPath()).getContent().isAvailable());
  }

  public void testChangingFileContent() throws Exception {
    VirtualFile f = root.createChildData(null, "file.txt");

    f.setBinaryContent(new byte[]{1});
    assertEquals(1, getVcsContentOf(f)[0]);

    f.setBinaryContent(new byte[]{2});
    assertEquals(2, getVcsContentOf(f)[0]);
  }

  public void testChangingFileContentOnlyAfterContentChangedEvent() throws Exception {
    final VirtualFile f = root.createChildData(null, "file.txt");
    f.setBinaryContent("before".getBytes());

    ContentChangesListener l = new ContentChangesListener(f);
    addFileListenerDuring(l, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        f.setBinaryContent("after".getBytes());
      }
    });

    assertEquals("before", l.getContentBefore());
    assertEquals("after", l.getContentAfter());
  }

  public void testRenamingFile() throws Exception {
    VirtualFile f = root.createChildData(null, "file.txt");
    f.rename(null, "file2.txt");

    assertFalse(hasVcsEntry(Paths.renamed(f.getPath(), "file.txt")));
    assertTrue(hasVcsEntry(f));
  }

  public void testRenamingFileOnlyAfterRenamedEvent() throws Exception {
    final VirtualFile f = root.createChildData(null, "old.txt");
    final boolean[] log = new boolean[4];
    final String oldPath = Paths.appended(root.getPath(), "old.txt");
    final String newPath = Paths.appended(root.getPath(), "new.txt");

    VirtualFileListener l = new VirtualFileAdapter() {
      public void beforePropertyChange(VirtualFilePropertyEvent e) {
        log[0] = hasVcsEntry(oldPath);
        log[1] = hasVcsEntry(newPath);
      }

      public void propertyChanged(VirtualFilePropertyEvent e) {
        log[2] = hasVcsEntry(oldPath);
        log[3] = hasVcsEntry(newPath);
      }
    };

    addFileListenerDuring(l, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        f.rename(null, "new.txt");
      }
    });

    assertEquals(true, log[0]);
    assertEquals(false, log[1]);
    assertEquals(false, log[2]);
    assertEquals(true, log[3]);
  }

  public void testRenamingFilteredFiles() throws Exception {
    VirtualFile f = root.createChildData(null, "file.class");
    assertFalse(hasVcsEntry(f));
    f.rename(null, "file.txt");
    assertTrue(hasVcsEntry(f));
  }

  public void testRenamingFilteredDirectoriesToNonFiltered() throws Exception {
    VirtualFile f = root.createChildDirectory(null, FILTERED_DIR_NAME);

    String filtered = Paths.appended(root.getPath(), FILTERED_DIR_NAME);
    String notFiltered = Paths.appended(root.getPath(), "not_filtered");

    assertFalse(hasVcsEntry(filtered));
    f.rename(null, "not_filtered");

    assertFalse(hasVcsEntry(filtered));
    assertTrue(hasVcsEntry(notFiltered));
  }

  public void testRenamingNonFilteredDirectoriesToFiltered() throws Exception {
    VirtualFile f = root.createChildDirectory(null, "not_filtered");

    String filtered = Paths.appended(root.getPath(), FILTERED_DIR_NAME);
    String notFiltered = Paths.appended(root.getPath(), "not_filtered");

    assertTrue(hasVcsEntry(notFiltered));
    f.rename(null, FILTERED_DIR_NAME);

    assertFalse(hasVcsEntry(notFiltered));
    assertFalse(hasVcsEntry(filtered));
  }

  public void testChangingROStatusForFile() throws Exception {
    VirtualFile f = root.createChildData(null, "f.txt");
    assertFalse(getVcsEntry(f).isReadOnly());

    ReadOnlyAttributeUtil.setReadOnlyAttribute(f, true);
    assertTrue(getVcsEntry(f).isReadOnly());

    ReadOnlyAttributeUtil.setReadOnlyAttribute(f, false);
    assertFalse(getVcsEntry(f).isReadOnly());
  }
  
  public void testIgnoringROStstusChangeForUnversionedFiles() throws Exception {
    VirtualFile f = root.createChildData(null, "f");
    ReadOnlyAttributeUtil.setReadOnlyAttribute(f, true); // shouldn't throw
  }
  
  public void testIgnoringChangeOfROStatusForDirectory() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "dir");
    assertEquals(1, getVcsRevisionsFor(dir).size());

    ReadOnlyAttributeUtil.setReadOnlyAttribute(dir, true);
    assertEquals(1, getVcsRevisionsFor(dir).size());
  }

  public void testDeletion() throws Exception {
    VirtualFile f = root.createChildDirectory(null, "f.txt");

    String path = f.getPath();
    assertTrue(hasVcsEntry(path));

    f.delete(null);
    assertFalse(hasVcsEntry(path));
  }

  public void testDeletionOfFilteredDirectoryDoesNotThrowsException() throws Exception {
    VirtualFile f = root.createChildDirectory(null, FILTERED_DIR_NAME);

    String filtered = Paths.appended(root.getPath(), FILTERED_DIR_NAME);

    assertFalse(hasVcsEntry(filtered));
    f.delete(null);

    assertFalse(hasVcsEntry(filtered));
  }

  public void testDeletingBigFiles() throws Exception {
    File tempDir = createTempDirectory();
    File tempFile = new File(tempDir, "bigFile.txt");
    OutputStream s = new FileOutputStream(tempFile);
    s.write(new byte[ContentFactory.MAX_CONTENT_LENGTH + 1]);
    s.close();

    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile);
    assertNotNull(f);

    f.move(null, root);
    assertTrue(hasVcsEntry(f));

    f.delete(null);
    assertFalse(hasVcsEntry(f));
  }
}
