/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.rename.inplace;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 11/9/11
 */
public class MemberInplaceRenameHandler extends VariableInplaceRenameHandler {
  @Override
  protected boolean isAvailable(PsiElement element, Editor editor, PsiFile file) {
    final PsiElement nameSuggestionContext = file.findElementAt(editor.getCaretModel().getOffset());
    if (element == null && LookupManager.getActiveLookup(editor) != null) {
      element = PsiTreeUtil.getParentOfType(nameSuggestionContext, PsiNamedElement.class);
    }
    final RefactoringSupportProvider
      supportProvider = element != null ? LanguageRefactoringSupport.INSTANCE.forLanguage(element.getLanguage()) : null;
    return editor.getSettings().isVariableInplaceRenameEnabled()
           && supportProvider != null
           && supportProvider.isMemberInplaceRenameAvailable(element, nameSuggestionContext);
  }

  @Override
  public VariableInplaceRenamer doRename(@NotNull final PsiElement elementToRename, final Editor editor, final DataContext dataContext) {
    if (elementToRename instanceof PsiNameIdentifierOwner) {
      RenamePsiElementProcessor.forElement(elementToRename).substituteElementToRename(elementToRename, editor, new Pass<PsiElement>() {
        @Override
        public void pass(PsiElement element) {
          final MemberInplaceRenamer renamer = new MemberInplaceRenamer((PsiNameIdentifierOwner)elementToRename, element, editor);
          boolean startedRename = renamer.performInplaceRename();
          if (!startedRename) {
            performDialogRename(elementToRename, editor, dataContext);
          }
        }
      });
    } else {
      performDialogRename(elementToRename, editor, dataContext);
    }
    return null;
  }
}
