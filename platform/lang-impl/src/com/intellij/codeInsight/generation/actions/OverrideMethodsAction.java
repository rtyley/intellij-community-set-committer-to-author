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

package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.OverrideMethodsHandler;
import com.intellij.lang.CodeInsightActions;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

public class OverrideMethodsAction extends BaseCodeInsightAction {

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new OverrideMethodsHandler();
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull final PsiFile file) {
    Language language = PsiUtilBase.getLanguageAtOffset(file, editor.getCaretModel().getOffset());
    final LanguageCodeInsightActionHandler codeInsightActionHandler = CodeInsightActions.OVERRIDE_METHOD.forLanguage(language);
    if (codeInsightActionHandler != null) {
      return codeInsightActionHandler.isValidFor(editor, file);
    }
    return false;
  }

  @Override
  public void update(final AnActionEvent event) {
    if (CodeInsightActions.OVERRIDE_METHOD.hasAnyExtensions()) {
      event.getPresentation().setVisible(true);
      super.update(event);
    }
    else {
      event.getPresentation().setVisible(false);
    }
  }
}