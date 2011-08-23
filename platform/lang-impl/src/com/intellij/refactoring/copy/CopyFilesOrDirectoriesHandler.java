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

package com.intellij.refactoring.copy;

import com.intellij.CommonBundle;
import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashSet;

public class CopyFilesOrDirectoriesHandler implements CopyHandlerDelegate {
  public boolean canCopy(final PsiElement[] elements) {
    HashSet<String> names = new HashSet<String>();
    for (PsiElement element : elements) {
      if (!(element instanceof PsiFileSystemItem)) return false;
      if (!element.isValid()) return false;

      String name = ((PsiFileSystemItem) element).getName();
      if (names.contains(name)) {
        return false;
      }
      names.add(name);

      if (element instanceof PsiFile && !canCopyFile((PsiFile) element)) {
        return false;
      }
      if (element instanceof PsiDirectory && !canCopyDirectory((PsiDirectory)element)) {
        return false;
      }
    }

    PsiElement[] filteredElements = PsiTreeUtil.filterAncestors(elements);
    return filteredElements.length == elements.length;
  }

  protected boolean canCopyFile(PsiFile element) {
    return true;
  }

  protected boolean canCopyDirectory(PsiDirectory element) {
    return true;
  }

  public void doCopy(final PsiElement[] elements, PsiDirectory defaultTargetDirectory) {
    if (defaultTargetDirectory == null) {
      defaultTargetDirectory = getCommonParentDirectory(elements);
    }

    Project project = defaultTargetDirectory != null ? defaultTargetDirectory.getProject() : elements [0].getProject();
    CopyFilesOrDirectoriesDialog dialog = new CopyFilesOrDirectoriesDialog(elements, defaultTargetDirectory, project, false);
    dialog.show();
    if (dialog.isOK()) {
      final String newName = elements.length == 1 ? dialog.getNewName() : null;
      final PsiDirectory targetDirectory = dialog.getTargetDirectory();
      try {
        for (PsiElement element : elements) {
          PsiFileSystemItem psiElement = (PsiFileSystemItem)element;
          if (psiElement.isDirectory()) {
            MoveFilesOrDirectoriesUtil.checkIfMoveIntoSelf(psiElement, targetDirectory);
          }
        }
      }
      catch (IncorrectOperationException e) {
        CommonRefactoringUtil.showErrorHint(project, null, e.getMessage(), CommonBundle.getErrorTitle(), null);
        return;
      }
      copyImpl(elements, newName, targetDirectory, false);
    }
  }

  public void doClone(final PsiElement element) {
    PsiDirectory targetDirectory;
    if (element instanceof PsiDirectory) {
      targetDirectory = ((PsiDirectory)element).getParentDirectory();
    }
    else  {
      targetDirectory = ((PsiFile)element).getContainingDirectory();
    }

    PsiElement[] elements = {element};
    CopyFilesOrDirectoriesDialog dialog = new CopyFilesOrDirectoriesDialog(elements, null, element.getProject(), true);
    dialog.show();
    if (dialog.isOK()) {
      String newName = dialog.getNewName();
      copyImpl(elements, newName, targetDirectory, true);
    }
  }

  @Nullable
  private static PsiDirectory getCommonParentDirectory(PsiElement[] elements){
    PsiDirectory result = null;

    for (PsiElement element : elements) {
      PsiDirectory directory;

      if (element instanceof PsiDirectory) {
        directory = (PsiDirectory)element;
        directory = directory.getParentDirectory();
      }
      else if (element instanceof PsiFile) {
        directory = ((PsiFile)element).getContainingDirectory();
      }
      else {
        throw new IllegalArgumentException("unexpected element " + element);
      }

      if (directory == null) continue;

      if (result == null) {
        result = directory;
      }
      else {
        if (PsiTreeUtil.isAncestor(directory, result, true)) {
          result = directory;
        }
      }
    }

    return result;
  }

