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
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.reference.UnusedDeclarationFixProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 *         Date: 1/19/12
 */
public class RegisterInspectionFixProvider implements UnusedDeclarationFixProvider {

  @NotNull
  @Override
  public IntentionAction[] getQuickFixes(PsiElement element) {
    if (!(element instanceof PsiIdentifier)) return IntentionAction.EMPTY_ARRAY;
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiClass)) return IntentionAction.EMPTY_ARRAY;
    if (InheritanceUtil.isInheritor((PsiClass)parent, LocalInspectionTool.class.getName())) {
      return new IntentionAction[] { new RegisterInspectionFix((PsiClass)parent, LocalInspectionEP.LOCAL_INSPECTION) };
    }
    if (InheritanceUtil.isInheritor((PsiClass)parent, GlobalInspectionTool.class.getName())) {
      return new IntentionAction[] { new RegisterInspectionFix((PsiClass)parent, InspectionEP.GLOBAL_INSPECTION) };
    }
    return IntentionAction.EMPTY_ARRAY;
  }
}
