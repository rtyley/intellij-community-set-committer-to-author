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
package com.siyeh.ipp.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;

public class ConcatenationUtils{

    private ConcatenationUtils(){
        super();
    }

    public static boolean isConcatenation(PsiElement element){
        if(!(element instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression expression = (PsiBinaryExpression) element;
        final PsiJavaToken sign = expression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(!tokenType.equals(JavaTokenType.PLUS)){
            return false;
        }
        final PsiExpression rhs = expression.getROperand();
        if(rhs == null){
            return false;
        }
        final PsiType type = expression.getType();
        if(type == null){
            final PsiExpression lhs = expression.getLOperand();
            return hasStringType(lhs) || hasStringType(rhs);
        }
        return type.equalsToText("java.lang.String");
    }

    private static boolean hasStringType(PsiExpression expression) {
        if (expression == null) {
            return false;
        }
        final PsiType type = expression.getType();
        return type != null && type.equalsToText("java.lang.String");
    }
}
