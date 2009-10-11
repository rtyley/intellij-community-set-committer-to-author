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
 * Date: 07-May-2008
 */
package com.intellij.refactoring.replaceConstructorWithBuilder;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceConstructorWithBuilderHandler implements RefactoringActionHandler {
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(offset);
    final PsiClass psiClass = getParentNamedClass(element);
    if (psiClass == null) {
      showErrorMessage("The caret should be positioned inside a class which constructors are to be replaced with builder.", project, editor);
      return;
    }

    final PsiMethod[] constructors = psiClass.getConstructors();
    if (constructors.length == 0) {
      showErrorMessage("Current class doesn't have constructors to replace with builder.", project, editor);
      return;
    }

    new ReplaceConstructorWithBuilderDialog(project, constructors).show();
  }

  @Nullable
  private static PsiClass getParentNamedClass(PsiElement element) {
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (psiClass instanceof PsiAnonymousClass) {
      return getParentNamedClass(psiClass);
    }
    return psiClass;
  }

  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, final DataContext dataContext) {
    throw new UnsupportedOperationException();
  }

  private static void showErrorMessage(String message, Project project, Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor, message, ReplaceConstructorWithBuilderProcessor.REFACTORING_NAME,  HelpID.REPLACE_CONSTRUCTOR_WITH_BUILDER);
  }
}
