/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MagicNumberInspectionBase extends BaseInspection {
  @SuppressWarnings("PublicField")
  public boolean ignoreInHashCode = true;
  @SuppressWarnings("PublicField")
  public boolean ignoreInTestCode = false;
  @SuppressWarnings("PublicField")
  public boolean ignoreInAnnotations = true;
  @SuppressWarnings("PublicField")
  public boolean ignoreInitialCapacity = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("magic.number.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("magic.number.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("magic.number.ignore.option"), "ignoreInHashCode");
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.in.test.code"), "ignoreInTestCode");
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.in.annotations"),"ignoreInAnnotations");
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.as.initial.capacity"), "ignoreInitialCapacity");
    return panel;
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MagicNumberVisitor();
  }

  private class MagicNumberVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      final PsiType type = expression.getType();
      if (!ClassUtils.isPrimitiveNumericType(type) || PsiType.CHAR.equals(type)) {
        return;
      }
      if (isSpecialCaseLiteral(expression) || ExpressionUtils.isDeclaredConstant(expression)) {
        return;
      }
      if (ignoreInHashCode) {
        final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
        if (MethodUtils.isHashCode(containingMethod)) {
          return;
        }
      }
      if (ignoreInTestCode && TestUtils.isInTestCode(expression)) {
        return;
      }
      if (ignoreInAnnotations) {
        final boolean insideAnnotation = AnnotationUtil.isInsideAnnotation(expression);
        if (insideAnnotation) {
          return;
        }
      }
      if (ignoreInitialCapacity) {
        final PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(expression, PsiExpressionList.class, true, PsiMember.class);
        if (expressionList != null) {
          final PsiElement parent = expressionList.getParent();
          if (parent instanceof PsiNewExpression) {
            final PsiNewExpression newExpression = (PsiNewExpression)parent;
            if (TypeUtils.expressionHasTypeOrSubtype(newExpression, CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER,
                                                     CommonClassNames.JAVA_UTIL_MAP, CommonClassNames.JAVA_UTIL_COLLECTION) != null) {
              return;
            }
          }
        }
      }
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiPrefixExpression) {
        registerError(parent);
      }
      else {
        registerError(expression);
      }
    }

    private boolean isSpecialCaseLiteral(PsiLiteralExpression expression) {
      final Object object = ExpressionUtils.computeConstantExpression(expression);
      if (object instanceof Integer) {
        final int i = ((Integer)object).intValue();
        return i >= 0 && i <= 10 || i == 100 || i == 1000;
      }
      else if (object instanceof Long) {
        final long l = ((Long)object).longValue();
        return l >= 0L && l <= 2L;
      }
      else if (object instanceof Double) {
        final double d = ((Double)object).doubleValue();
        return d == 1.0 || d == 0.0;
      }
      else if (object instanceof Float) {
        final float f = ((Float)object).floatValue();
        return f == 1.0f || f == 0.0f;
      }
      return false;
    }
  }
}
