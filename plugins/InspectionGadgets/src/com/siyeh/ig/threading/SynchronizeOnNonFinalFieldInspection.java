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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MakeFieldFinalFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SynchronizeOnNonFinalFieldInspection extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "synchronize.on.non.final.field.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "synchronize.on.non.final.field.problem.descriptor");
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final PsiField field = (PsiField) infos[0];
        return MakeFieldFinalFix.buildFix(field);
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new SynchronizeOnNonFinalFieldVisitor();
    }

    private static class SynchronizeOnNonFinalFieldVisitor
            extends BaseInspectionVisitor {

        @Override public void visitSynchronizedStatement(
                @NotNull PsiSynchronizedStatement statement) {
            super.visitSynchronizedStatement(statement);
            final PsiExpression lockExpression = statement.getLockExpression();
            if (!(lockExpression instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReference reference = lockExpression.getReference();
            if (reference == null) {
                return;
            }
            final PsiElement element = reference.resolve();
            if (!(element instanceof PsiField)) {
                return;
            }
            final PsiField field = (PsiField)element;
            if (field.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            registerError(lockExpression, field);
        }
    }
}