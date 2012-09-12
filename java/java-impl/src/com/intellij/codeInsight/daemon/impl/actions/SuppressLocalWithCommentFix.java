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
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 */
public class SuppressLocalWithCommentFix extends SuppressByJavaCommentFix {
  public SuppressLocalWithCommentFix(HighlightDisplayKey key) {
    super(key);
  }

  @Nullable
  @Override
  protected PsiElement getContainer(PsiElement context) {
    final PsiElement container = super.getContainer(context);
    if (container != null) {
      final PsiElement elementToAnnotate = getElementToAnnotate(context, container);
      if (elementToAnnotate == null) return null;
    }
    return container;
  }

  @Override
  protected void createSuppression(Project project, Editor editor, PsiElement element, PsiElement container)
    throws IncorrectOperationException {
    suppressWithComment(project, editor, element, container);
  }

  @NotNull
  @Override
  public String getText() {
    return "Suppress for statement with comment";
  }
}
