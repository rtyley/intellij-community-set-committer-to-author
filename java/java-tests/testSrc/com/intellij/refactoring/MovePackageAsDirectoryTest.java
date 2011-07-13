
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesProcessor;
import junit.framework.Assert;

import java.util.Arrays;
import java.util.Comparator;

public class MovePackageAsDirectoryTest extends MultiFileTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected String getTestRoot() {
    return "/refactoring/movePackageAsDir/";
  }

  public void testMovePackage() throws Exception {
    doTest(createAction("pack1", "target"));
  }

  public void testMovePackageWithTxtFilesInside() throws Exception {
    doTest(createAction("pack1", "target"));
  }

  public void testMultipleClassesInOneFile() throws Exception {
    final boolean [] fileWasDeleted = new boolean[]{false};
    final VirtualFileAdapter fileAdapter = new VirtualFileAdapter() {
      @Override
      public void fileDeleted(VirtualFileEvent event) {
        fileWasDeleted[0] = !event.getFile().isDirectory();
      }
    };
    VirtualFileManager.getInstance().addVirtualFileListener(fileAdapter);
    try {
      doTest(createAction("pack1", "target"));
    }
    finally {
      VirtualFileManager.getInstance().removeVirtualFileListener(fileAdapter);
    }
    Assert.assertFalse("Deleted instead of moved", fileWasDeleted[0]);
  }


  public void testRemoveUnresolvedImports() throws Exception {
    doTest(createAction("pack1", "target"));
  }

  public void testXmlDirRefs() throws Exception {
    doTest(createAction("pack1", "target"));
  }

  public void testXmlEmptyDirRefs() throws Exception {
    final String packageName = "pack1";
    doTest(new MyPerformAction(packageName, "target"){
      private static final String EMPTY_TXT = "empty.txt";
      @Override
      protected void preprocessSrcDir(PsiDirectory srcDirectory) {
        final PsiFile empty = srcDirectory.findFile(EMPTY_TXT);
        assert empty != null;
        empty.delete();
      }

      @Override
      protected void postProcessTargetDir(PsiDirectory targetDirectory) {
        final PsiDirectory subdirectory = targetDirectory.findSubdirectory(packageName);
        assert subdirectory != null;
        subdirectory.createFile(EMPTY_TXT);
      }
    });
  }

  private PerformAction createAction(final String packageName, final String targetPackageName) {
    return new MyPerformAction(packageName, targetPackageName);
  }

  @Override
  protected void setupProject(VirtualFile rootDir) {
    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    final ContentEntry contentEntry = rootModel.addContentEntry(rootDir);
    final VirtualFile[] children = rootDir.getChildren();
    for (VirtualFile child : children) {
      if (child.getName().startsWith("src")) {
        contentEntry.addSourceFolder(child, false);
      }
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        rootModel.commit();
      }
    });
  }

  private class MyPerformAction implements PerformAction {
    private final String myPackageName;
    private final String myTargetPackageName;

    public MyPerformAction(String packageName, String targetPackageName) {
      myPackageName = packageName;
      myTargetPackageName = targetPackageName;
    }

    @Override
    public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
      final Comparator<PsiDirectory> directoryComparator = new Comparator<PsiDirectory>() {
        @Override
        public int compare(PsiDirectory o1, PsiDirectory o2) {
          return o1.getVirtualFile().getPresentableUrl().compareTo(o2.getVirtualFile().getPresentableUrl());
        }
      };

      final PsiPackage sourcePackage = psiFacade.findPackage(myPackageName);
      assertNotNull(sourcePackage);
      final PsiDirectory[] srcDirectories = sourcePackage.getDirectories();
      assertEquals(srcDirectories.length, 2);
      Arrays.sort(srcDirectories, directoryComparator);

      final PsiPackage targetPackage = psiFacade.findPackage(myTargetPackageName);
      assertNotNull(targetPackage);
      final PsiDirectory[] targetDirectories = targetPackage.getDirectories();
      Arrays.sort(targetDirectories, directoryComparator);
      assertTrue(targetDirectories.length > 0);
      preprocessSrcDir(srcDirectories[0]);
      new MoveDirectoryWithClassesProcessor(getProject(), new PsiDirectory[]{srcDirectories[0]}, targetDirectories[0], false, false, true, null).run();
      postProcessTargetDir(targetDirectories[0]);
      FileDocumentManager.getInstance().saveAllDocuments();
    }

    protected void postProcessTargetDir(PsiDirectory targetDirectory) {
    }

    protected void preprocessSrcDir(PsiDirectory srcDirectory) {
    }
  }
}
