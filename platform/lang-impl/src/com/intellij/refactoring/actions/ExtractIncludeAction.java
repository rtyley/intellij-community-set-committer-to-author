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

package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.lang.TitledHandler;
import com.intellij.refactoring.lang.LanguageExtractInclude;

/**
 * @author ven
 */
public class ExtractIncludeAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return true;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return false;
  }

  protected boolean isAvailableForLanguage(Language language) {
    return true;
  }

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    final RefactoringActionHandler handler = getHandler(e.getDataContext());
    if (handler instanceof TitledHandler) {
      e.getPresentation().setText(((TitledHandler) handler).getActionTitle());
    }
    else {
      e.getPresentation().setText("Extract Include File...");
    }
  }

  protected boolean isAvailableForFile(PsiFile file) {
    final Language baseLanguage = file.getViewProvider().getBaseLanguage();
    return LanguageExtractInclude.INSTANCE.forLanguage(baseLanguage) != null;
  }

  public RefactoringActionHandler getHandler(DataContext dataContext) {
    PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
    if (file == null) return null;
    return LanguageExtractInclude.INSTANCE.forLanguage(file.getViewProvider().getBaseLanguage());
  }
}
