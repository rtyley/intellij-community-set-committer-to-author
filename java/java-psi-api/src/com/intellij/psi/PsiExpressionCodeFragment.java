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
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a fragment of Java code the contents of which is an expression.
 *
 * @see PsiElementFactory#createExpressionCodeFragment(String, PsiElement, PsiType, boolean)
 */
public interface PsiExpressionCodeFragment extends JavaCodeFragment {
  /**
   * Returns the expression contained in the fragment.
   *
   * @return the expression contained in the fragment.
   */
  PsiExpression getExpression();

  /**
   * Returns the expected type of the expression (not used by the expression itself,
   * but can be used by its clients).
   *
   * @return the expected type of the expression.
   */
  @Nullable PsiType getExpectedType();

  /**
   * Sets the expected type of the expression (not used by the expression itself,
   * but can be used by its clients).
   *
   * @param type the expected type of the expression. 
   */
  void setExpectedType(PsiType type);
}
