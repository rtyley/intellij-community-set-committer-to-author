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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RemoveUnusedParameterFix extends IntentionAndQuickFixAction {
  private final PsiParameter myParameter;

  public RemoveUnusedParameterFix(PsiParameter parameter) {
    myParameter = parameter;
  }

  @NotNull
  public String getName() {
    return QuickFixBundle.message("remove.unused.parameter.text", myParameter.getName());
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("remove.unused.parameter.family");
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return
      myParameter.isValid()
      && myParameter.getDeclarationScope() instanceof PsiMethod
      && myParameter.getManager().isInProject(myParameter);
  }

  public void applyFix(final Project project, final PsiFile file, @Nullable final Editor editor) {
    if (!CodeInsightUtilBase.prepareFileForWrite(myParameter.getContainingFile())) return;
    removeReferences(myParameter);
  }

  private static void removeReferences(PsiParameter parameter) {
    PsiMethod method = (PsiMethod) parameter.getDeclarationScope();
    ChangeSignatureProcessor processor = new ChangeSignatureProcessor(parameter.getProject(),
                                                                      method,
        false, null,
        method.getName(),
        method.getReturnType(),
        getNewParametersInfo(method, parameter));

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      processor.run();
    }
    else {
      processor.run();
    }
  }

  public static ParameterInfoImpl[] getNewParametersInfo(PsiMethod method, PsiParameter parameterToRemove) {
    List<ParameterInfoImpl> result = new ArrayList<ParameterInfoImpl>();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      if (!Comparing.equal(parameter, parameterToRemove)) {
        result.add(new ParameterInfoImpl(i, parameter.getName(), parameter.getType()));
      }
    }
    return result.toArray(new ParameterInfoImpl[result.size()]);
  }

  public boolean startInWriteAction() {
    return true;
  }


}
