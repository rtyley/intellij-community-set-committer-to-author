/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.base;

import com.intellij.codeInsight.intention.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.util.*;
import com.siyeh.*;
import com.siyeh.ipp.psiutils.*;
import org.jetbrains.annotations.*;

public abstract class Intention extends PsiElementBaseIntentionAction {
    private final PsiElementPredicate predicate;

    /** @noinspection AbstractMethodCallInConstructor,OverridableMethodCallInConstructor*/
    protected Intention(){
        super();
        predicate = getElementPredicate();
    }

    public void invoke(@NotNull Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        if(isFileReadOnly(project, file)){
            return;
        }
        final PsiElement element = findMatchingElement(file, editor);
        if(element == null){
            return;
        }
        processIntention(element);
    }

    protected abstract void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException;

    @NotNull protected abstract PsiElementPredicate getElementPredicate();

    protected static void replaceExpression(@NotNull String newExpression,
                                            @NotNull PsiExpression expression)
            throws IncorrectOperationException{
        final PsiManager mgr = expression.getManager();
        final JavaPsiFacade psiFacade =
                JavaPsiFacade.getInstance(mgr.getProject());
        final PsiElementFactory factory = psiFacade.getElementFactory();
        final PsiExpression newCall =
                factory.createExpressionFromText(newExpression, expression);
        final PsiElement insertedElement = expression.replace(newCall);
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceExpressionWithNegatedExpression(
            @NotNull PsiExpression newExpression,
            @NotNull PsiExpression expression)
            throws IncorrectOperationException{
        final PsiManager manager = expression.getManager();
        final JavaPsiFacade psiFacade =
                JavaPsiFacade.getInstance(manager.getProject());
        final PsiElementFactory factory = psiFacade.getElementFactory();
        PsiExpression expressionToReplace = expression;
        final String newExpressionText = newExpression.getText();
        final String expString;
        if(BoolUtils.isNegated(expression)){
            expressionToReplace = BoolUtils.findNegation(expression);
            expString = newExpressionText;
        } else if(ComparisonUtils.isComparison(newExpression)){
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) newExpression;
            final PsiJavaToken sign = binaryExpression.getOperationSign();
            final String negatedComparison =
                    ComparisonUtils.getNegatedComparison(sign);
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            assert rhs != null;
            expString = lhs.getText() + negatedComparison + rhs.getText();
        } else{
            if(ParenthesesUtils.getPrecedence(newExpression) >
                    ParenthesesUtils.PREFIX_PRECEDENCE){
                expString = "!(" + newExpressionText + ')';
            } else{
                expString = '!' + newExpressionText;
            }
        }
        final PsiExpression newCall =
                factory.createExpressionFromText(expString, expression);
        assert expressionToReplace != null;
        final PsiElement insertedElement = expressionToReplace.replace(newCall);
        final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceExpressionWithNegatedExpressionString(
            @NotNull String newExpression,
            @NotNull PsiExpression expression)
            throws IncorrectOperationException{
        final PsiManager mgr = expression.getManager();
        final Project project = mgr.getProject();
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiElementFactory factory = psiFacade.getElementFactory();
        PsiExpression expressionToReplace = expression;
        final String expString;
        if(BoolUtils.isNegated(expression)){
            expressionToReplace = BoolUtils.findNegation(expression);
            expString = newExpression;
        } else{
            expString = "!(" + newExpression + ')';
        }
        final PsiExpression newCall =
                factory.createExpressionFromText(expString, expression);
        assert expressionToReplace != null;
        final PsiElement insertedElement = expressionToReplace.replace(newCall);
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceStatement(
            @NonNls @NotNull String newStatementText,
            @NonNls @NotNull PsiStatement statement)
            throws IncorrectOperationException{
        final PsiManager mgr = statement.getManager();
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(mgr.getProject());
        final PsiElementFactory factory = psiFacade.getElementFactory();
        final PsiStatement newStatement =
                factory.createStatementFromText(newStatementText, statement);
        final PsiElement insertedElement = statement.replace(newStatement);
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceStatementAndShorten(
            @NonNls @NotNull String newStatementText,
            @NonNls @NotNull PsiStatement statement)
            throws IncorrectOperationException{
        final PsiManager mgr = statement.getManager();
        final Project project = mgr.getProject();
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiElementFactory factory = psiFacade.getElementFactory();
        final PsiStatement newStatement =
                factory.createStatementFromText(newStatementText, statement);
        final PsiElement insertedElement = statement.replace(newStatement);
        final JavaCodeStyleManager codeStyleManager =
                JavaCodeStyleManager.getInstance(project);
        final PsiElement shortenedElement =
                codeStyleManager.shortenClassReferences(insertedElement);
        mgr.getCodeStyleManager().reformat(shortenedElement);
    }

    @Nullable PsiElement findMatchingElement(PsiFile file,
                                             Editor editor){
        final CaretModel caretModel = editor.getCaretModel();
        final int position = caretModel.getOffset();
        PsiElement element = file.findElementAt(position);
        return findMatchingElement(element);
    }

    @Nullable PsiElement findMatchingElement(@Nullable PsiElement element) {
    while(element != null){
        if(predicate.satisfiedBy(element)){
            return element;
        } else{
            element = element.getParent();
            if (element instanceof PsiFile) {
                break;
            }
        }
    }
    return null;
  }


  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
      while (element != null) {
        if (predicate.satisfiedBy(element)) {
          return true;
        }
        else {
          element = element.getParent();
          if (element instanceof PsiFile) {
            break;
          }
        }
      }
      return false;
    }

    public boolean startInWriteAction(){
        return true;
    }

    private static boolean isFileReadOnly(Project project, PsiFile file){
        final VirtualFile virtualFile = file.getVirtualFile();
        final ReadonlyStatusHandler readonlyStatusHandler =
                ReadonlyStatusHandler.getInstance(project);
        final ReadonlyStatusHandler.OperationStatus operationStatus =
                readonlyStatusHandler.ensureFilesWritable(virtualFile);
        return operationStatus.hasReadonlyFiles();
    }

    private String getPrefix() {
        final Class<? extends Intention> aClass = getClass();
        final String name = aClass.getSimpleName();
        final StringBuilder buffer = new StringBuilder(name.length() + 10);
        buffer.append(Character.toLowerCase(name.charAt(0)));
        for (int i = 1; i < name.length(); i++){
            final char c = name.charAt(i);
            if (Character.isUpperCase(c)){
                buffer.append('.');
                buffer.append(Character.toLowerCase(c));
            } else {
                buffer.append(c);
            }
        }
        return buffer.toString();
    }

    @NotNull
    public String getText() {
        //noinspection UnresolvedPropertyKey
        return IntentionPowerPackBundle.message(getPrefix() + ".name");
    }

    @NotNull
    public String getFamilyName() {
        //noinspection UnresolvedPropertyKey
        return IntentionPowerPackBundle.defaultableMessage(getPrefix() + ".family.name");
    }
}