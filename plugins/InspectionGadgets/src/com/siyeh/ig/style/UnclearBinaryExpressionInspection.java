/*
 * Copyright 2011-2012 Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnclearBinaryExpressionInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unclear.binary.expression.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unclear.binary.expression.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnclearBinaryExpressionFix();
  }

  private static class UnclearBinaryExpressionFix extends InspectionGadgetsFix {

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("unclear.binary.expression.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiExpression)) {
        return;
      }
      final PsiExpression expression = (PsiExpression)element;
      final StringBuilder newExpressionText = createReplacementText(expression, new StringBuilder());
      replaceExpression(expression, newExpressionText.toString());
    }

    private static StringBuilder createReplacementText(@Nullable PsiExpression expression, StringBuilder out) {
      if (expression instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
        final IElementType tokenType = polyadicExpression.getOperationTokenType();
        final PsiElement parent = expression.getParent();
        if (parent instanceof PsiPolyadicExpression) {
          final PsiPolyadicExpression parentPolyadicExpression = (PsiPolyadicExpression)parent;
          final IElementType parentOperationSign = parentPolyadicExpression.getOperationTokenType();
          if (!tokenType.equals(parentOperationSign)) {
            out.append('(');
            createText(polyadicExpression, out);
            out.append(')');
            return out;
          }
        } else if (parent instanceof PsiConditionalExpression || parent instanceof PsiInstanceOfExpression) {
          out.append('(');
          createText(polyadicExpression, out);
          out.append(')');
          return out;
        }
        createText(polyadicExpression, out);
      }
      else if (expression instanceof PsiParenthesizedExpression) {
        final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
        final PsiExpression unwrappedExpression = parenthesizedExpression.getExpression();
        final PsiElement parent = expression.getParent();
        if (!(parent instanceof PsiParenthesizedExpression)) {
          out.append('(');
          createReplacementText(unwrappedExpression, out);
          out.append(')');
        }
        else {
          createReplacementText(unwrappedExpression, out);
        }
      }
      else if (expression instanceof PsiInstanceOfExpression) {
        final PsiElement parent = expression.getParent();
        final PsiInstanceOfExpression instanceofExpression = (PsiInstanceOfExpression)expression;
        if (mightBeConfusingExpression(parent)) {
          out.append('(');
          createText(instanceofExpression, out);
          out.append(')');
        }
        else {
          createText(instanceofExpression, out);
        }
      }
      else if (expression instanceof PsiConditionalExpression) {
        final PsiElement parent = expression.getParent();
        final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)expression;
        if (mightBeConfusingExpression(parent)) {
          out.append('(');
          createText(conditionalExpression, out);
          out.append(')');
        }
        else {
          createText(conditionalExpression, out);
        }
      }
      else if (expression != null) {
        out.append(expression.getText());
      }
      return out;
    }

    private static void createText(PsiInstanceOfExpression instanceofExpression, StringBuilder out) {
      final PsiExpression operand = instanceofExpression.getOperand();
      createReplacementText(operand, out);
      out.append(" instanceof ");
      final PsiTypeElement checkType = instanceofExpression.getCheckType();
      if (checkType != null) {
        out.append(checkType.getText());
      }
    }

    private static void createText(PsiConditionalExpression conditionalExpression, StringBuilder out) {
      final PsiExpression condition = conditionalExpression.getCondition();
      createReplacementText(condition, out);
      out.append('?');
      final PsiExpression thenExpression = conditionalExpression.getThenExpression();
      createReplacementText(thenExpression, out);
      out.append(':');
      final PsiExpression elseExpression = conditionalExpression.getElseExpression();
      createReplacementText(elseExpression, out);
    }

    private static void createText(PsiPolyadicExpression polyadicExpression, StringBuilder out) {
      final PsiExpression[] operands = polyadicExpression.getOperands();
      for (PsiExpression operand : operands) {
        if (operand == null) {
          continue;
        }
        if (operand.getType() == PsiType.VOID) {
          throw new ProcessCanceledException();
        }
        if (operands.length == 1) {
          createReplacementText(operand, out);
        }
        final PsiJavaToken token = polyadicExpression.getTokenBeforeOperand(operand);
        if (token != null) {
          final PsiElement beforeToken = token.getPrevSibling();
          if (beforeToken instanceof PsiWhiteSpace) {
            out.append(beforeToken.getText());
          }
          out.append(token.getText());
          final PsiElement afterToken = token.getNextSibling();
          if (afterToken instanceof PsiWhiteSpace) {
            out.append(afterToken.getText());
          }
        }
        if (operands.length != 1) {
          createReplacementText(operand, out);
        }
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnclearBinaryExpressionVisitor();
  }

  private static class UnclearBinaryExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final PsiElement parent = expression.getParent();
      if (mightBeConfusingExpression(parent)) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      final PsiExpression[] operands = expression.getOperands();
      for (PsiExpression operand : operands) {
        if (operand instanceof PsiInstanceOfExpression) {
          registerError(expression);
          return;
        }
        if (!(operand instanceof PsiPolyadicExpression)) {
          continue;
        }
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)operand;
        final IElementType childTokenType = polyadicExpression.getOperationTokenType();
        if (!tokenType.equals(childTokenType)) {
          registerError(expression);
          return;
        }
      }
    }

    @Override
    public void visitConditionalExpression(PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      final PsiElement parent = expression.getParent();
      if (mightBeConfusingExpression(parent)) {
        return;
      }
      final PsiExpression condition = expression.getCondition();
      final PsiExpression thenExpression = expression.getThenExpression();
      final PsiExpression elseExpression = expression.getElseExpression();
      if (!mightBeConfusingExpression(condition) && !mightBeConfusingExpression(thenExpression) &&
          !mightBeConfusingExpression(elseExpression)) {
        return;
      }
      registerError(expression);
    }

    @Override
    public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
      super.visitInstanceOfExpression(expression);
      final PsiElement parent = expression.getParent();
      if (mightBeConfusingExpression(parent)) {
        return;
      }
      final PsiExpression operand = expression.getOperand();
      if (!mightBeConfusingExpression(operand)) {
        return;
      }
      registerError(expression);
    }
  }

  static boolean mightBeConfusingExpression(@Nullable PsiElement element) {
    return element instanceof PsiPolyadicExpression || element instanceof PsiConditionalExpression ||
           element instanceof PsiInstanceOfExpression;
  }
}
