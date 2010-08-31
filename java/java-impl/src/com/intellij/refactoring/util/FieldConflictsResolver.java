/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.util;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 *  Resolves conflicts with fields in a class, when new local variable is
 *  introduced in code block
 *  @author dsl
 */
public class FieldConflictsResolver {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.FieldConflictsResolver");
  private final PsiCodeBlock myScope;
  private final PsiField myField;
  private final List<PsiReferenceExpression> myReferenceExpressions;
  private PsiClass myQualifyingClass;

  public FieldConflictsResolver(String name, PsiCodeBlock scope) {
    myScope = scope;
    if (myScope == null) {
      myField = null;
      myReferenceExpressions = null;
      return;
    }
    JavaPsiFacade facade = JavaPsiFacade.getInstance(myScope.getProject());
    final PsiVariable oldVariable = facade.getResolveHelper().resolveAccessibleReferencedVariable(name, myScope);
    myField = oldVariable instanceof PsiField ? (PsiField) oldVariable : null;
    if (!(oldVariable instanceof PsiField)) {
      myReferenceExpressions = null;
      return;
    }
    myReferenceExpressions = new ArrayList<PsiReferenceExpression>();
    for (PsiReference reference : ReferencesSearch.search(myField, new LocalSearchScope(myScope), false)) {
      final PsiElement element = reference.getElement();
      if (element instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
        if (referenceExpression.getQualifierExpression() == null) {
          myReferenceExpressions.add(referenceExpression);
        }
      }
    }
    if (myField.hasModifierProperty(PsiModifier.STATIC)) {
      myQualifyingClass = myField.getContainingClass();
    }
  }

  public PsiExpression fixInitializer(PsiExpression initializer) {
    if (myField == null) return initializer;
    final PsiReferenceExpression[] replacedRef = {null};
    initializer.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        final PsiExpression qualifierExpression = expression.getQualifierExpression();
        if (qualifierExpression != null) {
          qualifierExpression.accept(this);
        }
        else {
          final PsiElement result = expression.resolve();
          if (expression.getManager().areElementsEquivalent(result, myField)) {
            try {
              replacedRef[0] = qualifyReference(expression, myField, myQualifyingClass);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }
    });
    if (!initializer.isValid()) return replacedRef[0];
    return initializer;
  }

  public void fix() throws IncorrectOperationException {
    if (myField == null) return;
    final PsiManager manager = myScope.getManager();
    for (PsiReferenceExpression referenceExpression : myReferenceExpressions) {
      if (!referenceExpression.isValid()) continue;
      final PsiElement newlyResolved = referenceExpression.resolve();
      if (!manager.areElementsEquivalent(newlyResolved, myField)) {
        qualifyReference(referenceExpression, myField, myQualifyingClass);
      }
    }
  }


  public static PsiReferenceExpression qualifyReference(PsiReferenceExpression referenceExpression,
                                                        final PsiMember member,
                                                        @Nullable final PsiClass qualifyingClass) throws IncorrectOperationException {
    PsiManager manager = referenceExpression.getManager();
    PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(referenceExpression, PsiMethodCallExpression.class, true);
    while ((methodCallExpression) != null) {
      if (HighlightUtil.isSuperOrThisMethodCall(methodCallExpression)) {
        return referenceExpression;
      }
      methodCallExpression = PsiTreeUtil.getParentOfType(methodCallExpression, PsiMethodCallExpression.class, true);
    }
    PsiReferenceExpression expressionFromText;
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    if (qualifyingClass == null) {
      PsiClass parentClass = PsiTreeUtil.getParentOfType(referenceExpression, PsiClass.class);
      final PsiClass containingClass = member.getContainingClass();
      if (parentClass != null && !InheritanceUtil.isInheritorOrSelf(parentClass, containingClass, true)) {
        while (parentClass != null && !InheritanceUtil.isInheritorOrSelf(parentClass, containingClass, true)) {
          parentClass = PsiTreeUtil.getParentOfType(parentClass, PsiClass.class, true);
        }
        LOG.assertTrue(parentClass != null);
        expressionFromText = (PsiReferenceExpression)factory.createExpressionFromText("A.this." + member.getName(), null);
        ((PsiThisExpression)expressionFromText.getQualifierExpression()).getQualifier().replace(factory.createClassReferenceElement(parentClass));
      }
      else {
        expressionFromText = (PsiReferenceExpression)factory.createExpressionFromText("this." + member.getName(), null);
      }
    }
    else {
      expressionFromText = (PsiReferenceExpression)factory.createExpressionFromText("A." + member.getName(), null);
      expressionFromText.setQualifierExpression(factory.createReferenceExpression(qualifyingClass));
    }
    CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
    expressionFromText = (PsiReferenceExpression)codeStyleManager.reformat(expressionFromText);
    return (PsiReferenceExpression)referenceExpression.replace(expressionFromText);
  }
}
