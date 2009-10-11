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

package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;

public interface ElementFilter{
  /**
   * Checks if element in given context is mathed by given filter.
   * @param element most often PsiElement
   * @param context context of the element (if any)
   * @return true if matched
   */
  boolean isAcceptable(Object element, PsiElement context);

  /**
   * Quick check if the filter acceptable for elements of given class at all.
   * @param hintClass class for which we are looking for metadata
   * @return true if class matched
   */
  boolean isClassAcceptable(Class hintClass);

   // To be used only for debug purposes!
  @NonNls String toString();
}
