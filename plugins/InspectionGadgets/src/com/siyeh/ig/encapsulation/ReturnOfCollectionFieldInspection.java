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
package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ReturnOfCollectionFieldInspection extends BaseInspection{

    /** @noinspection PublicField*/
    public boolean ignorePrivateMethods = true;

    @Override
    @NotNull
    public String getID(){
        return "ReturnOfCollectionOrArrayField";
    }

    @Override
    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "return.of.collection.array.field.display.name");
    }

    @Override
    @Nullable
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "return.of.collection.array.field.option"), this,
                "ignorePrivateMethods");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos){
        final PsiField field = (PsiField)infos[0];
        final PsiType type = field.getType();
        if(type instanceof PsiArrayType){
            return InspectionGadgetsBundle.message(
                    "return.of.collection.array.field.problem.descriptor.array");
        } else{
            return InspectionGadgetsBundle.message(
                    "return.of.collection.array.field.problem.descriptor.collection");
        }
    }

    @Override
    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) infos[1];
        final String text = referenceExpression.getText();
        if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression,
                CommonClassNames.JAVA_UTIL_MAP)) {
            if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression,
                    "java.util.SortedMap")) {
                return new ReturnOfCollectionFieldFix(
                        "java.util.Collections.unmodifiableSortedMap(" +
                                text + ')');
            }
            return new ReturnOfCollectionFieldFix(
                    "java.util.Collections.unmodifiableMap(" + text + ')');
        } else if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression,
                CommonClassNames.JAVA_UTIL_COLLECTION)) {
            if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression,
                    CommonClassNames.JAVA_UTIL_SET)) {
                if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression,
                        "java.util.SortedSet")) {
                    return new ReturnOfCollectionFieldFix(
                            "java.util.Collections.unmodifiableSortedSet(" +
                                    text + ')');
                }
                return new ReturnOfCollectionFieldFix(
                        "java.util.Collections.unmodifiableSet(" + text + ')');
            } else if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression,
                    CommonClassNames.JAVA_UTIL_LIST)) {
                return new ReturnOfCollectionFieldFix(
                        "java.util.Collections.unmodifiableList(" + text + ')');
            }
            return new ReturnOfCollectionFieldFix(
                    "java.util.Collections.unmodifiableCollection(" + text +
                            ')');
        }
        return null;
    }

    @Override
    public BaseInspectionVisitor buildVisitor(){
        return new ReturnOfCollectionFieldVisitor();
    }

    private static class ReturnOfCollectionFieldFix
            extends InspectionGadgetsFix {

        private final String replacementText;

        ReturnOfCollectionFieldFix(@NonNls String replacementText) {
            this.replacementText = replacementText;
        }

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "return.of.collection.field.quickfix", replacementText);
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) element;
            replaceExpressionAndShorten(referenceExpression, replacementText);
        }
    }

    private class ReturnOfCollectionFieldVisitor
            extends BaseInspectionVisitor {

        @Override
        public void visitReturnStatement(@NotNull PsiReturnStatement statement){
            super.visitReturnStatement(statement);
            final PsiExpression returnValue = statement.getReturnValue();
            if(returnValue == null){
                return;
            }
            final PsiMethod containingMethod =
                    PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
            if (containingMethod == null) {
                return;
            }
            if (ignorePrivateMethods &&
                    containingMethod.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            final PsiClass returnStatementClass =
                    containingMethod.getContainingClass();
            if (returnStatementClass == null) {
                return;
            }
            if (!(returnValue instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression)returnValue;
            final PsiElement referent = referenceExpression.resolve();
            if(!(referent instanceof PsiField)){
                return;
            }
            final PsiField field = (PsiField) referent;
            final PsiClass fieldClass = field.getContainingClass();
            if (!returnStatementClass.equals(fieldClass)) {
                return;
            }
            if(!CollectionUtils.isArrayOrCollectionField(field)){
                return;
            }
            registerError(returnValue, field, returnValue);
        }
    }
}