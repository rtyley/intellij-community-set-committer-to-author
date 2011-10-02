/*
 * Copyright 2006-2011 Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

public class SizeReplaceableByIsEmptyInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreNegations = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "size.replaceable.by.isempty.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "expression.can.be.replaced.problem.descriptor", infos[0]);
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "size.replaceable.by.isempty.negation.ignore.option"), this,
                                          "ignoreNegations");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new SizeReplaceableByIsEmptyFix();
  }

  private static class SizeReplaceableByIsEmptyFix
    extends InspectionGadgetsFix {

    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "size.replaceable.by.isempty.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiBinaryExpression binaryExpression =
        (PsiBinaryExpression)descriptor.getPsiElement();
      PsiExpression operand = binaryExpression.getLOperand();
      if (!(operand instanceof PsiMethodCallExpression)) {
        operand = binaryExpression.getROperand();
      }
      if (!(operand instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)operand;
      final PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression =
        methodExpression.getQualifierExpression();
      if (qualifierExpression == null) {
        return;
      }
      @NonNls String newExpression = qualifierExpression.getText();
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (!JavaTokenType.EQEQ.equals(tokenType)) {
        newExpression = '!' + newExpression;
      }
      newExpression += ".isEmpty()";
      replaceExpression(binaryExpression, newExpression);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SizeReplaceableByIsEmptyVisitor();
  }

  private class SizeReplaceableByIsEmptyVisitor
    extends BaseInspectionVisitor {

    @NonNls private String isEmptyCall = "";

    @Override
    public void visitBinaryExpression(
      PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }
      if (!ComparisonUtils.isComparison(expression)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      if (lhs instanceof PsiMethodCallExpression) {
        if (canBeReplacedByIsEmpty(lhs, rhs, false,
                                   expression.getOperationTokenType())) {
          registerError(expression, isEmptyCall);
        }
      }
      else if (rhs instanceof PsiMethodCallExpression) {
        if (canBeReplacedByIsEmpty(rhs, lhs, true,
                                   expression.getOperationTokenType())) {
          registerError(expression, isEmptyCall);
        }
      }
    }

    private boolean canBeReplacedByIsEmpty(
      PsiExpression lhs, PsiExpression rhs,
      boolean flipped, IElementType tokenType) {
      final PsiMethodCallExpression callExpression =
        (PsiMethodCallExpression)lhs;
      if (!isSizeCall(callExpression)) {
        return false;
      }
      final Object object =
        ExpressionUtils.computeConstantExpression(rhs);
      if (!(object instanceof Integer)) {
        return false;
      }
      final Integer integer = (Integer)object;
      final int constant = integer.intValue();
      if (constant != 0) {
        return false;
      }
      if (JavaTokenType.EQEQ.equals(tokenType)) {
        return true;
      }
      if (ignoreNegations) {
        return false;
      }
      isEmptyCall = '!' + isEmptyCall;
      if (JavaTokenType.NE.equals(tokenType)) {
        return true;
      }
      else if (flipped) {
        if (JavaTokenType.LT.equals(tokenType)) {
          return true;
        }
      }
      else if (JavaTokenType.GT.equals(tokenType)) {
        return true;
      }
      return false;
    }

    private boolean isSizeCall(
      PsiMethodCallExpression callExpression) {
      final PsiReferenceExpression methodExpression =
        callExpression.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.SIZE.equals(referenceName)) {
        return false;
      }
      final PsiExpressionList argumentList =
        callExpression.getArgumentList();
      final PsiExpression[] expressions = argumentList.getExpressions();
      if (expressions.length != 0) {
        return false;
      }
      final PsiExpression qualifierExpression =
        methodExpression.getQualifierExpression();
      if (qualifierExpression == null) {
        return false;
      }
      isEmptyCall = qualifierExpression.getText() + ".isEmpty()";
      final PsiType type = qualifierExpression.getType();
      if (!(type instanceof PsiClassType)) {
        return false;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass aClass = classType.resolve();
      if (aClass == null) {
        return false;
      }
      final PsiMethod[] methods =
        aClass.findMethodsByName("isEmpty", true);
      for (PsiMethod method : methods) {
        final PsiParameterList parameterList =
          method.getParameterList();
        if (parameterList.getParametersCount() == 0) {
          return true;
        }
      }
      return false;
    }
  }
}