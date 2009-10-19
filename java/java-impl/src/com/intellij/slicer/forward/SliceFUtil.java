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
package com.intellij.slicer.forward;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.slicer.SliceManager;
import com.intellij.slicer.SliceUsage;
import com.intellij.slicer.SliceUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * @author cdr
 */
public class SliceFUtil {
  public static boolean processUsagesFlownFromThe(@NotNull PsiElement element, @NotNull final Processor<SliceUsage> processor, @NotNull final SliceUsage parent) {
    Pair<PsiElement, PsiSubstitutor> pair = getAssignmentTarget(element, parent);
    if (pair != null) {
      PsiElement target = pair.getFirst();
      final PsiSubstitutor substitutor = pair.getSecond();
      if (target instanceof PsiParameter) {
        PsiParameter parameter = (PsiParameter)target;
        PsiElement declarationScope = parameter.getDeclarationScope();
        if (declarationScope instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)declarationScope;
          final int parameterIndex = method.getParameterList().getParameterIndex(parameter);

          Processor<PsiMethod> myProcessor = new Processor<PsiMethod>() {
            public boolean process(PsiMethod override) {
              if (!parent.getScope().contains(override)) return true;
              final PsiSubstitutor superSubstitutor = method == override
                                                      ? substitutor
                                                      : MethodSignatureUtil.getSuperMethodSignatureSubstitutor(method.getSignature(substitutor),
                                                                                            override.getSignature(substitutor));

              PsiParameter[] parameters = override.getParameterList().getParameters();
              if (parameters.length <= parameterIndex) return true;
              PsiParameter actualParam = parameters[parameterIndex];

              SliceUsage usage = SliceUtil.createSliceUsage(actualParam, parent, superSubstitutor);
              return processor.process(usage);
            }
          };
          if (!myProcessor.process(method)) return false;
          return OverridingMethodsSearch.search(method, parent.getScope().toSearchScope(), true).forEach(myProcessor);
        }
      }

      SliceUsage usage = SliceUtil.createSliceUsage(target, parent, parent.getSubstitutor());
      return processor.process(usage);
    }

