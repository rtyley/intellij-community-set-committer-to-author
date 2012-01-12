/*
 * Copyright 2008 Bas Leijdekkers
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
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ChangeModifierFix extends InspectionGadgetsFix {

  @PsiModifier.ModifierConstant private final String modifierText;

  public ChangeModifierFix(@NonNls @PsiModifier.ModifierConstant String modifierText) {
    this.modifierText = modifierText;
  }

  @NotNull
  public String getName() {
    return InspectionGadgetsBundle.message("change.modifier.quickfix",
                                           modifierText);
  }

  public void doFix(Project project, ProblemDescriptor descriptor)
    throws IncorrectOperationException {
    final PsiElement element = descriptor.getPsiElement();
    final PsiModifierListOwner modifierListOwner =
      PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class);
    if (modifierListOwner == null) {
      return;
    }
    final PsiModifierList modifiers = modifierListOwner.getModifierList();
    if (modifiers == null) {
      return;
    }
    modifiers.setModifierProperty(modifierText, true);
  }
}