/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class BoolUtils {

  private BoolUtils() {
  }

  public static boolean isNegated(PsiExpression exp) {
    PsiExpression ancestor = exp;
    while (ancestor.getParent() instanceof PsiParenthesizedExpression) {
      ancestor = (PsiExpression)ancestor.getParent();
    }
    if (ancestor.getParent() instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixAncestor =
        (PsiPrefixExpression)ancestor.getParent();
      final IElementType tokenType = prefixAncestor.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.EXCL)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static PsiExpression findNegation(PsiExpression exp) {
    PsiExpression ancestor = exp;
    while (ancestor.getParent() instanceof PsiParenthesizedExpression) {
      ancestor = (PsiExpression)ancestor.getParent();
    }
    if (ancestor.getParent() instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixAncestor =
        (PsiPrefixExpression)ancestor.getParent();
      if (JavaTokenType.EXCL.equals(prefixAncestor.getOperationTokenType())) {
        return prefixAncestor;
      }
    }
    return null;
  }

  public static boolean isNegation(PsiExpression exp) {
    if (!(exp instanceof PsiPrefixExpression)) {
      return false;
    }
    final PsiPrefixExpression prefixExp = (PsiPrefixExpression)exp;
    final IElementType tokenType = prefixExp.getOperationTokenType();
    return tokenType.equals(JavaTokenType.EXCL);
  }

  @Nullable
  public static PsiExpression getNegated(PsiExpression exp) {
    final PsiPrefixExpression prefixExp = (PsiPrefixExpression)exp;
    final PsiExpression operand = prefixExp.getOperand();
    if (operand == null) {
      return null;
    }
    return ParenthesesUtils.stripParentheses(operand);
  }

  public static boolean isBooleanLiteral(PsiExpression expression) {
    if (!(expression instanceof PsiLiteralExpression)) {
      return false;
    }
    final PsiLiteralExpression literalExpression =
      (PsiLiteralExpression)expression;
    @NonNls final String text = literalExpression.getText();
    return PsiKeyword.TRUE.equals(text) ||
           PsiKeyword.FALSE.equals(text);
  }

  public static String getNegatedExpressionText(
    @Nullable PsiExpression condition) {
    if (condition == null) {
      return "";
    }
    if (condition instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression =
        (PsiParenthesizedExpression)condition;
      final PsiExpression expression =
        parenthesizedExpression.getExpression();
      return '(' + getNegatedExpressionText(expression) + ')';
    }
    else if (isNegation(condition)) {
      final PsiExpression negated = getNegated(condition);
      if (negated == null) {
        return "";
      }
      return negated.getText();
    }
    else if (ComparisonUtils.isComparison(condition)) {
      final PsiBinaryExpression binaryExpression =
        (PsiBinaryExpression)condition;
      final String negatedComparison =
        ComparisonUtils.getNegatedComparison(binaryExpression.getOperationTokenType());
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) {
        return lhs.getText() + negatedComparison;
      }
      return lhs.getText() + negatedComparison + rhs.getText();
    }
    else if (ParenthesesUtils.getPrecedence(condition) >
             ParenthesesUtils.PREFIX_PRECEDENCE) {
      return "!(" + condition.getText() + ')';
    }
    else {
      return '!' + condition.getText();
    }
  }
}