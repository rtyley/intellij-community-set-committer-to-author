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


import com.intellij.history.core.Paths;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Semaphore;

import static com.intellij.history.core.LocalVcsTestCase.list;

public class ExternalChangesAndRefreshingTest extends IntegrationTestCase {
  public void testRefreshingSynchronously() throws Exception {
    doTestRefreshing(false);
  }

  public void testRefreshingAsynchronously() throws Exception {
    doTestRefreshing(true);
  }

  @Override
  protected void runBareRunnable(Runnable r) throws Throwable {
    if (getName().equals("testRefreshingAsynchronously")) {
      // this methods waits for another thread to finish, that leds
      // to deadlock in swing-thread. Therefore we have to run this test
      // outside of swing-thread
      r.run();
    }
    else {
      super.runBareRunnable(r);
    }
  }

  private void doTestRefreshing(boolean async) throws Exception {
    String path1 = createFileExternally("f1.txt");
    String path2 = createFileExternally("f2.txt");

    assertFalse(hasVcsEntry(path1));
    assertFalse(hasVcsEntry(path2));

    refreshVFS(async);

    assertTrue(hasVcsEntry(path1));
    assertTrue(hasVcsEntry(path2));

    assertEquals(2, getVcsRevisionsFor(root).size());
  }

  public void testChangeSetName() throws Exception {
    createFileExternally("f.txt");
    refreshVFS();
    Revision r = getVcsRevisionsFor(root).get(0);
    assertEquals("External change", r.getCauseChangeName());
  }

  public void testRefreshDuringCommand() {
    // shouldn't throw
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        refreshVFS();
      }
    }, "", null);
  }

  public void testRefreshingSpecifiedFiles() throws Exception {
    String f1 = createFileExternally("f1.txt");
    String f2 = createFileExternally("f2.txt");

    LocalFileSystem.getInstance().refreshIoFiles(list(new File(f1), new File(f2)));

    assertTrue(hasVcsEntry(f1));
    assertTrue(hasVcsEntry(f2));
  }

  public void testCommandDuringRefresh() throws Exception {
    createFileExternally("f.txt");

    VirtualFileListener l = new VirtualFileAdapter() {
      @Override
      public void fileCreated(VirtualFileEvent e) {
        executeSomeCommand();
      }
    };

    // shouldn't throw
    addFileListenerDuring(l, new Runnable() {
      public void run() {
        refreshVFS();
      }
    });
  }

  private void executeSomeCommand() {
    CommandProcessor.getInstance().executeCommand(myProject, EmptyRunnable.getInstance(), "", null);
  }

  public void testContentOfFileChangedDuringRefresh() throws Exception {
    final VirtualFile f = root.createChildData(null, "file.txt");
    f.setBinaryContent("before".getBytes());

    performAllPendingJobs();

    ContentChangesListener l = new ContentChangesListener(f);
    addFileListenerDuring(l, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        changeFileExternally(f.getPath(), "after");
        refreshVFS();
      }
    });

    // todo unrelable test because content recorded before LvcsFileListener does its job
    assertEquals("before", l.getContentBefore());
    assertEquals("after", l.getContentAfter());
  }

  private void performAllPendingJobs() {
    refreshVFS();
  }

  public void testFileCreationDuringRefresh() throws Exception {
    final String path = createFileExternally("f.txt");
    changeFileExternally(path, "content");

    final String[] content = new String[1];
    VirtualFileListener l = new VirtualFileAdapter() {
      @Override
      public void fileCreated(VirtualFileEvent e) {
        try {
          if (!e.getFile().getPath().equals(path)) return;
          content[0] = new String(e.getFile().contentsToByteArray());
        }
        catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      }
    };

    addFileListenerDuring(l, new Runnable() {
      public void run() {
        refreshVFS();
      }
    });
    assertEquals("content", content[0]);
  }

  public void ignoreTestCreationOfExcludedDirectoryDuringRefresh() throws Exception {
    // todo does not work due to FileListener order. FileIndex gets event later than Lvcs.

    VirtualFile dir = root.createChildDirectory(null, "EXCLUDED");
    String p = dir.getPath();

    assertTrue(hasVcsEntry(p));

    ModifiableRootModel m = ModuleRootManager.getInstance(myModule).getModifiableModel();
    m.getContentEntries()[0].addExcludeFolder(dir);
    m.commit();

    assertFalse(hasVcsEntry(p));

    dir.delete(null);

    createDirectoryExternally("EXCLUDED");
    refreshVFS();

    assertFalse(hasVcsEntry(p));
  }

  public void testDeletionOfFilteredDirectoryExternallyDoesNotThrowExceptionDuringRefresh() throws Exception {
    VirtualFile f = root.createChildDirectory(null, FILTERED_DIR_NAME);
    String path = Paths.appended(root.getPath(), FILTERED_DIR_NAME);

    assertFalse(hasVcsEntry(path));

    new File(path).delete();
    refreshVFS();

    assertFalse(hasVcsEntry(path));
  }

  public void testCreationOfExcludedDirWithFilesDuringRefreshShouldNotThrowException() throws Exception {
    // there was a problem with the DirectoryIndex - the files that were created during the refresh
    // were not correctly excluded, thereby causing the LocalHistory to fail during addition of 
    // files under the excluded dir.

    File targetDir = createTargetDir();

    FileUtil.copyDir(targetDir, new File(root.getPath(), "target"));
    VirtualFileManager.getInstance().refresh(false);

    VirtualFile classes = root.findFileByRelativePath("target/classes");
    addExcludedDir(classes);
    classes.getParent().delete(null);

    FileUtil.copyDir(targetDir, new File(root.getPath(), "target"));
    VirtualFileManager.getInstance().refresh(false); // shouldn't throw
  }

  private File createTargetDir() throws IOException {
    File result = createTempDirectory();
    File classes = new File(result, "classes");
    classes.mkdir();
    new File(classes, "bak.txt").createNewFile();
    return result;
  }

  private void refreshVFS() {
    refreshVFS(false);
  }

  private void refreshVFS(boolean async) {
    try {
      final Semaphore s = new Semaphore(1);
      s.acquire();
      VirtualFileManager.getInstance().refresh(async, new Runnable() {
        public void run() {
          s.release();
        }
      });
      s.acquire();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}