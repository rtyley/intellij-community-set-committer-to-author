/*
 * Copyright 2007-2011 Bas Leijdekkers
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
package com.siyeh.ipp.exceptions;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ConvertCatchToThrowsIntention extends Intention {

    @Override
    @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new ConvertCatchToThrowsPredicate();
    }

    @Override
    protected void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiCatchSection catchSection =
                (PsiCatchSection) element.getParent();
        final PsiMethod method =
                PsiTreeUtil.getParentOfType(catchSection, PsiMethod.class);
        if (method == null) {
            return;
        }
        // todo warn if method implements or overrides some base method
        //             Warning
        // "Method xx() of class XX implements/overrides method of class
        // YY. Do you want to modify the base method?"
        //                                             [Yes][No][Cancel]
        final PsiReferenceList throwsList = method.getThrowsList();
        final Project project = element.getProject();
        final PsiElementFactory factory =
                JavaPsiFacade.getElementFactory(project);
        final PsiType catchType = catchSection.getCatchType();
        if (catchType instanceof PsiClassType) {
            final PsiClassType classType = (PsiClassType) catchType;
            final PsiJavaCodeReferenceElement referenceElement =
                    factory.createReferenceElementByType(classType);
            throwsList.add(referenceElement);
        } else if (catchType instanceof PsiDisjunctionType) {
            final PsiDisjunctionType disjunctionType =
                    (PsiDisjunctionType) catchType;
            final List<PsiType> disjunctions =
                    disjunctionType.getDisjunctions();
            for (PsiType disjunction : disjunctions) {
                if (!(disjunction instanceof PsiClassType)) {
                    continue;
                }
                final PsiClassType classType = (PsiClassType) disjunction;
                final PsiJavaCodeReferenceElement referenceElement =
                        factory.createReferenceElementByType(classType);
                throwsList.add(referenceElement);
            }
        } else {
            return;
        }
        final PsiTryStatement tryStatement = catchSection.getTryStatement();
        final PsiCatchSection[] catchSections = tryStatement.getCatchSections();
        if (catchSections.length > 1 || tryStatement.getResourceList() != null) {
            catchSection.delete();
        } else {
            final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if (tryBlock == null) {
                return;
            }
            final PsiElement parent = tryStatement.getParent();
            final PsiStatement[] statements = tryBlock.getStatements();
            for (PsiStatement statement : statements) {
                parent.addBefore(statement, tryStatement);
            }
            tryStatement.delete();
        }
    }
}