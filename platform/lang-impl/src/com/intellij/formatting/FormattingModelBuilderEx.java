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
package com.intellij.formatting;

import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 8/22/12 2:23 PM
 */
public interface FormattingModelBuilderEx extends FormattingModelBuilder {
  
  /**
   * Requests building the formatting model for a section of the file containing
   * the specified PSI element and its children.
   *
   * @param element  the top element for which formatting is requested.
   * @param settings the code style settings used for formatting.
   * @param mode     formatting mode
   * @return the formatting model for the file.
   */
  @NotNull
  FormattingModel createModel(@NotNull final PsiElement element, @NotNull final CodeStyleSettings settings, @NotNull FormattingMode mode);
}
