/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.chartostring;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;

class StringToCharPredicate implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiLiteralExpression)){
            return false;
        }
        final PsiLiteralExpression expression =
                (PsiLiteralExpression) element;
        final PsiType type = expression.getType();
        if(type == null){
            return false;
        }
        final String typeText = type.getCanonicalText();
        if(!"java.lang.String".equals(typeText)){
            return false;
        }
        final String value = (String) expression.getValue();
        if(value == null || value.length() != 1){
            return false;
        }
        return isInConcatenationContext(element);
    }

    private static boolean isInConcatenationContext(PsiElement element){
        final PsiElement parent = element.getParent();
        if(parent instanceof PsiBinaryExpression){
            final PsiBinaryExpression parentExpression =
                    (PsiBinaryExpression) parent;
            final PsiType parentType = parentExpression.getType();
            if(parentType == null){
                return false;
            }
            final String parentTypeText = parentType.getCanonicalText();
            if(!"java.lang.String".equals(parentTypeText)){
                return false;
            }
            final PsiExpression lhs = parentExpression.getLOperand();
            final PsiExpression rhs = parentExpression.getROperand();
            if(rhs == null){
                return false;
            }
            final PsiExpression otherOperand;
            if(lhs.equals(element)){
                otherOperand = rhs;
            } else{
                otherOperand = lhs;
            }
            final PsiType otherOperandType = otherOperand.getType();
            if(otherOperandType == null){
                return false;
            }
            final String otherOperandTypeText =
                    otherOperandType.getCanonicalText();
            return "java.lang.String".equals(otherOperandTypeText);
        } else if(parent instanceof PsiAssignmentExpression){
            final PsiAssignmentExpression parentExpression =
                    (PsiAssignmentExpression) parent;
          final IElementType tokenType = parentExpression.getOperationTokenType();
            if(!JavaTokenType.PLUSEQ.equals(tokenType)){
                return false;
            }
            final PsiType parentType = parentExpression.getType();
            if(parentType == null){
                return false;
            }
            final String parentTypeText = parentType.getCanonicalText();
            return "java.lang.String".equals(parentTypeText);
        }
        if(parent instanceof PsiExpressionList){
            final PsiElement grandParent = parent.getParent();
            if(!(grandParent instanceof PsiMethodCallExpression)){
                return false;
            }
            final PsiMethodCallExpression methodCall =
                    (PsiMethodCallExpression) grandParent;
            final PsiReferenceExpression methodExpression =
                    methodCall.getMethodExpression();
            final PsiExpression qualifierExpression =
                    methodExpression.getQualifierExpression();
            final PsiType type;
            if(qualifierExpression == null){
                // to use the intention inside the source of
                // String and StringBuffer
                type = methodExpression.getType();
            } else{
                type = qualifierExpression.getType();
            }
            if(type == null){
                return false;
            }
            final String className = type.getCanonicalText();
            if("java.lang.StringBuffer".equals(className) ||
                    "java.lang.StringBuilder".equals(className)){
                @NonNls final String methodName =
                        methodExpression.getReferenceName();
                if(!"append".equals(methodName) &&
                        !"insert".equals(methodName)){
                    return false;
                }
                final PsiElement method = methodExpression.resolve();
                return method != null;
            } else if("java.lang.String".equals(className)) {
                @NonNls final String methodName =
                        methodExpression.getReferenceName();
                if(!"indexOf".equals(methodName) &&
                        !"lastIndexOf".equals(methodName) &&
                        !"replace".equals(methodName)){
                    return false;
                }
                final PsiElement method = methodExpression.resolve();
                return method != null;
            } else{
                return false;
            }
        } else{
            return false;
        }
    }
}