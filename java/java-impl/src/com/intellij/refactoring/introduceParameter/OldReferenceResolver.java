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
package com.intellij.refactoring.introduceParameter;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 *         Date: Apr 29, 2009 2:03:38 PM
 */
public class OldReferenceResolver {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceParameter.OldReferenceResolver");

  private final PsiCall myContext;
  private final PsiExpression myExpr;
  private final HashMap<PsiExpression, String> myTempVars;
  private final PsiExpression myInstanceRef;
  private final PsiExpression[] myActualArgs;
  private final PsiMethod myMethodToReplaceIn;
  private final Project myProject;
  private final PsiManager myManager;
  private final int myReplaceFieldsWithGetters;
  private final PsiElement myParameterInitializer;

  public OldReferenceResolver(PsiCall context,
                              PsiExpression expr,
                              PsiMethod methodToReplaceIn,
                              int replaceFieldsWithGetters,
                              PsiElement parameterInitializer) throws IncorrectOperationException {
    myContext = context;
    myExpr = expr;
    myReplaceFieldsWithGetters = replaceFieldsWithGetters;
    myParameterInitializer = parameterInitializer;
    myTempVars = new HashMap<PsiExpression, String>();
    myActualArgs = myContext.getArgumentList().getExpressions();
    myMethodToReplaceIn = methodToReplaceIn;
    myProject = myContext.getProject();
    myManager = myContext.getManager();

    PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    PsiExpression instanceRef;
    if (myContext instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)myContext;
      final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
      instanceRef = methodExpression.getQualifierExpression();
      if (instanceRef == null) {
        final PsiClass thisResolveClass = RefactoringUtil.getThisResolveClass(methodExpression);
        if (thisResolveClass != null &&
            !(thisResolveClass instanceof PsiAnonymousClass) &&
            !thisResolveClass.equals(PsiTreeUtil.getParentOfType(methodExpression, PsiClass.class))) {
          //Qualified this needed
          instanceRef = factory.createExpressionFromText(thisResolveClass.getName() + ".this", null);
        }
      }
    }
    else {
      instanceRef = null;
    }
    myInstanceRef = instanceRef;
  }

  public void resolve() throws IncorrectOperationException {
    resolveOldReferences(myExpr, myParameterInitializer);

    Set<Map.Entry<PsiExpression, String>> mappingsSet = myTempVars.entrySet();

    PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();

    for (Map.Entry<PsiExpression, String> entry : mappingsSet) {
      PsiExpression oldRef = entry.getKey();
      PsiElement newRef = factory.createExpressionFromText(entry.getValue(), null);
      oldRef.replace(newRef);
    }
  }


  private void resolveOldReferences(PsiElement expr, PsiElement oldExpr) throws IncorrectOperationException {
    if (expr == null || !expr.isValid() || oldExpr == null) return;
    PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    PsiElement newExpr = expr;  // references continue being resolved in the children of newExpr

    if (oldExpr instanceof PsiReferenceExpression) {
      final PsiReferenceExpression oldRef = (PsiReferenceExpression)oldExpr;
      final JavaResolveResult adv = oldRef.advancedResolve(false);
      final PsiElement scope = getClassContainingResolve(adv);
      final PsiClass clss = PsiTreeUtil.getParentOfType(oldExpr, PsiClass.class);
      if (clss != null && scope != null ) {

        final PsiElement subj = adv.getElement();


        // Parameters
        if (subj instanceof PsiParameter) {
          PsiParameterList parameterList = myMethodToReplaceIn.getParameterList();

          if (subj.getParent() != parameterList) return;
          int index = parameterList.getParameterIndex((PsiParameter)subj);
          if (index < 0) return;
          if (index < myActualArgs.length) {
            PsiExpression actualArg = myActualArgs[index];
            int copyingSafetyLevel = RefactoringUtil.verifySafeCopyExpression(actualArg);
            if (copyingSafetyLevel == RefactoringUtil.EXPR_COPY_PROHIBITED) {
              actualArg = factory.createExpressionFromText(getTempVar(actualArg), null);
            }
            newExpr = newExpr.replace(actualArg);
          }
        }
        // "naked" field and methods  (should become qualified)
        else if ((subj instanceof PsiField || subj instanceof PsiMethod) && oldRef.getQualifierExpression() == null && PsiTreeUtil.isAncestor(clss, scope, false)) {

          boolean isStatic = subj instanceof PsiField && ((PsiField)subj).hasModifierProperty(PsiModifier.STATIC) ||
                             subj instanceof PsiMethod && ((PsiMethod)subj).hasModifierProperty(PsiModifier.STATIC);

          if (myInstanceRef != null && !isStatic) {
            String name = ((PsiNamedElement)subj).getName();
            PsiReferenceExpression newRef = (PsiReferenceExpression)factory.createExpressionFromText("a." + name, null);
            newRef = (PsiReferenceExpression)CodeStyleManager.getInstance(myProject).reformat(newRef);

            PsiExpression instanceRef = getInstanceRef(factory);

            newRef.getQualifierExpression().replace(instanceRef);
            newRef = (PsiReferenceExpression)newExpr.replace(newRef);
            newExpr = newRef.getReferenceNameElement();
          }
        }

        if (subj instanceof PsiField && PsiTreeUtil.isAncestor(scope, clss, false)) {
          // probably replacing field with a getter
          if (myReplaceFieldsWithGetters != IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE) {
            if (myReplaceFieldsWithGetters == IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL ||
                myReplaceFieldsWithGetters == IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE &&
                !JavaPsiFacade.getInstance(myProject).getResolveHelper().isAccessible((PsiMember)subj, newExpr, null)) {
              newExpr = replaceFieldWithGetter(newExpr, (PsiField)subj, oldRef.getQualifierExpression() == null && !((PsiField)subj).hasModifierProperty(PsiModifier.STATIC));
            }
          }
        }
      }
    }
    else if (oldExpr instanceof PsiThisExpression &&
             (((PsiThisExpression)oldExpr).getQualifier() == null ||
              myManager
                .areElementsEquivalent(((PsiThisExpression)oldExpr).getQualifier().resolve(), myMethodToReplaceIn.getContainingClass()))) {
      if (myInstanceRef != null) {
        newExpr.replace(getInstanceRef(factory));
      }
      return;
    }
    else if (oldExpr instanceof PsiSuperExpression && ((PsiSuperExpression)oldExpr).getQualifier() == null) {
      if (myInstanceRef != null) {
        newExpr.replace(getInstanceRef(factory));
      }
      return;
    }

    PsiElement[] oldChildren = oldExpr.getChildren();
    PsiElement[] newChildren = newExpr.getChildren();

    if (oldChildren.length == newChildren.length) {
      for (int i = 0; i < oldChildren.length; i++) {
        resolveOldReferences(newChildren[i], oldChildren[i]);
      }
    }
  }

  private PsiExpression getInstanceRef(PsiElementFactory factory) throws IncorrectOperationException {
    int copyingSafetyLevel = RefactoringUtil.verifySafeCopyExpression(myInstanceRef);

    PsiExpression instanceRef = myInstanceRef;
    if (copyingSafetyLevel == RefactoringUtil.EXPR_COPY_PROHIBITED) {
      instanceRef = factory.createExpressionFromText(getTempVar(myInstanceRef), null);
    }
    return instanceRef;
  }

  private String getTempVar(PsiExpression expr) throws IncorrectOperationException {
    String id = myTempVars.get(expr);
    if (id != null) {
      return id;
    }
    else {
      id = RefactoringUtil.createTempVar(expr, myContext, true);
      myTempVars.put(expr, id);
      return id;
    }
  }

  private PsiElement replaceFieldWithGetter(PsiElement expr, PsiField psiField, boolean qualify) throws IncorrectOperationException {
    if (RefactoringUtil.isAssignmentLHS(expr)) {
      // todo: warning
      return expr;
    }
    PsiElement newExpr = expr;

    PsiMethod getterPrototype = PropertyUtil.generateGetterPrototype(psiField);

    PsiMethod getter = psiField.getContainingClass().findMethodBySignature(getterPrototype, true);

    if (getter != null) {

      if (JavaPsiFacade.getInstance(psiField.getProject()).getResolveHelper().isAccessible(getter, newExpr, null)) {
        PsiElementFactory factory = JavaPsiFacade.getInstance(newExpr.getProject()).getElementFactory();
        String id = getter.getName();
        String qualifier = null;
        if (newExpr instanceof PsiReferenceExpression) {
          if (qualify) {
            qualifier = getInstanceRef(factory).getText();
          } else {
            final PsiExpression qualifierExpression = ((PsiReferenceExpression)newExpr).getQualifierExpression();
            if (qualifierExpression != null) {
              qualifier = qualifierExpression.getText();
            }
          }
        }
        PsiMethodCallExpression getterCall =
          (PsiMethodCallExpression)factory.createExpressionFromText((qualifier != null ? qualifier + "." : "") + id + "()", null);
        getterCall = (PsiMethodCallExpression)CodeStyleManager.getInstance(myProject).reformat(getterCall);
        if (newExpr.getParent() != null) {
          newExpr = newExpr.replace(getterCall);
        }
        else {
          newExpr = getterCall;
        }
      }
      else {
        // todo: warning
      }
    }

    return newExpr;
  }

  @Nullable
  private static PsiElement getClassContainingResolve(final JavaResolveResult result) {
    final PsiElement elem = result.getElement();
    if (elem != null) {
      if (elem instanceof PsiLocalVariable || elem instanceof PsiParameter) {
        return PsiTreeUtil.getParentOfType(elem, PsiClass.class);
      }
      else {
        return result.getCurrentFileResolveScope();
      }
    }
    return null;
  }
}
