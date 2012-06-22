/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntheticElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Danila Ponomarenko
 */
public abstract class BaseRefactoringAction extends PsiElementBaseIntentionAction implements RefactoringAction{
  @Override
  public final boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return !(element instanceof SyntheticElement) && isAvailableOverride(project, editor, element);
  }

  protected abstract boolean isAvailableOverride(@NotNull Project project, Editor editor, @NotNull PsiElement element);

  @Override
  public final boolean startInWriteAction() {
    return false;
  }

  @Override
  public final Icon getIcon(int flags) {
    return REFACTORING_BULB;
  }
}