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

/**
 * created at Nov 26, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveHandler implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("move.title");

  /**
   * called by an Action in AtomicAction when refactoring is invoked from Editor
   */
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);
    while(true){
      if (element == null) {
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("the.caret.should.be.positioned.at.the.class.method.or.field.to.be.refactored"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, null);
        return;
      }

      if (tryToMoveElement(element, project, dataContext, null, editor)) {
        return;
      }
      final TextRange range = element.getTextRange();
      if (range != null) {
        int relative = offset - range.getStartOffset();
        final PsiReference reference = element.findReferenceAt(relative);
        if (reference != null) {
          final PsiElement refElement = reference.resolve();
          if (refElement != null && tryToMoveElement(refElement, project, dataContext, reference, editor)) return;
        }
      }

      element = element.getParent();
    }
  }

  private static boolean tryToMoveElement(final PsiElement element, final Project project, final DataContext dataContext,
                                          final PsiReference reference, final Editor editor) {
    for(MoveHandlerDelegate delegate: Extensions.getExtensions(MoveHandlerDelegate.EP_NAME)) {
      if (delegate.tryToMove(element, project, dataContext, reference, editor)) {
        return true;
      }
    }

    return false;
  }

  /**
   * called by an Action in AtomicAction
   */
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    doMove(project, elements, dataContext == null ? null : (PsiElement)dataContext.getData(DataConstantsEx.TARGET_PSI_ELEMENT), null);
  }

  /**
   * must be invoked in AtomicAction
   */
  public static void doMove(Project project, @NotNull PsiElement[] elements, PsiElement targetContainer, MoveCallback callback) {
    if (elements.length == 0) return;

    for(MoveHandlerDelegate delegate: Extensions.getExtensions(MoveHandlerDelegate.EP_NAME)) {
      if (delegate.canMove(elements, targetContainer)) {
        delegate.doMove(project, elements, targetContainer, callback);
        break;
      }
    }
  }

  /**
   * Performs some extra checks (that canMove does not)
   * May replace some elements with others which actulaly shall be moved (e.g. directory->package)
   */
  @Nullable
  public static PsiElement[] adjustForMove(Project project, final PsiElement[] sourceElements, final PsiElement targetElement) {
    for(MoveHandlerDelegate delegate: Extensions.getExtensions(MoveHandlerDelegate.EP_NAME)) {
      if (delegate.canMove(sourceElements, targetElement)) {
        return delegate.adjustForMove(project, sourceElements, targetElement);
      }
    }
    return sourceElements;
  }

  /**
   * Must be invoked in AtomicAction
   * target container can be null => means that container is not determined yet and must be spacify by the user
   */
  public static boolean canMove(@NotNull PsiElement[] elements, PsiElement targetContainer) {
    for(MoveHandlerDelegate delegate: Extensions.getExtensions(MoveHandlerDelegate.EP_NAME)) {
      if (delegate.canMove(elements, targetContainer)) return true;
    }

    return false;
  }

  public static boolean isValidTarget(final PsiElement psiElement) {
    if (psiElement != null) {
      for(MoveHandlerDelegate delegate: Extensions.getExtensions(MoveHandlerDelegate.EP_NAME)) {
        if (delegate.isValidTarget(psiElement)) return true;
      }
    }

    return false;
  }
}
