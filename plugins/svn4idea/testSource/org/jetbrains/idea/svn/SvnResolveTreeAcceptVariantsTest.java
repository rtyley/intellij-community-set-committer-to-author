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
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestClientRunner;
import com.intellij.util.Processor;
import junit.framework.Assert;
import org.jetbrains.idea.svn.treeConflict.SvnTreeConflictResolver;
import org.junit.Before;
import org.junit.Test;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 5/3/12
 * Time: 6:14 PM
 */
public class SvnResolveTreeAcceptVariantsTest extends SvnTestCase {
  private VirtualFile myTheirs;
  private SvnClientRunnerImpl mySvnClientRunner;
  private SvnVcs myVcs;
  private VcsDirtyScopeManager myDirtyScopeManager;
  private ChangeListManager myChangeListManager;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    myTheirs = myTempDirFixture.findOrCreateDir("theirs");
    final TestClientRunner testClientRunner = new TestClientRunner(true, myClientBinaryPath);
    mySvnClientRunner = new SvnClientRunnerImpl(testClientRunner);
    clearWc(true);

    myVcs = SvnVcs.getInstance(myProject);
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
    myChangeListManager = ChangeListManager.getInstance(myProject);
    myTraceClient = true;
  }

  private void clearWc(final boolean withSvn) {
    myWorkingCopyDir.refresh(false, true);
    /*VfsUtil.processFilesRecursively(myWorkingCopyDir, new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile file) {
        if (myWorkingCopyDir.equals(file) || SvnUtil.isAdminDirectory(file)) return true;
        FileUtil.delete(new File(file.getPath()));
        return true;
      }
    }, new Convertor<VirtualFile, Boolean>() {
                                      @Override
                                      public Boolean convert(VirtualFile o) {
                                        return withSvn || ! SvnUtil.isAdminDirectory(o);
                                      }
                                    });*/
    clearDirInCommand(myWorkingCopyDir, new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile file) {
        return withSvn || ! SvnUtil.isAdminDirectory(file);
      }
    });
    myWorkingCopyDir.refresh(false, true);
  }

  @Test
  public void testMineFull() throws Exception {
    int cnt = 0;
    myWorkingCopyDir = createDirInCommand(myWorkingCopyDir, "test--");
    myTheirs = createDirInCommand(myTheirs, "theirs--");
    // todo debug
    //final TreeConflictData.Data data = TreeConflictData.DirToDir.MINE_EDIT_THEIRS_DELETE;
    for (final TreeConflictData.Data data : TreeConflictData.ourAll) {
      if (myTraceClient) {
        System.out.println("========= TEST " + getTestName(data) + " =========");
      }

      myWorkingCopyDir = createDirInCommand(myWorkingCopyDir.getParent(), "test" + cnt);
      myTheirs = createDirInCommand(myTheirs.getParent(), "theirs" + cnt);
      mySvnClientRunner.checkout(myRepoUrl, myTheirs);
      mySvnClientRunner.checkout(myRepoUrl, myWorkingCopyDir);

      createSubTree();
      final ConflictCreator creator = new ConflictCreator(myProject, myTheirs, myWorkingCopyDir, data, mySvnClientRunner);
      creator.create();

      myDirtyScopeManager.markEverythingDirty();
      myChangeListManager.ensureUpToDate(false);
      myDirtyScopeManager.markEverythingDirty();
      myChangeListManager.ensureUpToDate(false);

      final String conflictFile = data.getConflictFile();

      final File conflictIoFile = new File(myWorkingCopyDir.getPath(), conflictFile);
      final FilePathImpl filePath = new FilePathImpl(conflictIoFile, conflictIoFile.isDirectory());
      final Change change = myChangeListManager.getChange(filePath);
      Assert.assertNotNull(change);
      Assert.assertTrue(change instanceof ConflictedSvnChange);
      final SvnRevisionNumber committedRevision =
        change.getBeforeRevision() != null ? (SvnRevisionNumber)change.getBeforeRevision().getRevisionNumber() : null;
      //SvnRevisionNumber committedRevision = new SvnRevisionNumber(SVNRevision.create(cnt * 2 + 1));
      final SvnTreeConflictResolver resolver = new SvnTreeConflictResolver(myVcs, filePath, committedRevision, null);

      resolver.resolveSelectMineFull(((ConflictedSvnChange)change).getBeforeDescription());

      myTheirs.refresh(false, true);
      myWorkingCopyDir.refresh(false, true);
      checkStatusesAfterMineFullResolve(data, conflictIoFile);

      ++ cnt;
    }
  }

  private void checkStatusesAfterMineFullResolve(TreeConflictData.Data data, File conflictIoFile) {
    SVNStatus conflStatus = SvnUtil.getStatus(myVcs, conflictIoFile);
    Assert.assertTrue(createTestFailedComment(data, conflictIoFile.getPath()) + " tree conflict resolved",
                      conflStatus.getTreeConflict() == null);
    Collection<TreeConflictData.FileData> leftFiles = data.getLeftFiles();
    for (TreeConflictData.FileData file : leftFiles) {
      File exFile = new File(myWorkingCopyDir.getPath(), file.myRelativePath);
      final SVNStatus status = SvnUtil.getStatus(myVcs, exFile);
      boolean theirsExists = new File(myTheirs.getPath(), file.myRelativePath).exists();

      if (SVNStatusType.STATUS_UNVERSIONED.equals(file.myNodeStatus)) {
        Assert.assertTrue(createTestFailedComment(data, exFile.getPath()) + " (file exists)", exFile.exists());
        if (theirsExists) {
          // should be deleted
          Assert.assertTrue(createTestFailedComment(data, exFile.getPath()) + " (unversioned)", status == null || SVNStatusType.STATUS_DELETED.equals(status.getNodeStatus()));
        } else {
          // unversioned
          Assert.assertTrue(createTestFailedComment(data, exFile.getPath()) + " (unversioned)", status == null || SVNStatusType.STATUS_UNVERSIONED.equals(status.getNodeStatus()));
        }
      } else if (SVNStatusType.STATUS_DELETED.equals(file.myNodeStatus)) {
        Assert.assertTrue(createTestFailedComment(data, exFile.getPath()) + " (deleted status)", status != null && file.myNodeStatus.equals(status.getNodeStatus()));
      } else if (SVNStatusType.STATUS_ADDED.equals(file.myNodeStatus)) {
        Assert.assertTrue(createTestFailedComment(data, exFile.getPath()) + " (file exists)", exFile.exists());
        if (theirsExists) {
          Assert.assertTrue(createTestFailedComment(data, exFile.getPath()) + " (added status)", status != null && SVNStatusType.STATUS_REPLACED.equals(status.getNodeStatus()));
        } else {
          Assert.assertTrue(createTestFailedComment(data, exFile.getPath()) + " (added status)", status != null && SVNStatusType.STATUS_ADDED.equals(status.getNodeStatus()));
        }
      } else {
        if (SVNStatusType.STATUS_ADDED.equals(status.getNodeStatus())) {
          // in theirs -> deleted
          Assert.assertFalse(createTestFailedComment(data, file.myRelativePath) + " check deleted in theirs", theirsExists);
        } else {
          if (theirsExists) {
            Assert.assertTrue(createTestFailedComment(data, exFile.getPath()) + " (normal node status)", status != null && SVNStatusType.STATUS_REPLACED.equals(status.getNodeStatus()));
          } else {
            Assert.assertTrue(createTestFailedComment(data, exFile.getPath()) + " (normal node status)", status != null &&
              (SVNStatusType.STATUS_NORMAL.equals(status.getNodeStatus()) || SVNStatusType.STATUS_MODIFIED.equals(status.getNodeStatus())));
          }
        }
        Assert.assertTrue(createTestFailedComment(data, exFile.getPath()) + " (modified text status)", status != null && file.myContentsStatus.equals(status.getContentsStatus()));
      }
    }
  }

  private String createTestFailedComment(final TreeConflictData.Data data, final String path) {
    return "Check failed for test: " + getTestName(data) + " and file: " + path + " in: " + myWorkingCopyDir.getPath();
  }

  @Test
  public void testTheirsFull() throws Exception {
    int cnt = 0;
    myWorkingCopyDir = createDirInCommand(myWorkingCopyDir, "test--");
    myTheirs = createDirInCommand(myTheirs, "theirs--");
    // todo debug
    //final TreeConflictData.Data data = TreeConflictData.FileToFile.MINE_MOVE_THEIRS_ADD;
    for (final TreeConflictData.Data data : TreeConflictData.ourAll) {
      if (myTraceClient) {
        System.out.println("========= TEST " + getTestName(data) + " =========");
      }

      myWorkingCopyDir = createDirInCommand(myWorkingCopyDir.getParent(), "test" + cnt);
      myTheirs = createDirInCommand(myTheirs.getParent(), "theirs" + cnt);
      mySvnClientRunner.checkout(myRepoUrl, myTheirs);
      mySvnClientRunner.checkout(myRepoUrl, myWorkingCopyDir);

      createSubTree();
      final ConflictCreator creator = new ConflictCreator(myProject, myTheirs, myWorkingCopyDir, data, mySvnClientRunner);
      creator.create();

      myDirtyScopeManager.markEverythingDirty();
      myChangeListManager.ensureUpToDate(false);
      myDirtyScopeManager.markEverythingDirty();
      myChangeListManager.ensureUpToDate(false);

      final String conflictFile = data.getConflictFile();

      final File conflictIoFile = new File(myWorkingCopyDir.getPath(), conflictFile);
      final FilePathImpl filePath = new FilePathImpl(conflictIoFile, conflictIoFile.isDirectory());
      final Change change = myChangeListManager.getChange(filePath);
      Assert.assertNotNull(change);
      Assert.assertTrue(change instanceof ConflictedSvnChange);
      final SvnRevisionNumber committedRevision =
        change.getBeforeRevision() != null ? (SvnRevisionNumber)change.getBeforeRevision().getRevisionNumber() : null;
      FilePath beforePath = null;
      if (change.isMoved() || change.isRenamed()) {
        beforePath = change.getBeforeRevision().getFile();
      }
      //SvnRevisionNumber committedRevision = new SvnRevisionNumber(SVNRevision.create(cnt * 2 + 1));
      final SvnTreeConflictResolver resolver = new SvnTreeConflictResolver(myVcs, filePath, committedRevision, beforePath);

      resolver.resolveSelectTheirsFull(((ConflictedSvnChange) change).getBeforeDescription());

    myTheirs.refresh(false, true);
    myWorkingCopyDir.refresh(false, true);
      VfsUtil.processFileRecursivelyWithoutIgnored(myTheirs, new Processor<VirtualFile>() {
        @Override
        public boolean process(VirtualFile file) {
          final String relative = VfsUtil.getRelativePath(file, myTheirs, File.separatorChar);
          File workingFile = new File(myWorkingCopyDir.getPath(), relative);
          boolean exists = workingFile.exists();
          if (! exists) {
            String[] excluded = data.getExcludeFromToTheirsCheck();
            if (excluded != null && Arrays.asList(excluded).contains(relative)) {
              return true;
            }
            Assert.assertTrue("Check failed for test: " + getTestName(data) + " and file: " + relative + " in: " + myWorkingCopyDir.getPath(),
                          exists);
          }
          SVNInfo theirsInfo = myVcs.getInfo(new File(file.getPath()));
          SVNInfo thisInfo = myVcs.getInfo(workingFile);
          if (theirsInfo != null) {
            Assert.assertEquals("Check failed for test: " + getTestName(data) + " and file: " + relative + " in: " + myWorkingCopyDir.getPath() +
                                ", theirs: " + theirsInfo.getRevision().getNumber() + ", mine: " + thisInfo.getRevision().getNumber(),
                                theirsInfo.getRevision().getNumber(), thisInfo.getRevision().getNumber());
          }
          return true;
        }
      });
      ++ cnt;
    }
  }

  private String getTestName(final TreeConflictData.Data data) {
    Class<?>[] classes = TreeConflictData.class.getDeclaredClasses();
    for (Class<?> aClass : classes) {
      String s = testFields(data, aClass);
      if (s != null) return aClass.getName() + "#" + s;
    }
    return null;
  }

  private String testFields(TreeConflictData.Data data, final Class clazz) {
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      int modifiers = field.getModifiers();
      try {
        if ((Modifier.STATIC & modifiers) != 0 && data == field.get(null)) {
          return field.getName();
        }
      }
      catch (IllegalAccessException e) {
        e.printStackTrace();
        return null;
      }
    }
    return null;
  }

  private void createSubTree() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    clearWc(false);
    mySvnClientRunner.checkin(myWorkingCopyDir);
    sleep(10);
    final SubTree subTree = new SubTree(myWorkingCopyDir);
    mySvnClientRunner.checkin(myWorkingCopyDir);
    sleep(10);
    mySvnClientRunner.update(myTheirs);
    mySvnClientRunner.update(myWorkingCopyDir);
    sleep(10);

    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    disableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  private static void sleep(final int millis) {
    try {
      Thread.sleep(millis);
    }
    catch (InterruptedException ignore) { }
  }
}
