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
package com.siyeh.ipp.modifiers;

import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.siyeh.ipp.base.PsiElementPredicate;

/**
 * @author Bas Leijdekkers
 */
class MakePublicPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiClass || parent instanceof PsiField || parent instanceof PsiMethod)) {
      return false;
    }
    if (element instanceof PsiDocComment || element instanceof PsiCodeBlock) {
      return false;
    }
    if (parent instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)parent;
      final PsiElement brace = aClass.getLBrace();
      if (brace != null && brace.getTextOffset() < element.getTextOffset()) {
        return false;
      }
    }
    final PsiModifierListOwner owner = (PsiModifierListOwner)parent;
    final PsiModifierList modifierList = owner.getModifierList();
    return !(modifierList == null || modifierList.hasModifierProperty(PsiModifier.PUBLIC));
  }
}
