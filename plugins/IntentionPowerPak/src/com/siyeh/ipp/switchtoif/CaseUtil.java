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
package com.siyeh.ipp.switchtoif;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ipp.psiutils.EquivalenceChecker;
import com.siyeh.ipp.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class CaseUtil{

    private CaseUtil(){
        super();
    }

    private static boolean canBeCaseLabel(PsiExpression expression){
        if(expression == null){
            return false;
        }
        if(expression instanceof PsiReferenceExpression){
            final PsiElement referent = ((PsiReference) expression).resolve();
            if(referent instanceof PsiEnumConstant){
                return true;
            }
        }
        final PsiType type = expression.getType();
        if(type == null){
            return false;
        }
        if(!type.equals(PsiType.INT) &&
                !type.equals(PsiType.CHAR) &&
                !type.equals(PsiType.LONG) &&
                !type.equals(PsiType.SHORT)){
            return false;
        }
        return PsiUtil.isConstantExpression(expression);
    }

    public static boolean containsHiddenBreak(PsiStatement statement){
        return containsHiddenBreak(statement, true);
    }

    private static boolean containsHiddenBreak(PsiStatement statement,
                                               boolean isTopLevel){
        if(statement instanceof PsiBlockStatement){
            final PsiCodeBlock codeBlock =
                    ((PsiBlockStatement) statement).getCodeBlock();
            final PsiStatement[] statements = codeBlock.getStatements();
            for(final PsiStatement childStatement : statements){
                if(containsHiddenBreak(childStatement, false)){
                    return true;
                }
            }
        } else if(statement instanceof PsiIfStatement){
            final PsiIfStatement ifStatement = (PsiIfStatement) statement;
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            final PsiStatement elseBranch = ifStatement.getElseBranch();
            return containsHiddenBreak(thenBranch, false) ||
                    containsHiddenBreak(elseBranch, false);
        } else if(statement instanceof PsiBreakStatement){
            if(isTopLevel){
                return false;
            }
            final PsiIdentifier identifier =
                    ((PsiBreakStatement) statement).getLabelIdentifier();
            if(identifier == null){
                return true;
            }
            final String text = identifier.getText();
            return "".equals(text);
        }
        return false;
    }

    public static boolean isUsedByStatementList(PsiLocalVariable variable,
                                                List<PsiElement> elements){
        for(PsiElement element : elements){
            if(isUsedByStatement(variable, element)){
                return true;
            }
        }
        return false;
    }

    private static boolean isUsedByStatement(PsiLocalVariable variable,
                                             PsiElement statement){
        final LocalVariableUsageVisitor visitor =
                new LocalVariableUsageVisitor(variable);
        statement.accept(visitor);
        return visitor.isUsed();
    }

    public static String findUniqueLabel(PsiStatement statement,
                                         @NonNls String baseName){
        PsiElement ancestor = statement;
        while(ancestor.getParent() != null){
            if(ancestor instanceof PsiMethod
                    || ancestor instanceof PsiClass
                    || ancestor instanceof PsiFile){
                break;
            }
            ancestor = ancestor.getParent();
        }
        if(!checkForLabel(baseName, ancestor)){
            return baseName;
        }
        int val = 1;
        while(true){
            final String name = baseName + val;
            if(!checkForLabel(name, ancestor)){
                return name;
            }
            val++;
        }
    }

    private static boolean checkForLabel(String name, PsiElement ancestor){
        final LabelSearchVisitor visitor = new LabelSearchVisitor(name);
        ancestor.accept(visitor);
        return visitor.isUsed();
    }

    @Nullable
    public static PsiExpression getCaseExpression(PsiIfStatement statement){
        final PsiExpression condition = statement.getCondition();
        final LanguageLevel languageLevel =
                PsiUtil.getLanguageLevel(statement);
        final boolean stringSwitch =
                languageLevel.compareTo(LanguageLevel.JDK_1_7) >= 0;
        final PsiExpression possibleCaseExpression =
                determinePossibleCaseExpressions(condition, stringSwitch);
        if(possibleCaseExpression == null){
            return null;
        }
        if (SideEffectChecker.mayHaveSideEffects(possibleCaseExpression)) {
            return null;
        }
        while(true){
            final PsiExpression caseCondition = statement.getCondition();
            if (!canBeMadeIntoCase(caseCondition, possibleCaseExpression,
                    stringSwitch)) {
                break;
            }
            final PsiStatement elseBranch = statement.getElseBranch();
            if(!(elseBranch instanceof PsiIfStatement)){
                return possibleCaseExpression;
            }
            statement = (PsiIfStatement) elseBranch;
        }
        return null;
    }

    private static PsiExpression determinePossibleCaseExpressions(
            PsiExpression expression, boolean stringSwitch){
        while(expression instanceof PsiParenthesizedExpression){
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression)expression;
            expression = parenthesizedExpression.getExpression();
        }
        if (expression == null) {
            return null;
        }
        if (stringSwitch) {
            final PsiExpression jdk17Expression =
                    determinePossibleStringCaseExpression(expression);
            if (jdk17Expression != null) {
                return jdk17Expression;
            }
        }
        if (!(expression instanceof PsiBinaryExpression)){
            return null;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) expression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType operation = sign.getTokenType();
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        if(operation.equals(JavaTokenType.OROR)){
            return determinePossibleCaseExpressions(lhs, stringSwitch);
        } else if(operation.equals(JavaTokenType.EQEQ)){
            if(canBeCaseLabel(lhs)){
                return rhs;
            } else if (canBeCaseLabel(rhs)){
                return lhs;
            }
        }
        return null;
    }

    private static PsiExpression determinePossibleStringCaseExpression(
            PsiExpression expression) {
        if (!(expression instanceof PsiMethodCallExpression)) {
            return null;
        }
        final PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression) expression;
        final PsiReferenceExpression methodExpression =
                methodCallExpression.getMethodExpression();
        final String referenceName = methodExpression.getReferenceName();
        if (!"equals".equals(referenceName)) {
            return null;
        }
        final PsiExpression qualifierExpression =
                methodExpression.getQualifierExpression();
        if (qualifierExpression == null) {
            return null;
        }
        final PsiType type = qualifierExpression.getType();
        if (type == null || !type.equalsToText("java.lang.String")) {
            return null;
        }
        final PsiExpressionList argumentList =
                methodCallExpression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length != 1) {
            return null;
        }
        final PsiExpression argument = arguments[0];
        final PsiType argumentType = argument.getType();
        if (argumentType == null ||
                !argumentType.equalsToText("java.lang.String")) {
            return null;
        }
        if (PsiUtil.isConstantExpression(qualifierExpression)) {
            return argument;
        } else if (PsiUtil.isConstantExpression(argument)) {
            return qualifierExpression;
        }
        return null;
    }

    private static boolean canBeMadeIntoCase(
            PsiExpression expression, PsiExpression caseExpression,
            boolean stringSwitch) {
        while(expression instanceof PsiParenthesizedExpression){
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression)expression;
            expression = parenthesizedExpression.getExpression();
        }
        if (stringSwitch) {
            final PsiExpression stringCaseExpression =
                    determinePossibleStringCaseExpression(expression);
            if (EquivalenceChecker.expressionsAreEquivalent(caseExpression,
                    stringCaseExpression)) {
                return true;
            }
        }
        if(!(expression instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) expression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType operation = sign.getTokenType();
        final PsiExpression lOperand = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        if(operation.equals(JavaTokenType.OROR)){
            return canBeMadeIntoCase(lOperand, caseExpression, stringSwitch) &&
                    canBeMadeIntoCase(rhs, caseExpression, stringSwitch);
        } else if(operation.equals(JavaTokenType.EQEQ)){
            if(canBeCaseLabel(lOperand) &&
                    EquivalenceChecker.expressionsAreEquivalent(caseExpression,
                                                                rhs)){
                return true;
            } else if(canBeCaseLabel(rhs) &&
                    EquivalenceChecker.expressionsAreEquivalent(caseExpression,
                                                                lOperand)){
                return true;
            }
            return false;
        } else{
            return false;
        }
    }
}