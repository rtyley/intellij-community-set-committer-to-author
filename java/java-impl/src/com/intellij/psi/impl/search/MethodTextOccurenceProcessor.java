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
package com.intellij.psi.impl.search;

import com.intellij.psi.*;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class MethodTextOccurenceProcessor implements TextOccurenceProcessor {
  private final PsiMethod[] myMethods;
  private final Processor<PsiReference> myConsumer;
  private final PsiClass myContainingClass;
  private final boolean myStrictSignatureSearch;

  public MethodTextOccurenceProcessor(final Processor<PsiReference> consumer, @NotNull final PsiClass aClass, final boolean strictSignatureSearch,
                                      final PsiMethod... methods) {
    myMethods = methods;
    myConsumer = consumer;
    myContainingClass = aClass;
    myStrictSignatureSearch = strictSignatureSearch;
  }

  public boolean execute(PsiElement element, int offsetInElement) {
    final PsiReference[] refs = element.getReferences();
    for (PsiReference ref : refs) {
      if (ref.getRangeInElement().contains(offsetInElement)) {
        for (PsiMethod method : myMethods) {
          if (!method.isValid()) {
            continue;
          }

          if (ref instanceof ResolvingHint && !((ResolvingHint)ref).canResolveTo(method)) {
            return true;
          }
          if (ref.isReferenceTo(method)) {
            return myConsumer.process(ref);
          }
          PsiElement refElement = ref.resolve();

          if (refElement instanceof PsiMethod) {
            PsiMethod refMethod = (PsiMethod)refElement;
            PsiClass refMethodClass = refMethod.getContainingClass();
            if (refMethodClass == null) continue;

            if (!refMethod.hasModifierProperty(PsiModifier.STATIC)) {
              PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(myContainingClass, refMethodClass, PsiSubstitutor.EMPTY);
              if (substitutor != null) {
                MethodSignature superSignature = method.getSignature(substitutor);
                MethodSignature refSignature = refMethod.getSignature(PsiSubstitutor.EMPTY);

                if (MethodSignatureUtil.isSubsignature(superSignature, refSignature)) {
                  if (!myConsumer.process(ref)) return false;
                }
              }
            }

            if (!myStrictSignatureSearch) {
              PsiManager manager = method.getManager();
              if (manager.areElementsEquivalent(refMethodClass, myContainingClass)) {
                if (!myConsumer.process(ref)) return false;
              }
            }
          }
        }
      }
    }

    return true;
  }
}
