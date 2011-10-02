/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class StringConcatenationInsideStringBufferAppendInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "string.concatenation.inside.string.buffer.append.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    final String className = aClass.getName();
    return InspectionGadgetsBundle.message(
      "string.concatenation.inside.string.buffer.append.problem.descriptor",
      className);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationInsideStringBufferAppendVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ReplaceWithChainedAppendFix();
  }

  private static class ReplaceWithChainedAppendFix
    extends InspectionGadgetsFix {

    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "string.concatenation.inside.string.buffer.append.replace.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement methodNameElement = descriptor.getPsiElement();
      final PsiReferenceExpression methodExpression =
        (PsiReferenceExpression)methodNameElement.getParent();
      if (methodExpression == null) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)methodExpression.getParent();
      if (methodCallExpression == null) {
        return;
      }
      final PsiExpressionList argumentList =
        methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiExpression argument = arguments[0];
      final boolean useStringValueOf;
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        useStringValueOf = false;
      }
      else {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
          useStringValueOf = false;
        }
        else {
          final String qualifiedName = containingClass.getQualifiedName();
          if (qualifiedName == null) {
            useStringValueOf = false;
          }
          else {
            useStringValueOf = !qualifiedName.equals(
              CommonClassNames.JAVA_LANG_STRING_BUFFER) &&
                               !qualifiedName.equals("java.lang.StringBuilder");
          }
        }
      }
      final List<String> expressions =
        findConcatenationComponents(argument, useStringValueOf);
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      @NonNls final StringBuffer newExpressionBuffer = new StringBuffer();
      newExpressionBuffer.append(qualifier.getText());
      for (String expression : expressions) {
        newExpressionBuffer.append(".append(");
        newExpressionBuffer.append(expression);
        newExpressionBuffer.append(')');
      }
      final String newExpression = newExpressionBuffer.toString();
      replaceExpression(methodCallExpression, newExpression);
    }

    private static List<String> findConcatenationComponents(
      PsiExpression concatenation, boolean useStringValueOf)
      throws IncorrectOperationException {
      final List<String> out = new ArrayList<String>();
      findConcatenationComponents(concatenation, out, useStringValueOf);
      return out;
    }

    private static void findConcatenationComponents(
      PsiExpression concatenation, @NonNls List<String> out,
      boolean useStringValueOf)
      throws IncorrectOperationException {
      final PsiType type = concatenation.getType();
      if (concatenation instanceof PsiBinaryExpression) {
        if (type != null && type.equalsToText(
          CommonClassNames.JAVA_LANG_STRING)) {
          PsiBinaryExpression binaryExpression =
            (PsiBinaryExpression)concatenation;
          PsiExpression lhs = binaryExpression.getLOperand();
          PsiExpression rhs = binaryExpression.getROperand();
          assert rhs != null;
          if (!PsiUtil.isConstantExpression(rhs)) {
            findConcatenationComponents(lhs, out, useStringValueOf);
            findConcatenationComponents(rhs, out, useStringValueOf);
            return;
          }
          final StringBuffer builder =
            new StringBuffer(rhs.getText());
          while (lhs instanceof PsiBinaryExpression) {
            final PsiType lhsType = lhs.getType();
            if (lhsType == null ||
                !lhsType.equalsToText(
                  CommonClassNames.JAVA_LANG_STRING)) {
              break;
            }
            binaryExpression = (PsiBinaryExpression)lhs;
            rhs = binaryExpression.getROperand();
            assert rhs != null;
            if (!PsiUtil.isConstantExpression(rhs)) {
              findConcatenationComponents(lhs, out, useStringValueOf);
              out.add(builder.toString());
              return;
            }
            lhs = binaryExpression.getLOperand();
            builder.insert(0, " + ");
            builder.insert(0, rhs.getText());
          }
          if (PsiUtil.isConstantExpression(lhs)) {
            builder.insert(0, " + ");
            builder.insert(0, lhs.getText());
            out.add(builder.toString());
          }
          else {
            findConcatenationComponents(lhs, out, useStringValueOf);
            out.add(builder.toString());
          }
          return;
        }
      }
      else if (concatenation instanceof PsiParenthesizedExpression) {
        final PsiParenthesizedExpression parenthesizedExpression =
          (PsiParenthesizedExpression)concatenation;
        final PsiExpression expression =
          parenthesizedExpression.getExpression();
        if (expression != null) {
          out.add(expression.getText());
        }
        return;
      }
      if (useStringValueOf && type != null &&
          !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        out.add("String.valueOf(" + concatenation.getText() + ')');
      }
      else {
        out.add(concatenation.getText());
      }
    }
  }

  private static class StringConcatenationInsideStringBufferAppendVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls final String methodName =
        methodExpression.getReferenceName();
      if (!"append".equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      if (!isConcatenation(argument)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String className = containingClass.getQualifiedName();
      if (CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(className) ||
          "java.lang.StringBuilder".equals(className)) {
        registerMethodCallError(expression, containingClass);
        return;
      }
      final Project project = containingClass.getProject();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiClass appendableClass =
        psiFacade.findClass("java.lang.Appendable",
                            GlobalSearchScope.allScope(project));
      if (appendableClass == null) {
        return;
      }
      if (!containingClass.isInheritor(appendableClass, true)) {
        return;
      }
      registerMethodCallError(expression, containingClass);
    }

    private static boolean isConcatenation(PsiExpression expression) {
      if (!(expression instanceof PsiBinaryExpression)) {
        return false;
      }
      if (PsiUtil.isConstantExpression(expression)) {
        return false;
      }
      final PsiType type = expression.getType();
      if (type == null) {
        return false;
      }
      return type.equalsToText(CommonClassNames.JAVA_LANG_STRING);
    }
  }
}