    if (element instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression)element;
      PsiElement resolved = ref.resolve();
      if (!(resolved instanceof PsiVariable)) return true;
      final PsiVariable variable = (PsiVariable)resolved;
      return processAssignedFrom(variable, ref, parent, processor);
    }
    if (element instanceof PsiVariable) {
      return processAssignedFrom(element, element, parent, processor);
    }
    if (element instanceof PsiMethod) {
      return processAssignedFrom(element, element, parent, processor);
    }
    return true;
  }

  private static boolean processAssignedFrom(final PsiElement from, final PsiElement context, final SliceUsage parent,
                                                 final Processor<SliceUsage> processor) {
    if (from instanceof PsiLocalVariable) {
      return searchReferencesAndProcessAssignmentTarget(from, context, parent, processor);
    }
    if (from instanceof PsiParameter) {
      PsiParameter parameter = (PsiParameter)from;
      PsiElement scope = parameter.getDeclarationScope();
      Collection<PsiParameter> parametersToAnalyze = new THashSet<PsiParameter>();
      if (scope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)scope;
        int index = method.getParameterList().getParameterIndex(parameter);

        Collection<PsiMethod> superMethods = new THashSet<PsiMethod>(Arrays.asList(method.findDeepestSuperMethods()));
        superMethods.add(method);
        for (Iterator<PsiMethod> iterator = superMethods.iterator(); iterator.hasNext(); ) {
          SliceManager.getInstance(method.getProject()).checkCanceled();
          PsiMethod superMethod = iterator.next();
          if (superMethod instanceof PsiCompiledElement) {
            iterator.remove();
          }
        }

        final THashSet<PsiMethod> implementors = new THashSet<PsiMethod>(superMethods);
        for (PsiMethod superMethod : superMethods) {
          SliceManager.getInstance(method.getProject()).checkCanceled();
          if (!OverridingMethodsSearch.search(superMethod, parent.getScope().toSearchScope(), true).forEach(new Processor<PsiMethod>() {
            public boolean process(PsiMethod sub) {
              SliceManager.getInstance(method.getProject()).checkCanceled();
              implementors.add(sub);
              return true;
            }
          })) return false;
        }
        for (PsiMethod implementor : implementors) {
          SliceManager.getInstance(method.getProject()).checkCanceled();
          PsiParameter[] parameters = implementor.getParameterList().getParameters();
          if (index != -1 && index < parameters.length) {
            parametersToAnalyze.add(parameters[index]);
          }
        }
      }
      else {
        parametersToAnalyze.add(parameter);
      }
      for (final PsiParameter psiParameter : parametersToAnalyze) {
        SliceManager.getInstance(from.getProject()).checkCanceled();

        if (!searchReferencesAndProcessAssignmentTarget(psiParameter, null, parent, processor)) return false;
      }
      return true;
    }
    if (from instanceof PsiField) {
      return searchReferencesAndProcessAssignmentTarget(from, null, parent, processor);
    }

    if (from instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)from;

      Collection<PsiMethod> superMethods = new THashSet<PsiMethod>(Arrays.asList(method.findDeepestSuperMethods()));
      superMethods.add(method);
      final Set<PsiReference> processed = new THashSet<PsiReference>(); //usages of super method and overridden method can overlap
      for (final PsiMethod containingMethod : superMethods) {
        if (!MethodReferencesSearch.search(containingMethod, parent.getScope().toSearchScope(), true).forEach(new Processor<PsiReference>() {
            public boolean process(final PsiReference reference) {
              SliceManager.getInstance(from.getProject()).checkCanceled();
              synchronized (processed) {
                if (!processed.add(reference)) return true;
              }
              PsiElement element = reference.getElement().getParent();

              return processAssignmentTarget(element, parent, processor);
            }
          })) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean searchReferencesAndProcessAssignmentTarget(@NotNull PsiElement element, @Nullable final PsiElement context, final SliceUsage parent,
                                                                    final Processor<SliceUsage> processor) {
    return ReferencesSearch.search(element).forEach(new Processor<PsiReference>() {
      public boolean process(PsiReference reference) {
        PsiElement element = reference.getElement();
        if (context != null && element.getTextOffset() < context.getTextOffset()) return true;
        return processAssignmentTarget(element, parent, processor);
      }
    });
  }

  private static boolean processAssignmentTarget(PsiElement element, final SliceUsage parent, final Processor<SliceUsage> processor) {
    Pair<PsiElement, PsiSubstitutor> pair = getAssignmentTarget(element, parent);
    if (pair != null) {
      SliceUsage usage = SliceUtil.createSliceUsage(element, parent, pair.getSecond());
      return processor.process(usage);
    }
    return true;
  }

  private static Pair<PsiElement,PsiSubstitutor> getAssignmentTarget(PsiElement element, SliceUsage parentUsage) {
    element = complexify(element);
    PsiElement target = null;
    PsiSubstitutor substitutor = parentUsage.getSubstitutor();
    //assignment
    PsiElement parent = element.getParent();
    if (parent instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
      if (element.equals(assignment.getRExpression())) {
        PsiElement left = assignment.getLExpression();
        if (left instanceof PsiReferenceExpression) {
          JavaResolveResult result = ((PsiReferenceExpression)left).advancedResolve(false);
          target = result.getElement();
          substitutor = result.getSubstitutor();
        }
      }
    }
    else if (parent instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)parent;

      PsiElement initializer = variable.getInitializer();
      if (element.equals(initializer)) {
        target = variable;
      }
    }
    //method call
    else if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCallExpression) {
      PsiExpression[] expressions = ((PsiExpressionList)parent).getExpressions();
      int index = ArrayUtil.find(expressions, element);
      PsiCallExpression methodCall = (PsiCallExpression)parent.getParent();
      JavaResolveResult result = methodCall.resolveMethodGenerics();
      PsiMethod method = (PsiMethod)result.getElement();
      if (index != -1 && method != null) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (index < parameters.length) {
          target = parameters[index];
          substitutor = result.getSubstitutor();
        }
      }
    }
    else if (parent instanceof PsiReturnStatement) {
      PsiReturnStatement statement = (PsiReturnStatement)parent;
      if (element.equals(statement.getReturnValue())) {
        target = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
      }
    }

    return target == null ? null : Pair.create(target, substitutor);
  }

  public static PsiElement complexify(@NotNull PsiElement element) {
    PsiElement parent = element.getParent();
    if (parent instanceof PsiParenthesizedExpression && element.equals(((PsiParenthesizedExpression)parent).getExpression())) {
      return complexify(parent);
    }
    if (parent instanceof PsiTypeCastExpression && element.equals(((PsiTypeCastExpression)parent).getOperand())) {
      return complexify(parent);
    }
    return element;
  }
}
