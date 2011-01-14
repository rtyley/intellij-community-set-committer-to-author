package org.jetbrains.idea.svn;

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.pending.DuringChangeListManagerUpdateTestScheme;
import com.intellij.openapi.vcs.changes.ui.RollbackWorker;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class SvnChangesCorrectlyRefreshedTest extends SvnTestCase {
  private ChangeListManager clManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    clManager = ChangeListManager.getInstance(myProject);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  private class SubTree {
    private final VirtualFile myRootDir;
    private VirtualFile mySourceDir;
    private final VirtualFile myTargetDir;

    private VirtualFile myS1File;
    private VirtualFile myS2File;

    private final List<VirtualFile> myTargetFiles;
    private static final String ourS1Contents = "123";
    private static final String ourS2Contents = "abc";

    private SubTree(final VirtualFile base) throws Throwable {
      myRootDir = createDirInCommand(base, "root");
      mySourceDir = createDirInCommand(myRootDir, "source");
      myS1File = createFileInCommand(mySourceDir, "s1.txt", ourS1Contents);
      myS2File = createFileInCommand(mySourceDir, "s2.txt", ourS2Contents);

      myTargetDir = createDirInCommand(myRootDir, "target");
      myTargetFiles = new ArrayList<VirtualFile>();
      for (int i = 0; i < 10; i++) {
        myTargetFiles.add(createFileInCommand(myTargetDir, "t" + (i+10) +".txt", ourS1Contents));
      }
    }
  }

  private void sleep1000() {
    try {
      Thread.sleep(300);
    }
    catch (InterruptedException e) {
      //
    }
  }

  @Test
  public void testModificationAndAfterRevert() throws Throwable {
    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();
    try {
      Thread.sleep(300);
    }
    catch (InterruptedException e) {
      //
    }

    editFileInCommand(myProject, subTree.myS1File, "new");

    final CharSequence text1 = LoadTextUtil.loadText(subTree.myS1File);
    Assert.assertEquals("new", text1.toString());

    sleep1000();
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(subTree.myS1File.getPath()));
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    clManager.ensureUpToDate(false);
    final VcsException updateException = ((ChangeListManagerImpl)clManager).getUpdateException();
    if (updateException != null) {
      updateException.printStackTrace();
    }
    Assert.assertNull(updateException == null ? null : updateException.getMessage(), updateException);

    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {subTree.myS1File}, clManager.getDefaultListName(), clManager);

    final Collection<Change> changes = clManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject, true);
    worker.doRollback(changes, false, null, null);

    final CharSequence text = LoadTextUtil.loadText(subTree.myS1File);
    Assert.assertEquals(SubTree.ourS1Contents, text.toString());

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {}, clManager.getDefaultListName(), clManager);
  }

  @Test
  public void testRenameFileAndAfterRevert() throws Throwable {
    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();

    final String newName = "newName";
    renameFileInCommand(subTree.myS1File, newName);

    assertVF(subTree.mySourceDir, newName);

    sleep1000();
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {subTree.myS1File}, clManager.getDefaultListName(), clManager);

    final Collection<Change> changes = clManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject, true);
    worker.doRollback(changes, false, null, null);

    assertVF(subTree.mySourceDir, "s1.txt");

    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {}, clManager.getDefaultListName(), clManager);
  }

  @Test
  public void testMoveFileAndAfterRevert() throws Throwable {
    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();

    moveFileInCommand(subTree.myS1File, subTree.myTargetDir);

    assertVF(subTree.myTargetDir, "s1.txt");

    sleep1000();
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {subTree.myS1File}, clManager.getDefaultListName(), clManager);

    final Collection<Change> changes = clManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject, true);
    worker.doRollback(changes, false, null, null);

    assertVF(subTree.mySourceDir, "s1.txt");

    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {}, clManager.getDefaultListName(), clManager);
  }

  @Test
  public void testRenameDirAndAfterRevert() throws Throwable {
    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();

    final String newName = "newName";
    renameFileInCommand(subTree.mySourceDir, newName);

    assertVF(subTree.myRootDir, newName);
    assertVF(subTree.mySourceDir, "s1.txt");
    assertVF(subTree.mySourceDir, "s2.txt");

    sleep1000();
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {subTree.mySourceDir, subTree.myS1File, subTree.myS2File},
                                                                clManager.getDefaultListName(), clManager);

    final Collection<Change> changes = clManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject, true);
    worker.doRollback(changes, false, null, null);

    subTree.mySourceDir = assertVF(subTree.myRootDir, "source");
    Assert.assertTrue(subTree.mySourceDir.getPath().endsWith("/root/source"));
    assertVF(subTree.mySourceDir, "s1.txt");
    assertVF(subTree.mySourceDir, "s2.txt");

    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {}, clManager.getDefaultListName(), clManager);
  }

  @Test
  public void testMoveDirEditFileAndAfterRevert() throws Throwable {
    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();

    moveFileInCommand(subTree.mySourceDir, subTree.myTargetDir);
    Assert.assertTrue(subTree.mySourceDir.getPath().endsWith("/target/source"));
    assertVF(subTree.myTargetDir, "source");

    editFileInCommand(myProject, subTree.myS1File, "new");
    final CharSequence text1 = LoadTextUtil.loadText(subTree.myS1File);
    Assert.assertEquals("new", text1.toString());

    sleep1000();
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {subTree.mySourceDir, subTree.myS1File, subTree.myS2File},
                                                                clManager.getDefaultListName(), clManager);

    final Collection<Change> changes = clManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject, true);
    worker.doRollback(changes, false, null, null);

    subTree.mySourceDir = assertVF(subTree.myRootDir, "source");
    Assert.assertTrue(subTree.mySourceDir.getPath().endsWith("/root/source"));
    
    subTree.myS1File = assertVF(subTree.mySourceDir, "s1.txt");
    subTree.myS2File = assertVF(subTree.mySourceDir, "s2.txt");
    final CharSequence text = LoadTextUtil.loadText(subTree.myS1File);
    Assert.assertEquals(SubTree.ourS1Contents, text.toString());

    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {}, clManager.getDefaultListName(), clManager);
  }
  
  @Test
  public void testAddDirEditFileAndAfterRevert() throws Throwable {
    final SubTree subTree = new SubTree(myWorkingCopyDir);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    clManager.ensureUpToDate(false);
    final List<VirtualFile> files = getAllFiles(subTree);
    
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(VfsUtil.toVirtualFileArray(files), clManager.getDefaultListName(), clManager);

    final Collection<Change> changes = clManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject, true);
    worker.doRollback(changes, false, null, null);

    assertVF(subTree.myRootDir, "source");
    assertVF(subTree.mySourceDir, "s1.txt");
    assertVF(subTree.myRootDir, "target");

    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {}, clManager.getDefaultListName(), clManager);

    for (VirtualFile file : files) {
      Assert.assertTrue(file.getPath(), clManager.isUnversioned(file));
    }
  }

  private List<VirtualFile> getAllFiles(final SubTree subTree) {
    final List<VirtualFile> files = new ArrayList<VirtualFile>();
    files.addAll(Arrays.asList(subTree.myRootDir, subTree.mySourceDir, subTree.myS2File, subTree.myS1File, subTree.myTargetDir));
    files.addAll(subTree.myTargetFiles);
    return files;
  }

  @Test
  public void testDeleteDirEditFileAndAfterRevert() throws Throwable {
    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();

    deleteFileInCommand(subTree.myRootDir);
    sleep1000();

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    clManager.ensureUpToDate(false);
    final List<VirtualFile> files = getAllFiles(subTree);
    DuringChangeListManagerUpdateTestScheme.checkDeletedFilesAreInList(VfsUtil.toVirtualFileArray(files), clManager.getDefaultListName(),
                                                                       clManager);

    final Collection<Change> changes = clManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject, true);
    worker.doRollback(changes, false, null, null);

    assertVF(subTree.myRootDir, "source");
    assertVF(subTree.mySourceDir, "s1.txt");
    assertVF(subTree.myRootDir, "target");

    assertVF(subTree.myTargetDir, "t11.txt");
    assertVF(subTree.myTargetDir, "t13.txt");
    assertVF(subTree.myTargetDir, "t14.txt");
    assertVF(subTree.myTargetDir, "t15.txt");

    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {}, clManager.getDefaultListName(), clManager);
  }
  
  private VirtualFile assertVF(final VirtualFile parent, final String name) {
    final VirtualFile[] files = parent.getChildren();
    final StringBuilder sb = new StringBuilder("Files: ");
    for (VirtualFile file : files) {
      sb.append(file.getName()).append(' ');
      if (name.equals(file.getName())) return file;
    }
    System.out.println("not found as child");
    Assert.assertNotNull(LocalFileSystem.getInstance().findFileByIoFile(new File(parent.getPath(), name)));
    //Assert.assertTrue(sb.toString(), false);
    return null;
  }
}
