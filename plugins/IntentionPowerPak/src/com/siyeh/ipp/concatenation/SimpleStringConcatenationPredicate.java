/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.concatenation;

import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiPolyadicExpression;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ConcatenationUtils;
import com.siyeh.ipp.psiutils.ErrorUtil;

class SimpleStringConcatenationPredicate
  implements PsiElementPredicate {

  private final boolean excludeConcatenationsInsideAnnotations;

  public SimpleStringConcatenationPredicate(boolean excludeConcatenationsInsideAnnotations) {
    this.excludeConcatenationsInsideAnnotations = excludeConcatenationsInsideAnnotations;
  }

  public boolean satisfiedBy(PsiElement element) {
    if (!ConcatenationUtils.isConcatenation(element)) {
      return false;
    }
    if (excludeConcatenationsInsideAnnotations && isInsideAnnotation(element)) {
      return false;
    }
    return !ErrorUtil.containsError(element);
  }

  private static boolean isInsideAnnotation(PsiElement element) {
    if (!(element instanceof PsiPolyadicExpression)) {
      return false;
    }
    final PsiElement parent = element.getParent();
    return parent instanceof PsiNameValuePair || parent instanceof PsiArrayInitializerMemberValue;
  }
}
