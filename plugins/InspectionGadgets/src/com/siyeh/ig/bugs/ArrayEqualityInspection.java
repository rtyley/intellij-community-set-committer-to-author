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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ArrayEqualityInspection extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "array.comparison.display.name");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "array.comparison.problem.descriptor");
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new ArrayEqualityFix();
    }

    private static class ArrayEqualityFix extends InspectionGadgetsFix {
        
        @NotNull
        @Override
        public String getName() {
            return InspectionGadgetsBundle.message("array.comparison.quickfix");
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiBinaryExpression)) {
                return;
            }
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) parent;
            final IElementType tokenType =
                    binaryExpression.getOperationTokenType();
            @NonNls final StringBuilder newExpressionText = new StringBuilder();
            if (JavaTokenType.NE.equals(tokenType)) {
                newExpressionText.append('!');
            } else if (!JavaTokenType.EQEQ.equals(tokenType)) {
                return;
            }
            newExpressionText.append("java.util.Arrays.equals(");
            newExpressionText.append(binaryExpression.getLOperand().getText());
            newExpressionText.append(',');
            final PsiExpression rhs = binaryExpression.getROperand();
            if (rhs == null) {
                return;
            }
            newExpressionText.append(rhs.getText());
            newExpressionText.append(')');
            replaceExpressionAndShorten(binaryExpression,
                    newExpressionText.toString());
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ArrayEqualityVisitor();
    }

    private static class ArrayEqualityVisitor extends BaseInspectionVisitor {

        @Override public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!(expression.getROperand() != null)){
                return;
            }
            if (!ComparisonUtils.isEqualityComparison(expression)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            if (!(lhs.getType() instanceof PsiArrayType)) {
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            if (rhs == null) {
                return;
            }
            if (!(rhs.getType() instanceof PsiArrayType)) {
                return;
            }
            final String lhsText = lhs.getText();
            if (PsiKeyword.NULL.equals(lhsText)) {
                return;
            }
            final String rhsText = rhs.getText();
            if (PsiKeyword.NULL.equals(rhsText)) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            registerError(sign);
        }
    }
}