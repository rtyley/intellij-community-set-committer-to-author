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
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class WrapExpressionFix implements IntentionAction {

  private final PsiExpression myExpression;
  private final PsiClassType myExpectedType;

  public WrapExpressionFix(PsiClassType expectedType, PsiExpression expression) {
    myExpression = expression;
    myExpectedType = expectedType;
  }

  @NotNull
  public String getText() {
    final PsiMethod wrapper = myExpression.isValid() ? findWrapper(myExpression.getType(), myExpectedType) : null;
    final String methodPresentation = wrapper != null ? (wrapper.getContainingClass().getName() + "." + wrapper.getName()) : "";
    return QuickFixBundle.message("wrap.expression.using.static.accessor.text", methodPresentation);
  }

  @Nullable
  private static PsiMethod findWrapper(PsiType type, PsiClassType expectedType) {
    PsiClass aClass = expectedType.resolve();
    if (aClass != null) {
      PsiMethod[] methods = aClass.getMethods();
      for (PsiMethod method : methods) {
        if (method.hasModifierProperty(PsiModifier.STATIC) && method.getParameterList().getParametersCount() == 1 &&
            method.getParameterList().getParameters()[0].getType().equals(type) &&
            method.getReturnType() != null &&
            expectedType.equals(method.getReturnType())) {
          return method;
        }
      }
    }

    return null;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("wrap.expression.using.static.accessor.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myExpression.isValid()
           && myExpression.getManager().isInProject(myExpression)
           && myExpectedType.isValid()
           && myExpression.getType() != null
           && findWrapper(myExpression.getType(), myExpectedType) != null;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    PsiMethod wrapper = findWrapper(myExpression.getType(), myExpectedType);
    assert wrapper != null;
    PsiElementFactory factory = JavaPsiFacade.getInstance(file.getProject()).getElementFactory();
    @NonNls String methodCallText = "Foo." + wrapper.getName() + "()";
    PsiMethodCallExpression call = (PsiMethodCallExpression) factory.createExpressionFromText(methodCallText,
                                                                                              null);
    call.getArgumentList().add(myExpression);
    ((PsiReferenceExpression) call.getMethodExpression().getQualifierExpression()).bindToElement(
      wrapper.getContainingClass());
    myExpression.replace(call);
  }

  public boolean startInWriteAction() {
    return true;
  }

  public static void registerWrapAction (JavaResolveResult[] candidates, PsiExpression[] expressions, HighlightInfo highlightInfo) {
    PsiClassType expectedType = null;
    PsiExpression expr = null;

    nextMethod:
    for (int i = 0; i < candidates.length && expectedType == null; i++) {
      JavaResolveResult candidate = candidates[i];
      PsiSubstitutor substitutor = candidate.getSubstitutor();
      final PsiElement element = candidate.getElement();
      assert element != null;
      PsiParameter[] parameters = ((PsiMethod)element).getParameterList().getParameters();
      if (parameters.length != expressions.length) continue;
      for (int j = 0; j < expressions.length; j++) {
        PsiExpression expression = expressions[j];
        if (expression.getType() != null) {
          PsiType paramType = parameters[j].getType();
          paramType = substitutor != null ? substitutor.substitute(paramType) : paramType;
          if (paramType.isAssignableFrom(expression.getType())) continue;
          if (paramType instanceof PsiClassType) {
            if (expectedType == null && findWrapper(expression.getType(), (PsiClassType) paramType) != null) {
              expectedType = (PsiClassType) paramType;
              expr = expression;
            } else {
              expectedType = null;
              expr = null;
              continue nextMethod;
            }
          }
        }
      }
    }

    if (expectedType != null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, expr.getTextRange(), new WrapExpressionFix(expectedType, expr), null);
    }
  }

}
