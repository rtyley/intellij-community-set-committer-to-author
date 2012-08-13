/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PsiLambdaExpressionImpl extends ExpressionPsiElement implements PsiLambdaExpression {
  public static RecursionGuard ourGuard = RecursionManager.createGuard("Lambda");

  public PsiLambdaExpressionImpl() {
    super(JavaElementType.LAMBDA_EXPRESSION);
  }

  @NotNull
  @Override
  public PsiParameterList getParameterList() {
    return PsiTreeUtil.getRequiredChildOfType(this, PsiParameterList.class);
  }

  @Override
  public PsiElement getBody() {
    final PsiElement element = getLastChild();
    return element instanceof PsiExpression || element instanceof PsiCodeBlock ? element : null;
  }

  @Override
  public List<PsiExpression> getReturnExpressions() {
    final PsiElement body = getBody();
    if (body instanceof PsiExpression) {
      //if (((PsiExpression)body).getType() != PsiType.VOID) return Collections.emptyList();
      return Collections.singletonList((PsiExpression)body);
    }
    final List<PsiExpression> result = new ArrayList<PsiExpression>();
    if (body != null) {
      body.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitReturnStatement(PsiReturnStatement statement) {
          final PsiExpression returnValue = statement.getReturnValue();
          if (returnValue != null) {
            result.add(returnValue);
          }
        }

        @Override
        public void visitClass(PsiClass aClass) {
        }
      });
    }
    return result;
  }

  @Nullable
  @Override
  public PsiType getFunctionalInterfaceType() {
    PsiElement parent = getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      parent = parent.getParent();
    }
    PsiType type = null;
    if (parent instanceof PsiTypeCastExpression) {
      type = ((PsiTypeCastExpression)parent).getType();
    }
    else if (parent instanceof PsiVariable) {
      type = ((PsiVariable)parent).getType();
    }
    else if (parent instanceof PsiAssignmentExpression) {
      final PsiExpression lExpression = ((PsiAssignmentExpression)parent).getLExpression();
      type = lExpression.getType();
    }
    else if (parent instanceof PsiExpressionList) {
      final PsiExpressionList expressionList = (PsiExpressionList)parent;
      int lambdaIdx = LambdaUtil.getLambdaIdx(expressionList, this);
      
      if (lambdaIdx > -1) {
        final PsiElement gParent = expressionList.getParent();
        if (gParent instanceof PsiMethodCallExpression) {
          final PsiMethodCallExpression contextCall = (PsiMethodCallExpression)gParent;
          final JavaResolveResult resolveResult = contextCall.resolveMethodGenerics();
          final PsiElement resolve = resolveResult.getElement();
          if (resolve instanceof PsiMethod) {
            final PsiParameter[] parameters = ((PsiMethod)resolve).getParameterList().getParameters();
            if (lambdaIdx < parameters.length) {
              type = parameters[lambdaIdx].getType();
              final PsiType psiType = type;
              type = ourGuard.doPreventingRecursion(this, true, new Computable<PsiType>() {
                @Override
                public PsiType compute() {
                  return resolveResult.getSubstitutor().substitute(psiType);
                }
              });
            }
          }
        }
      }
    }
    else if (parent instanceof PsiReturnStatement) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
      if (method != null) {
        type = method.getReturnType();
      }
    }
    else if (parent instanceof PsiLambdaExpression) {
      final PsiType parentInterfaceType = ((PsiLambdaExpression)parent).getFunctionalInterfaceType();
      if (parentInterfaceType != null) {
        type = LambdaUtil.getFunctionalInterfaceReturnType(parentInterfaceType);
      }
    }
    return type;
  }

  @Override
  public PsiType getType() {
    return new PsiLambdaExpressionType(this);
  }

  @Override
  public void accept(@NotNull final PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitLambdaExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    return PsiImplUtil.processDeclarationsInLambda(this, processor, state, lastParent, place);
  }

  @Override
  public String toString() {
    return "PsiLambdaExpression:" + getText();
  }
}
