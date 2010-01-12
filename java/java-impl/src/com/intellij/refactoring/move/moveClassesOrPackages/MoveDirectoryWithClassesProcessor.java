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

/*
 * User: anna
 * Date: 28-Dec-2009
 */
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MoveDirectoryWithClassesProcessor extends BaseRefactoringProcessor {
  private final PsiDirectory[] myDirectories;
  private PsiDirectory myTargetDirectory;
  private boolean mySearchInComments;
  private boolean mySearchInNonJavaFiles;
  private Map<PsiClass, TargetDirectoryWrapper> myClassesToMove;
  private NonCodeUsageInfo[] myNonCodeUsages;
  private MoveCallback myMoveCallback;

  public MoveDirectoryWithClassesProcessor(Project project,
                                           PsiDirectory[] directories,
                                           PsiDirectory targetDirectory,
                                           boolean searchInComments,
                                           boolean searchInNonJavaFiles,
                                           boolean includeSelf,
                                           MoveCallback moveCallback) {
    super(project);
    myDirectories = directories;
    myTargetDirectory = targetDirectory;
    mySearchInComments = searchInComments;
    mySearchInNonJavaFiles = searchInNonJavaFiles;
    myMoveCallback = moveCallback;
    myClassesToMove = new HashMap<PsiClass, TargetDirectoryWrapper>();
    for (PsiDirectory dir : directories) {
      collectClasses(myClassesToMove, dir, includeSelf ? dir.getParentDirectory() : dir, getTargetDirectory(dir));
    }
  }

  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    PsiElement[] elements = new PsiElement[myClassesToMove.size()];
    final PsiClass[] classes = myClassesToMove.keySet().toArray(new PsiClass[myClassesToMove.keySet().size()]);
    System.arraycopy(classes, 0, elements, 0, classes.length);
    return new MoveClassesOrPackagesViewDescriptor(elements, false, false, getTargetName());
  }

  protected String getTargetName() {
    return RefactoringUIUtil.getDescription(getTargetDirectory(null).getTargetDirectory(), false);
  }

  @NotNull
  @Override
  public UsageInfo[] findUsages() {
    final List<UsageInfo> usages = new ArrayList<UsageInfo>();
    final Set<String> packageNames = new HashSet<String>();
    for (PsiClass psiClass : myClassesToMove.keySet()) {
      Collections.addAll(usages, MoveClassesOrPackagesUtil.findUsages(psiClass, mySearchInComments, mySearchInNonJavaFiles, psiClass.getName()));
      final String fqName = psiClass.getQualifiedName();
      assert fqName != null;
      packageNames.add(StringUtil.getPackageName(fqName));
    }
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
    for (String packageName : packageNames) {
      final PsiPackage aPackage = psiFacade.findPackage(packageName);
      if (aPackage != null) {
        boolean remainsNothing = true;
        for (PsiDirectory packageDirectory : aPackage.getDirectories()) {
          if (!isUnderRefactoring(packageDirectory)) {
            remainsNothing = false;
            break;
          }
        }
        if (remainsNothing) {
          for (PsiReference reference : ReferencesSearch.search(aPackage)) {
            final PsiElement element = reference.getElement();
            final PsiImportStatementBase statementBase = PsiTreeUtil.getParentOfType(element, PsiImportStatementBase.class);
            if (statementBase != null && statementBase.isOnDemand()) {
              usages.add(new RemoveOnDemandImportStatementsUsageInfo(statementBase));
            }
          }
        }
      }
    }
    return UsageViewUtil.removeDuplicatedUsages(usages.toArray(new UsageInfo[usages.size()]));
  }

  private boolean isUnderRefactoring(PsiDirectory packageDirectory) {
    for (PsiDirectory directory : myDirectories) {
      if (PsiTreeUtil.isAncestor(directory, packageDirectory, true)) {
        return true;
      }
    }
    return false;
  }


  @Override
  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    for (PsiClass psiClass : myClassesToMove.keySet()) {
      try {
        myClassesToMove.get(psiClass).checkMove(psiClass);
      }
      catch (IncorrectOperationException e) {
        conflicts.putValue(psiClass, e.getMessage());
      }
    }
    return showConflicts(conflicts);
  }

  @Override
  protected void refreshElements(PsiElement[] elements) {}

  @Override
  public void performRefactoring(UsageInfo[] usages) {
    final Map<PsiElement, PsiElement> oldToNewElementsMapping = new HashMap<PsiElement, PsiElement>();
    for (PsiClass psiClass : myClassesToMove.keySet()) {
      ChangeContextUtil.encodeContextInfo(psiClass, true);
      final RefactoringElementListener listener = getTransaction().getElementListener(psiClass);
      final PsiClass newClass = MoveClassesOrPackagesUtil.doMoveClass(psiClass, myClassesToMove.get(psiClass).findOrCreateTargetDirectory());
      oldToNewElementsMapping.put(psiClass, newClass);
      listener.elementMoved(newClass);
    }
    for (PsiClass psiClass : myClassesToMove.keySet()) {
      ChangeContextUtil.decodeContextInfo(psiClass, null, null);
    }
    myNonCodeUsages = MoveClassesOrPackagesProcessor.retargetUsages(usages, oldToNewElementsMapping);
    for (UsageInfo usage : usages) {
      if (usage instanceof RemoveOnDemandImportStatementsUsageInfo) {
        final PsiElement element = usage.getElement();
        if (element != null) {
          element.delete();
        }
      }
    }
    for (PsiDirectory directory : myDirectories) {
      directory.delete();
    }
  }

  @Override
  protected void performPsiSpoilingRefactoring() {
    RenameUtil.renameNonCodeUsages(myProject, myNonCodeUsages);
    if (myMoveCallback != null) {
      myMoveCallback.refactoringCompleted();
    }
  }

  private static void collectClasses(Map<PsiClass, TargetDirectoryWrapper> classesToMove,
                                     PsiDirectory directory,
                                     PsiDirectory rootDirectory,
                                     @NotNull TargetDirectoryWrapper targetDirectory) {
    final PsiElement[] children = directory.getChildren();
    final String relativePath = VfsUtil.getRelativePath(directory.getVirtualFile(), rootDirectory.getVirtualFile(), '/');

    final TargetDirectoryWrapper newTargetDirectory = relativePath.length() == 0
                                                      ? targetDirectory
                                                      : targetDirectory.findOrCreateChild(relativePath);
    for (PsiElement child : children) {
      if (child instanceof PsiJavaFile) {
        for (PsiClass aClass : ((PsiJavaFile)child).getClasses()) {
          classesToMove.put(aClass, newTargetDirectory);
        }
      }
      else if (child instanceof PsiDirectory){
        collectClasses(classesToMove, (PsiDirectory)child, directory, newTargetDirectory);
      }
    }
  }

  @Override
  protected String getCommandName() {
    return RefactoringBundle.message("moving.directories.command");
  }

  public TargetDirectoryWrapper getTargetDirectory(PsiDirectory dir) {
    return new TargetDirectoryWrapper(myTargetDirectory);
  }

  private static class RemoveOnDemandImportStatementsUsageInfo extends UsageInfo {
    public RemoveOnDemandImportStatementsUsageInfo(PsiImportStatementBase statementBase) {
      super(statementBase);
    }
  }
  
  public static class TargetDirectoryWrapper {
    private TargetDirectoryWrapper myParentDirectory;
    private PsiDirectory myTargetDirectory;
    private String myRelativePath;

    public TargetDirectoryWrapper(PsiDirectory targetDirectory) {
      myTargetDirectory = targetDirectory;
    }

    public TargetDirectoryWrapper(TargetDirectoryWrapper parentDirectory, String relativePath) {
      myParentDirectory = parentDirectory;
      myRelativePath = relativePath;
    }

    public TargetDirectoryWrapper(PsiDirectory parentDirectory, String relativePath) {
      myTargetDirectory = parentDirectory.findSubdirectory(relativePath);
      //in case it was null
      myParentDirectory = new TargetDirectoryWrapper(parentDirectory);
      myRelativePath = relativePath;
    }

    public PsiDirectory findOrCreateTargetDirectory() {
      if (myTargetDirectory == null) {
        final PsiDirectory root = myParentDirectory.findOrCreateTargetDirectory();

        myTargetDirectory = root.findSubdirectory(myRelativePath);
        if (myTargetDirectory == null) {
          myTargetDirectory = root.createSubdirectory(myRelativePath);
        }
      }
      return myTargetDirectory;
    }

    @Nullable
    public PsiDirectory getTargetDirectory() {
      return myTargetDirectory;
    }

    public TargetDirectoryWrapper findOrCreateChild(String relativePath) {
      if (myTargetDirectory != null) {
        final PsiDirectory psiDirectory = myTargetDirectory.findSubdirectory(relativePath);
        if (psiDirectory != null) {
          return new TargetDirectoryWrapper(psiDirectory);
        }
      }
      return new TargetDirectoryWrapper(this, relativePath);
    }

    public void checkMove(PsiClass psiClass) throws IncorrectOperationException {
      if (myTargetDirectory != null) {
        psiClass.getManager().checkMove(psiClass, myTargetDirectory);
      }
    }
  }
}
