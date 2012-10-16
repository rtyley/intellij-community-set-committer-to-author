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
package com.siyeh.ig.logging;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public class StringConcatenationArgumentToLogCallInspection extends BaseInspection {

  private static final Set<String> logNames = new THashSet<String>();
  static {
    logNames.add("trace");
    logNames.add("debug");
    logNames.add("info");
    logNames.add("warn");
    logNames.add("error");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.problem.descriptor");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final int count = StringConcatenationArgumentToLogCallFix.isAvailable((PsiExpression)infos[0]);
    if (count == 0) {
      return null;
    }
    return new StringConcatenationArgumentToLogCallFix(count > 1);
  }

  private static class StringConcatenationArgumentToLogCallFix extends InspectionGadgetsFix {

    private final boolean myPlural;

    public StringConcatenationArgumentToLogCallFix(boolean plural) {
      myPlural = plural;
    }

    @NotNull
    @Override
    public String getName() {
      if (myPlural) {
        return InspectionGadgetsBundle.message("string.concatenation.in.format.call.plural.quickfix");
      }
      else {
        return InspectionGadgetsBundle.message("string.concatenation.in.format.call.quickfix");
      }
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement grandParent = element.getParent().getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final StringBuilder newMethodCall = new StringBuilder(methodCallExpression.getMethodExpression().getText());
      newMethodCall.append("(");
      PsiExpression argument = arguments[0];
      int usedArguments;
      if (!(argument instanceof PsiPolyadicExpression)) {
        if (!TypeUtils.expressionHasTypeOrSubtype(argument, "org.slf4j.Marker") || arguments.length < 2) {
          return;
        }
        newMethodCall.append(argument.getText()).append(",\"");
        argument = arguments[1];
        usedArguments = 2;
        if (!(argument instanceof PsiPolyadicExpression)) {
          return;
        }
      }
      else {
        newMethodCall.append('\"');
        usedArguments = 1;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)argument;
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return;
      }
      final String methodName = method.getName();
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final PsiMethod[] methods = containingClass.findMethodsByName(methodName, false);
      boolean varArgs = false;
      for (PsiMethod otherMethod : methods) {
        if (otherMethod.isVarArgs()) {
          varArgs = true;
          break;
        }
      }
      final List<PsiExpression> newArguments = new ArrayList();
      final PsiExpression[] operands = polyadicExpression.getOperands();
      for (PsiExpression operand : operands) {
        if (operand instanceof PsiLiteralExpression) {
          final String text = operand.getText();
          final int count = StringUtil.getOccurrenceCount(text, "{}");
          for (int i = 0; i < count && usedArguments + i < arguments.length; i++) {
            newArguments.add((PsiExpression)arguments[i + usedArguments].copy());
          }
          usedArguments += count;
          newMethodCall.append(text.substring(1, text.length() - 1));
        }
        else {
          newArguments.add((PsiExpression)operand.copy());
          newMethodCall.append("{}");
        }
      }
      while (usedArguments < arguments.length) {
        newArguments.add(arguments[usedArguments++]);
      }
      newMethodCall.append('"');
      if (!varArgs && newArguments.size() > 2) {
        newMethodCall.append(", new Object[]{");
        boolean comma = false;
        for (PsiExpression newArgument : newArguments) {
          if (comma) {
            newMethodCall.append(',');
          }
          else {
            comma =true;
          }
          newMethodCall.append(newArgument.getText());
        }
        newMethodCall.append('}');
      }
      else {
        for (PsiExpression newArgument : newArguments) {
          newMethodCall.append(',').append(newArgument.getText());
        }
      }
      newMethodCall.append(')');
      replaceExpression(methodCallExpression, newMethodCall.toString());
    }

    public static int isAvailable(PsiExpression expression) {
      int count = 0;
      if (!(expression instanceof PsiPolyadicExpression)) {
        return count;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      final PsiExpression[] operands = polyadicExpression.getOperands();
      for (PsiExpression operand : operands) {
        if (operand instanceof PsiLiteralExpression) {
          if (!ExpressionUtils.hasStringType(operand)) {
            return count;
          }
          continue;
        }
        if (!(operand instanceof PsiReferenceExpression)) {
          return count;
        }
        count++;
      }
      return count;
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationArgumentToLogCallVisitor();
  }

  private static class StringConcatenationArgumentToLogCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      if (!logNames.contains(referenceName)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || !"org.slf4j.Logger".equals(containingClass.getQualifiedName())) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      PsiExpression argument = arguments[0];
      if (!ExpressionUtils.hasStringType(argument)) {
        if (arguments.length < 2) {
          return;
        }
        argument = arguments[1];
        if (!ExpressionUtils.hasStringType(argument)) {
          return;
        }
      }
      argument = ParenthesesUtils.stripParentheses(argument);
      if (argument == null || !containsConcatenation(argument)) {
        return;
      }
      registerMethodCallError(expression, argument);
    }

    private static boolean containsConcatenation(@Nullable PsiExpression expression) {
      if (expression instanceof PsiParenthesizedExpression) {
        final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
        containsConcatenation(parenthesizedExpression.getExpression());
      }
      else if (expression instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
        if (!ExpressionUtils.hasStringType(polyadicExpression)) {
          return false;
        }
        final IElementType tokenType = polyadicExpression.getOperationTokenType();
        if (!JavaTokenType.PLUS.equals(tokenType)) {
          return false;
        }
        final PsiExpression[] operands = polyadicExpression.getOperands();
        for (PsiExpression operand : operands) {
          if (operand instanceof PsiReferenceExpression) {
            return true;
          }
        }
      }
      return false;
    }
  }
}
