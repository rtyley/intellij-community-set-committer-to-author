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

package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class MoveFilesOrDirectoriesUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil");

  private MoveFilesOrDirectoriesUtil() {
  }

  // Does not process non-code usages!
  public static void doMoveDirectory(final PsiDirectory aDirectory, final PsiDirectory destDirectory) throws IncorrectOperationException {
    PsiManager manager = aDirectory.getManager();
    // do actual move
    manager.moveDirectory(aDirectory, destDirectory);
  }

  // Does not process non-code usages!
  public static void doMoveFile(final PsiFile file, final PsiDirectory newDirectory) throws IncorrectOperationException {
    PsiManager manager = file.getManager();
    // the class is already there, this is true when multiple classes are defined in the same file
    if (!newDirectory.equals(file.getContainingDirectory())) {
      // do actual move
      manager.moveFile(file, newDirectory);
    }
  }

  /**
   * @param elements should contain PsiDirectories or PsiFiles only
   */
  public static void doMove(final Project project,
                            final PsiElement[] elements,
                            final PsiElement[] targetElement,
                            final MoveCallback moveCallback) {
    doMove(project, elements, targetElement, moveCallback, null);
  }

  /**
   * @param elements should contain PsiDirectories or PsiFiles only if adjustElements == null
   */
  public static void doMove(final Project project,
                            final PsiElement[] elements,
                            final PsiElement[] targetElement,
                            final MoveCallback moveCallback,
                            final Function<PsiElement[], PsiElement[]> adjustElements) {
    if (adjustElements == null) {
      for (PsiElement element : elements) {
        if (!(element instanceof PsiFile) && !(element instanceof PsiDirectory)) {
          throw new IllegalArgumentException("unexpected element type: " + element);
        }
      }
    }

    final PsiDirectory targetDirectory = resolveToDirectory(project, targetElement[0]);
    if (targetElement[0] != null && targetDirectory == null) return;

    final PsiDirectory initialTargetDirectory = getInitialTargetDirectory(targetDirectory, elements);

    final MoveFilesOrDirectoriesDialog.Callback doRun = new MoveFilesOrDirectoriesDialog.Callback() {
      public void run(final MoveFilesOrDirectoriesDialog moveDialog) {
        final PsiDirectory targetDirectory = moveDialog != null ? moveDialog.getTargetDirectory() : initialTargetDirectory;

        LOG.assertTrue(targetDirectory != null);
        PsiElement[] newElements = adjustElements != null ? adjustElements.fun(elements) : elements;
        targetElement[0] = targetDirectory;

        PsiManager manager = PsiManager.getInstance(project);
        try {
          for (PsiElement psiElement : newElements) {
            manager.checkMove(psiElement, targetDirectory);
          }

          new MoveFilesOrDirectoriesProcessor(project, newElements, targetDirectory,
                                              RefactoringSettings.getInstance().MOVE_SEARCH_FOR_REFERENCES_FOR_FILE,
                                              false, false, moveCallback, new Runnable() {
            public void run() {
              if (moveDialog != null) moveDialog.close(DialogWrapper.CANCEL_EXIT_CODE);
            }
          }).run();
        }
        catch (IncorrectOperationException e) {
          CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), e.getMessage(),
                                                 "refactoring.moveFile", project);
        }
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      doRun.run(null);
    }
    else {
      final MoveFilesOrDirectoriesDialog moveDialog = new MoveFilesOrDirectoriesDialog(project, doRun);
      moveDialog.setData(elements, initialTargetDirectory, "refactoring.moveFile");
      moveDialog.show();
    }
  }

  @Nullable
  private static PsiDirectory resolveToDirectory(final Project project, final PsiElement element) {
    if (!(element instanceof PsiDirectoryContainer)) {                                                                 
      return (PsiDirectory)element;
    }

    PsiDirectory[] directories = ((PsiDirectoryContainer)element).getDirectories();
    switch (directories.length) {
      case 0:
        return null;
      case 1:
        return directories[0];
      default:
        return DirectoryChooserUtil.chooseDirectory(directories, directories[0], project, new HashMap<PsiDirectory, String>());
    }

  }

  @Nullable
  private static PsiDirectory getCommonDirectory(PsiElement[] movedElements) {
    PsiDirectory commonDirectory = null;

    for (PsiElement movedElement : movedElements) {
      final PsiDirectory containingDirectory;
      if (movedElement instanceof PsiDirectory) {
        containingDirectory = ((PsiDirectory)movedElement).getParentDirectory();
      }
      else {
        final PsiFile containingFile = movedElement.getContainingFile();
        containingDirectory = containingFile == null ? null : containingFile.getContainingDirectory();
      }

      if (containingDirectory != null) {
        if (commonDirectory == null) {
          commonDirectory = containingDirectory;
        }
        else {
          if (commonDirectory != containingDirectory) {
            return null;
          }
        }
      }
    }
    return commonDirectory;
  }

  @Nullable
  private static PsiDirectory getInitialTargetDirectory(PsiDirectory initialTargetElement, final PsiElement[] movedElements) {
    PsiDirectory initialTargetDirectory = initialTargetElement;
    if (initialTargetDirectory == null) {
      if (movedElements != null) {
        final PsiDirectory commonDirectory = getCommonDirectory(movedElements);
        if (commonDirectory != null) {
          initialTargetDirectory = commonDirectory;
        }
        else {
          initialTargetDirectory = getContainerDirectory(movedElements[0]);
        }
      }
    }
    return initialTargetDirectory;
  }

  @Nullable
  private static PsiDirectory getContainerDirectory(final PsiElement psiElement) {
    if (psiElement instanceof PsiDirectory) {
      return (PsiDirectory)psiElement;
    }
    else if (psiElement != null) {
      return psiElement.getContainingFile().getContainingDirectory();
    }
    else {
      return null;
    }
  }

  public static void checkIfMoveIntoSelf(PsiElement element, PsiElement newContainer) throws IncorrectOperationException {
    PsiElement container = newContainer;
    while (container != null) {
      if (container == element) {
        if (element instanceof PsiDirectory) {
          if (element == newContainer) {
            throw new IncorrectOperationException("Cannot place directory into itself.");
          }
          else {
            throw new IncorrectOperationException("Cannot place directory into its subdirectory.");
          }
        }
        else {
          throw new IncorrectOperationException();
        }
      }
      container = container.getParent();
    }
  }
}
