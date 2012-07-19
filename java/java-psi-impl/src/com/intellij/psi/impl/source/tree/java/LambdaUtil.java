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

import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: anna
 * Date: 7/17/12
 */
public class LambdaUtil {
  @Nullable
  public static String checkInterfaceFunctional(@NotNull PsiClass psiClass) {
    final List<MethodSignature> signatures = findFunctionCandidates(psiClass);
    if (signatures == null) return "Target type of a lambda conversion must be an interface";
    if (signatures.isEmpty()) return "No target method found";
    return signatures.size() == 1 ? null : "Multiple non-overriding abstract methods found";
  }

  private static boolean overridesPublicObjectMethod(PsiMethod psiMethod) {
    boolean overrideObject = false;
    for (PsiMethod superMethod : psiMethod.findDeepestSuperMethods()) {
      final PsiClass containingClass = superMethod.getContainingClass();
      if (containingClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
        if (superMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
          overrideObject = true;
          break;
        }
      }
    }
    return overrideObject;
  }

  private static MethodSignature getMethodSignature(PsiMethod method, PsiClass psiClass, PsiClass containingClass) {
    final MethodSignature methodSignature;
    if (containingClass != null && containingClass != psiClass) {
      methodSignature = method.getSignature(TypeConversionUtil.getSuperClassSubstitutor(containingClass, psiClass, PsiSubstitutor.EMPTY));
    }
    else {
      methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
    }
    return methodSignature;
  }

  @Nullable
  private static List<MethodSignature> hasSubsignature(List<MethodSignature> signatures) {
    for (MethodSignature signature : signatures) {
      boolean subsignature = true;
      for (MethodSignature methodSignature : signatures) {
        if (!signature.equals(methodSignature)) {
          if (!MethodSignatureUtil.isSubsignature(signature, methodSignature)) {
            subsignature = false;
            break;
          }
        }
      }
      if (subsignature) return Collections.singletonList(signature);
    }
    return signatures;
  }

  @Nullable
  private static List<MethodSignature> findFunctionCandidates(PsiClass psiClass) {
    if (psiClass.isInterface()) {
      final List<MethodSignature> methods = new ArrayList<MethodSignature>();
      final PsiMethod[] psiClassMethods = psiClass.getAllMethods();
      for (PsiMethod psiMethod : psiClassMethods) {
        if (!psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) continue;
        final PsiClass methodContainingClass = psiMethod.getContainingClass();
        if (!overridesPublicObjectMethod(psiMethod)) {
          methods.add(getMethodSignature(psiMethod, psiClass, methodContainingClass));
        }
      }

      return hasSubsignature(methods);
    }
    return null;
  }

  @Nullable
  public static MethodSignature getFunction(PsiClass psiClass) {
    if (psiClass == null) return null;
    final List<MethodSignature> functions = findFunctionCandidates(psiClass);
    if (functions != null && functions.size() == 1) {
      return functions.get(0);
    }
    return null;
  }
  
  public static PsiType getLambdaParameterType(PsiParameter param) {

    final PsiElement paramParent = param.getParent();
    if (paramParent instanceof PsiParameterList) {
      final int parameterIndex = ((PsiParameterList)paramParent).getParameterIndex(param);
      if (parameterIndex > -1) {
        final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(param, PsiLambdaExpression.class);
        final PsiClassType.ClassResolveResult resolveResult = getFunctionInterfaceType(lambdaExpression);
        if (resolveResult != null) {
          final MethodSignature methodSignature = getFunction(resolveResult.getElement());
          if (methodSignature != null) {
            final PsiType[] types = methodSignature.getParameterTypes();
            if (parameterIndex < types.length) {
              return resolveResult.getSubstitutor().substitute(types[parameterIndex]);
            }
          }
        }
      }
    }
    return new PsiLambdaParameterType(param);
  }

  @Nullable
  public static PsiClassType.ClassResolveResult getFunctionInterfaceType(@Nullable PsiLambdaExpression lambdaExpression) {
    if (lambdaExpression != null) {
      final PsiElement parent = lambdaExpression.getParent();
      PsiType type = null;
      if (parent instanceof PsiTypeCastExpression) {
        type = ((PsiTypeCastExpression)parent).getType();
      }
      else if (parent instanceof PsiVariable) {
        type = ((PsiVariable)parent).getType();
      }
      else if (parent instanceof PsiExpressionList) {
        final PsiExpressionList expressionList = (PsiExpressionList)parent;
        final int lambdaIdx = ArrayUtil.find(expressionList.getExpressions(), lambdaExpression);
        if (lambdaIdx > -1) {
          final PsiElement gParent = expressionList.getParent();
          if (gParent instanceof PsiMethodCallExpression) {
            final JavaResolveResult resolveResult = ((PsiMethodCallExpression)gParent).resolveMethodGenerics();
            final PsiElement resolve = resolveResult.getElement();
            if (resolve instanceof PsiMethod) {
              final PsiParameter[] parameters = ((PsiMethod)resolve).getParameterList().getParameters();
              if (lambdaIdx < parameters.length) {
                type = resolveResult.getSubstitutor().substitute(parameters[lambdaIdx].getType());
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

      if (type instanceof PsiClassType) {
        return ((PsiClassType)type).resolveGenerics();
      }
    }
    return null;
  }
}