  /**
   *
   * @param elements
   * @param newName can be not null only if elements.length == 1
   * @param targetDirectory
   */
  private static void copyImpl(final PsiElement[] elements, final String newName, final PsiDirectory targetDirectory, final boolean doClone) {
    if (doClone && elements.length != 1) {
      throw new IllegalArgumentException("invalid number of elements to clone:" + elements.length);
    }

    if (newName != null && elements.length != 1) {
      throw new IllegalArgumentException("no new name should be set; number of elements is: " + elements.length);
    }

    final Project project = targetDirectory.getProject();
    Runnable command = new Runnable() {
      public void run() {
        final Runnable action = new Runnable() {
          public void run() {
            try {
              PsiFile firstFile = null;
              final int[] choice = elements.length > 1 ? new int[]{-1} : null;
              for (PsiElement element : elements) {
                PsiFile f = copyToDirectory((PsiFileSystemItem)element, newName, targetDirectory, choice);
                if (firstFile == null) {
                  firstFile = f;
                }
              }

              if (firstFile != null) {
                CopyHandler.updateSelectionInActiveProjectView(firstFile, project, doClone);
                if (!(firstFile instanceof PsiBinaryFile)){
                  EditorHelper.openInEditor(firstFile);
                  ApplicationManager.getApplication().invokeLater(new Runnable() {
                                  public void run() {
                                    ToolWindowManager.getInstance(project).activateEditorComponent();
                                  }
                                });
                }
              }
            }
            catch (final IncorrectOperationException ex) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  Messages.showMessageDialog(project, ex.getMessage(), RefactoringBundle.message("error.title"), Messages.getErrorIcon());
                }
              });
            }
            catch (final IOException ex) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  Messages.showMessageDialog(project, ex.getMessage(), RefactoringBundle.message("error.title"), Messages.getErrorIcon());
                }
              });
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    };
    CommandProcessor.getInstance().executeCommand(project, command, doClone ?
                                                                    RefactoringBundle.message("copy,handler.clone.files.directories") :
                                                                    RefactoringBundle.message("copy.handler.copy.files.directories"), null);
  }

  /**
   * @param elementToCopy PsiFile or PsiDirectory
   * @param newName can be not null only if elements.length == 1
   * @return first copied PsiFile (recursivly); null if no PsiFiles copied
   */
  @Nullable
  public static PsiFile copyToDirectory(@NotNull PsiFileSystemItem elementToCopy,
                                        @Nullable String newName,
                                        @NotNull PsiDirectory targetDirectory)
    throws IncorrectOperationException, IOException {
    return copyToDirectory(elementToCopy, newName, targetDirectory, null);
  }

  /**
   *
   * @param elementToCopy PsiFile or PsiDirectory
   * @param newName can be not null only if elements.length == 1
   * @param choice
   * @return first copied PsiFile (recursivly); null if no PsiFiles copied
   */
  @Nullable
  public static PsiFile copyToDirectory(@NotNull PsiFileSystemItem elementToCopy,
                                        @Nullable String newName,
                                        @NotNull PsiDirectory targetDirectory, int[] choice)
    throws IncorrectOperationException, IOException {
    if (elementToCopy instanceof PsiFile) {
      PsiFile file = (PsiFile)elementToCopy;
      String name = newName == null ? file.getName() : newName;
      if (checkFileExist(targetDirectory, choice, file, name)) return null;
      return targetDirectory.copyFileFrom(name, file);
    }
    else if (elementToCopy instanceof PsiDirectory) {
      PsiDirectory directory = (PsiDirectory)elementToCopy;
      if (directory.equals(targetDirectory)) {
        return null;
      }
      if (newName == null) newName = directory.getName();
      final PsiDirectory existing = targetDirectory.findSubdirectory(newName);
      final PsiDirectory subdirectory = existing == null ? targetDirectory.createSubdirectory(newName) : existing;
      EncodingManager.doActionAndRestoreEncoding(directory.getVirtualFile(), new ThrowableComputable<VirtualFile, IOException>() {
        public VirtualFile compute() {
          return subdirectory.getVirtualFile();
        }
      });

      PsiFile firstFile = null;
      PsiElement[] children = directory.getChildren();
      for (PsiElement child : children) {
        PsiFileSystemItem item = (PsiFileSystemItem)child;
        PsiFile f = copyToDirectory(item, item.getName(), subdirectory, choice);
        if (firstFile == null) {
          firstFile = f;
        }
      }
      return firstFile;
    }
    else {
      throw new IllegalArgumentException("unexpected elementToCopy: " + elementToCopy);
    }
  }

  public static boolean checkFileExist(PsiDirectory targetDirectory, int[] choice, PsiFile file, String name) {
    final PsiFile existing = targetDirectory.findFile(name);
    if (existing!=null) {
      int selection = choice == null || choice[0] == -1 ? Messages.showDialog(
        String.format("File '%s' already exists in directory '%s'", name, targetDirectory.getVirtualFile().getPath()),
        "Copy",
        choice == null ? new String[]{"Overwrite", "Skip"}
                       : new String[]{"Overwrite", "Skip", "Overwrite for all", "Skip for all"}, 0, Messages.getQuestionIcon())
                                           : choice[0];
      if (choice != null && selection > 1) {
        choice[0] = selection % 2;
        selection = choice[0];
      }
      if (selection == 0 && file != existing) {
        existing.delete();
      } else return true;
    }
    return false;
  }
}
