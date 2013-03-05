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
package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * User: anna
 */
public class PsiMethodReferenceUtil {
  public static ThreadLocal<Map<PsiMethodReferenceExpression, PsiType>> ourRefs = new ThreadLocal<Map<PsiMethodReferenceExpression, PsiType>>();

  public static final Logger LOG = Logger.getInstance("#" + PsiMethodReferenceUtil.class.getName());
  
  public static class QualifierResolveResult {
    private final PsiClass myContainingClass;
    private final PsiSubstitutor mySubstitutor;
    private final boolean myReferenceTypeQualified;

    public QualifierResolveResult(PsiClass containingClass, PsiSubstitutor substitutor, boolean referenceTypeQualified) {
      myContainingClass = containingClass;
      mySubstitutor = substitutor;
      myReferenceTypeQualified = referenceTypeQualified;
    }

    public PsiClass getContainingClass() {
      return myContainingClass;
    }

    public PsiSubstitutor getSubstitutor() {
      return mySubstitutor;
    }

    public boolean isReferenceTypeQualified() {
      return myReferenceTypeQualified;
    }
  }

  public static boolean isValidQualifier(PsiMethodReferenceExpression expression) {
    final PsiElement referenceNameElement = expression.getReferenceNameElement();
    if (referenceNameElement instanceof PsiKeyword) {
      final PsiElement qualifier = expression.getQualifier();
      if (qualifier instanceof PsiTypeElement) {
        return true;
      }
      if (qualifier instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifier).resolve() instanceof PsiClass) {
        return true;
      }
    }
    return false;
  }

  public static QualifierResolveResult getQualifierResolveResult(PsiMethodReferenceExpression methodReferenceExpression) {
    PsiClass containingClass = null;
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    final PsiExpression expression = methodReferenceExpression.getQualifierExpression();
    if (expression != null) {
      final PsiType expressionType = getExpandedType(expression.getType(), expression);
      PsiClassType.ClassResolveResult result = PsiUtil.resolveGenericsClassInType(expressionType);
      containingClass = result.getElement();
      if (containingClass != null) {
        substitutor = result.getSubstitutor();
      }
      if (containingClass == null && expression instanceof PsiReferenceExpression) {
        final JavaResolveResult resolveResult = ((PsiReferenceExpression)expression).advancedResolve(false);
        final PsiElement resolve = resolveResult.getElement();
        if (resolve instanceof PsiClass) {
          containingClass = (PsiClass)resolve;
          substitutor = resolveResult.getSubstitutor();
          return new QualifierResolveResult(containingClass, substitutor, true);
        }
      }
    }
    else {
      final PsiTypeElement typeElement = methodReferenceExpression.getQualifierType();
      if (typeElement != null) {
        PsiType type = getExpandedType(typeElement.getType(), typeElement);
        PsiClassType.ClassResolveResult result = PsiUtil.resolveGenericsClassInType(type);
        containingClass = result.getElement();
        if (containingClass != null) {
          substitutor = result.getSubstitutor();
        }
      }
    }
    return new QualifierResolveResult(containingClass, substitutor, false);
  }
  
  public static boolean isAcceptable(@Nullable final PsiMethodReferenceExpression methodReferenceExpression, PsiType left) {
    if (methodReferenceExpression == null) return false;
    if (left instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)left).getConjuncts()) {
        if (isAcceptable(methodReferenceExpression, conjunct)) return true;
      }
      return false;
    }
    Map<PsiMethodReferenceExpression, PsiType> map = ourRefs.get();
    if (map == null) {
      map = new HashMap<PsiMethodReferenceExpression, PsiType>();
      ourRefs.set(map);
    }

    final JavaResolveResult result;
    try {
      if (map.put(methodReferenceExpression, left) != null) {
        return false;
      }
      result = methodReferenceExpression.advancedResolve(false);
    }
    finally {
      map.remove(methodReferenceExpression);
    }

    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(left);
    final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
    if (method != null) {
      final QualifierResolveResult qualifierResolveResult = getQualifierResolveResult(methodReferenceExpression);
      final PsiElement resolve = result.getElement();
      if (resolve instanceof PsiMethod) {
        final MethodSignature signature1 = method.getSignature(LambdaUtil.getSubstitutor(method, resolveResult));
        PsiSubstitutor subst = PsiSubstitutor.EMPTY;
        subst = subst.putAll(qualifierResolveResult.getSubstitutor());
        subst = subst.putAll(result.getSubstitutor());
        final MethodSignature signature2 = ((PsiMethod)resolve).getSignature(subst);

        final PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(left);
        
        PsiType returnType = PsiTypesUtil.patchMethodGetClassReturnType(methodReferenceExpression, methodReferenceExpression,
                                                                        (PsiMethod)resolve, null,
                                                                        PsiUtil.getLanguageLevel(methodReferenceExpression));
        if (returnType == null) {
          returnType = ((PsiMethod)resolve).getReturnType();
        }
        PsiType methodReturnType = subst.substitute(returnType);
        if (interfaceReturnType != null && interfaceReturnType != PsiType.VOID) {
          if (methodReturnType == null) {
            methodReturnType = JavaPsiFacade.getElementFactory(methodReferenceExpression.getProject()).createType(((PsiMethod)resolve).getContainingClass(), subst);
          }
          if (!TypeConversionUtil.isAssignable(interfaceReturnType, methodReturnType, false)) return false;
        }
        if (areAcceptable(signature1, signature2, qualifierResolveResult.getContainingClass(), qualifierResolveResult.getSubstitutor(), ((PsiMethod)resolve).isVarArgs())) return true;
      } else if (resolve instanceof PsiClass) {
        final PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(left);
        if (interfaceReturnType != null) {
          if (interfaceReturnType == PsiType.VOID) return true;
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          if (resolve == JavaPsiFacade.getElementFactory(resolve.getProject()).getArrayClass(PsiUtil.getLanguageLevel(resolve))) {
            if (parameters.length != 1 || parameters[0].getType() != PsiType.INT) return false;
            final PsiTypeParameter[] typeParameters = ((PsiClass)resolve).getTypeParameters();
            if (typeParameters.length == 1) {
              final PsiType arrayComponentType = result.getSubstitutor().substitute(typeParameters[0]);
              return arrayComponentType != null && TypeConversionUtil.isAssignable(interfaceReturnType, arrayComponentType.createArrayType(), true);
            }
            return false;
          }
          final PsiClassType classType = JavaPsiFacade.getElementFactory(methodReferenceExpression.getProject()).createType((PsiClass)resolve, result.getSubstitutor());
          if (TypeConversionUtil.isAssignable(interfaceReturnType, classType, !((PsiClass)resolve).hasTypeParameters())) {
            if (parameters.length == 0) return true;
            if (parameters.length == 1) {
              if (isReceiverType(resolveResult.getSubstitutor().substitute(parameters[0].getType()), qualifierResolveResult.getContainingClass(), qualifierResolveResult.getSubstitutor())) return true;
            }
          }
        }
      }
    }
    return false;
  }

  private static boolean isReceiverType(@Nullable PsiClass aClass, @Nullable PsiClass containingClass) {
    return InheritanceUtil.isInheritorOrSelf(aClass, containingClass, true);
  }

  public static boolean isReceiverType(PsiType receiverType, @Nullable PsiClass containingClass, PsiSubstitutor psiSubstitutor) {
    boolean arrayType = receiverType instanceof PsiArrayType;
    if (containingClass != null) {
      receiverType = getExpandedType(receiverType, containingClass);
    }
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(GenericsUtil.eliminateWildcards(receiverType));
    final PsiClass receiverClass = resolveResult.getElement();
    if (receiverClass != null && isReceiverType(receiverClass, containingClass)) {
      LOG.assertTrue(containingClass != null);
      return arrayType ||
             resolveResult.getSubstitutor().equals(psiSubstitutor) ||
             emptyOrRaw(containingClass, psiSubstitutor) ||
             emptyOrRaw(receiverClass, resolveResult.getSubstitutor());
    } 
    return false;
  }

  private static boolean emptyOrRaw(PsiClass containingClass, PsiSubstitutor psiSubstitutor) {
    return PsiUtil.isRawSubstitutor(containingClass, psiSubstitutor) ||
           (!containingClass.hasTypeParameters() && psiSubstitutor.getSubstitutionMap().isEmpty());
  }

  public static boolean isReceiverType(PsiType functionalInterfaceType, PsiClass containingClass, @Nullable PsiMethod referencedMethod) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final MethodSignature function = LambdaUtil.getFunction(resolveResult.getElement());
    if (function != null) {
      final int interfaceMethodParamsLength = function.getParameterTypes().length;
      if (interfaceMethodParamsLength > 0) {
        final PsiType firstParamType = resolveResult.getSubstitutor().substitute(function.getParameterTypes()[0]);
        boolean isReceiver = isReceiverType(firstParamType,
                                            containingClass, PsiUtil.resolveGenericsClassInType(firstParamType).getSubstitutor());
        if (isReceiver) {
          if (referencedMethod == null){
            if (interfaceMethodParamsLength == 1) return true;
            return false;
          }
          if (referencedMethod.getParameterList().getParametersCount() != interfaceMethodParamsLength - 1) {
            return false;
          }
          return true;
        }
      }
    }
    return false;
  }

  public static boolean areAcceptable(MethodSignature signature1,
                                      MethodSignature signature2,
                                      PsiClass psiClass,
                                      PsiSubstitutor psiSubstitutor, 
                                      boolean isVarargs) {
    int offset = 0;
    final PsiType[] signatureParameterTypes1 = signature1.getParameterTypes();
    final PsiType[] signatureParameterTypes2 = signature2.getParameterTypes();
    if (signatureParameterTypes1.length != signatureParameterTypes2.length) {
      if (signatureParameterTypes1.length == signatureParameterTypes2.length + 1) {
        if (isReceiverType(signatureParameterTypes1[0], psiClass, psiSubstitutor)) {
          offset++;
        }
        else if (!isVarargs){
          return false;
        }
      }
      else if (!isVarargs) {
        return false;
      }
    }

    final int min = Math.min(signatureParameterTypes2.length, signatureParameterTypes1.length);
    for (int i = 0; i < min; i++) {
      final PsiType type1 = GenericsUtil.eliminateWildcards(psiSubstitutor.substitute(signatureParameterTypes1[offset + i]));
      if (isVarargs && i == min - 1) {
        if (!TypeConversionUtil.isAssignable(((PsiArrayType)signatureParameterTypes2[i]).getComponentType(), type1) && 
            !TypeConversionUtil.isAssignable(signatureParameterTypes2[i], type1)) {
          return false;
        }
      }
      else {
        if (!TypeConversionUtil.isAssignable(signatureParameterTypes2[i], type1)) {
          return false;
        }
      }
    }
    return true;
  }


  public static boolean onArrayType(PsiClass containingClass, MethodSignature signature) {
    if (signature.getParameterTypes().length == 1 && signature.getParameterTypes()[0] == PsiType.INT) {
      if (containingClass != null) {
        final Project project = containingClass.getProject();
        final LanguageLevel level = PsiUtil.getLanguageLevel(containingClass);
        return containingClass == JavaPsiFacade.getElementFactory(project).getArrayClass(level);
      }
    }
    return false;
  }

  private static PsiType getExpandedType(PsiType type, @NotNull PsiElement typeElement) {
    if (type instanceof PsiArrayType) {
      type = JavaPsiFacade.getElementFactory(typeElement.getProject()).getArrayClassType(((PsiArrayType)type).getComponentType(),
                                                                                         PsiUtil.getLanguageLevel(typeElement));
    }
    return type;
  }
}
