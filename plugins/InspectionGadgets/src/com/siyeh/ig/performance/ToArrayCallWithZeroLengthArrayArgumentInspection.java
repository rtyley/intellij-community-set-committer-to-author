/*
 * Copyright 2007-2012 Bas Leijdekkers
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
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.HighlightUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ToArrayCallWithZeroLengthArrayArgumentInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "to.array.call.with.zero.length.array.argument.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiExpression argument = (PsiExpression)infos[1];
    return InspectionGadgetsBundle.message(
      "to.array.call.with.zero.length.array.argument.problem.descriptor",
      argument.getText());
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ToArrayCallWithZeroLengthArrayArgumentFix();
  }

  private static class ToArrayCallWithZeroLengthArrayArgumentFix extends InspectionGadgetsFix {


    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("to.array.call.with.zero.length.array.argument.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      if (qualifier == null) {
        return;
      }
      final String collectionText = qualifier.getText();
      final PsiType type = argument.getType();
      if (type == null) {
        return;
      }
      final PsiType componentType = type.getDeepComponentType();
      final String typeText = componentType.getCanonicalText();
      if (!(qualifier instanceof PsiMethodCallExpression)) {
        @NonNls final String replacementText = "new " + typeText + '[' + collectionText + ".size()]";
        final String newExpressionText = getElementText(methodCallExpression, argument, replacementText);
        if (newExpressionText == null) {
          return;
        }
        replaceExpression(methodCallExpression, newExpressionText);
        return;
      }
      // need to introduce a variable to prevent calling a method twice
      PsiStatement statement = PsiTreeUtil.getParentOfType(methodCallExpression, PsiStatement.class);
      if (statement == null) {
        return;
      }
      final PsiType qualifierType = qualifier.getType();
      if (qualifierType == null) {
        return;
      }
      PsiDeclarationStatement declarationStatement = factory.createVariableDeclarationStatement("var", qualifierType, qualifier);
      PsiElement statementParent = statement.getParent();
      while (statementParent instanceof PsiLoopStatement || statementParent instanceof PsiIfStatement) {
        statement = (PsiStatement) statementParent;
        statementParent = statement.getParent();
      }
      final String toArrayText = "var.toArray(new " + typeText + "[var.size()])";
      PsiMethodCallExpression newMethodCallExpression =
        (PsiMethodCallExpression)factory.createExpressionFromText(toArrayText, methodCallExpression);
      declarationStatement = (PsiDeclarationStatement)statementParent.addBefore(declarationStatement, statement);
      newMethodCallExpression = (PsiMethodCallExpression)methodCallExpression.replace(newMethodCallExpression);
      showRenameTemplate(declarationStatement, newMethodCallExpression, statementParent);
    }

    private void showRenameTemplate(PsiDeclarationStatement declarationStatement, PsiMethodCallExpression methodCallExpression,
                                    PsiElement context) {
      if (!isOnTheFly()) {
        return;
      }
      final PsiVariable variable = (PsiVariable)declarationStatement.getDeclaredElements()[0];
      final PsiReferenceExpression ref1 = (PsiReferenceExpression)methodCallExpression.getMethodExpression().getQualifierExpression();
      final PsiNewExpression argument = (PsiNewExpression)methodCallExpression.getArgumentList().getExpressions()[0];
      final PsiMethodCallExpression sizeExpression = (PsiMethodCallExpression)argument.getArrayDimensions()[0];
      final PsiReferenceExpression ref2 = (PsiReferenceExpression)sizeExpression.getMethodExpression().getQualifierExpression();
      HighlightUtils.showRenameTemplate(context, variable, ref1, ref2);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ToArrayCallWithZeroLengthArrayArgument();
  }

  private static class ToArrayCallWithZeroLengthArrayArgument extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"toArray".equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      final PsiType type = argument.getType();
      if (!(type instanceof PsiArrayType)) {
        return;
      }
      if (type.getArrayDimensions() != 1) {
        return;
      }
      if (argument instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)argument;
        final PsiElement element = referenceExpression.resolve();
        if (!(element instanceof PsiField)) {
          return;
        }
        final PsiField field = (PsiField)element;
        if (!CollectionUtils.isConstantEmptyArray(field)) {
          return;
        }
      }
      else if (!ExpressionUtils.isZeroLengthArrayConstruction(argument)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (!InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
        return;
      }
      registerMethodCallError(expression, expression, argument);
    }
  }
}