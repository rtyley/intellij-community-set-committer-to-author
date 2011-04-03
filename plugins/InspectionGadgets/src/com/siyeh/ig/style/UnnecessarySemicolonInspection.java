/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnnecessarySemicolonInspection extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessary.semicolon.display.name");
    }

    @Override
    public boolean isEnabledByDefault(){
        return true;
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "unnecessary.semicolon.problem.descriptor");
    }

    @Override
    public BaseInspectionVisitor buildVisitor(){
        return new UnnecessarySemicolonVisitor();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos){
        return new UnnecessarySemicolonFix();
    }

    private static class UnnecessarySemicolonFix extends InspectionGadgetsFix{

        @NotNull
        public String getName(){
            return InspectionGadgetsBundle.message(
                    "unnecessary.semicolon.remove.quickfix");
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement semicolonElement = descriptor.getPsiElement();
            final PsiElement parent = semicolonElement.getParent();
            if(parent instanceof PsiEmptyStatement){
                final PsiElement lastChild = parent.getLastChild();
                if(lastChild instanceof PsiComment){
                    parent.replace(lastChild);
                } else{
                    deleteElement(parent);
                }
            } else{
                deleteElement(semicolonElement);
            }
        }
    }

    private static class UnnecessarySemicolonVisitor
            extends BaseInspectionVisitor{

        /**
         * Finds semicolons between the top level classes in a java file.
         */
        @Override public void visitFile(PsiFile file){
            final PsiElement firstChild = file.getFirstChild();
            PsiElement sibling = skipForwardWhiteSpacesAndComments(firstChild);
            while(sibling != null){
                if(sibling instanceof PsiJavaToken){
                    final PsiJavaToken token = (PsiJavaToken)sibling;
                    final IElementType tokenType = token.getTokenType();
                    if(tokenType.equals(JavaTokenType.SEMICOLON)){
                        registerError(sibling);
                    }
                }
                sibling = skipForwardWhiteSpacesAndComments(sibling);
            }
            super.visitFile(file);
        }

        @Override public void visitClass(@NotNull PsiClass aClass){
            super.visitClass(aClass);

            findUnnecessarySemicolonsAfterEnumConstants(aClass);
            if(!aClass.isEnum()){
                return;
            }
            final PsiField[] fields = aClass.getFields();
            final PsiElement element;
            if(fields.length > 0){
                final PsiField lastField = fields[fields.length - 1];
                if(!(lastField instanceof PsiEnumConstant)){
                    return;
                }
                element = skipForwardWhiteSpacesAndComments(lastField);
            } else {
                final PsiElement lBrace = aClass.getLBrace();
                element = skipForwardWhiteSpacesAndComments(lBrace);
            }
            if(!(element instanceof PsiJavaToken)){
                return;
            }
            final PsiJavaToken token = (PsiJavaToken)element;
            final IElementType tokenType = token.getTokenType();
            if(!tokenType.equals(JavaTokenType.SEMICOLON)){
                return;
            }
            final PsiElement next = skipForwardWhiteSpacesAndComments(element);
            if(next == null || !next.equals(aClass.getRBrace())){
                return;
            }
            registerError(element);
        }

        private void findUnnecessarySemicolonsAfterEnumConstants(
                @NotNull PsiClass aClass) {
            PsiElement child = aClass.getFirstChild();
            while(child != null){
                if(child instanceof PsiJavaToken){
                    final PsiJavaToken token = (PsiJavaToken)child;
                    final IElementType tokenType = token.getTokenType();
                    if(tokenType.equals(JavaTokenType.SEMICOLON)){
                        final PsiElement prevSibling =
                                skipBackwardWhiteSpacesAndComments(child);
                        if(!(prevSibling instanceof PsiEnumConstant)) {
                            if (prevSibling instanceof PsiJavaToken) {
                                final PsiJavaToken javaToken =
                                        (PsiJavaToken)prevSibling;
                                final IElementType prevTokenType =
                                        javaToken.getTokenType();
                                if (!JavaTokenType.COMMA.equals(prevTokenType)
                                        && !JavaTokenType.LBRACE.equals(
                                        prevTokenType)) {
                                    registerError(child);
                                }
                            } else {
                                registerError(child);
                            }
                        }
                    }
                }
                child = skipForwardWhiteSpacesAndComments(child);
            }
        }

        @Nullable
        private static PsiElement skipForwardWhiteSpacesAndComments(
                PsiElement element){
            return PsiTreeUtil.skipSiblingsForward(element,
                    PsiWhiteSpace.class, PsiComment.class);
        }

        @Nullable
        private static PsiElement skipBackwardWhiteSpacesAndComments(
                PsiElement element){
            return PsiTreeUtil.skipSiblingsBackward(element,
                    PsiWhiteSpace.class, PsiComment.class);
        }

        @Override public void visitEmptyStatement(PsiEmptyStatement statement){
            super.visitEmptyStatement(statement);
            final PsiElement parent = statement.getParent();
            if(parent instanceof PsiCodeBlock){
                final PsiElement semicolon = statement.getFirstChild();
                if (semicolon == null) {
                    return;
                }
                registerError(semicolon);
            }
        }

        @Override
        public void visitResourceList(final PsiResourceList resourceList) {
            super.visitResourceList(resourceList);
            final PsiElement last = resourceList.getLastChild();
            if (last instanceof PsiJavaToken && ((PsiJavaToken)last).getTokenType() == JavaTokenType.RPARENTH) {
                final PsiElement prev = skipBackwardWhiteSpacesAndComments(last);
                if (prev instanceof PsiJavaToken && ((PsiJavaToken)prev).getTokenType() == JavaTokenType.SEMICOLON) {
                    registerError(prev);
                }
            }
        }
    }
}