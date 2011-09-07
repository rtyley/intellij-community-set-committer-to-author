
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
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.util.IncorrectOperationException;

class JavaWithNotSurrounder extends JavaExpressionSurrounder{
  public boolean isApplicable(PsiExpression expr) {
    return PsiType.BOOLEAN.equals(expr.getType());
  }

  public TextRange surroundExpression(Project project, Editor editor, PsiExpression expr) throws IncorrectOperationException {
    PsiManager manager = expr.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    PsiPrefixExpression prefixExpr = (PsiPrefixExpression)factory.createExpressionFromText("!(a)", null);
    prefixExpr = (PsiPrefixExpression)codeStyleManager.reformat(prefixExpr);
    ((PsiParenthesizedExpression)prefixExpr.getOperand()).getExpression().replace(expr);
    expr = (PsiExpression)IntroduceVariableBase.replace(expr, prefixExpr, project);
    int offset = expr.getTextRange().getEndOffset();
    return new TextRange(offset, offset);
  }

  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.not.template");
  }
}