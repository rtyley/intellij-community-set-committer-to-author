/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Consumer;

/**
 * @author peter
 */
public class JavaBasicToClassNameDelegator extends CompletionContributor {

  @Override
  public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.BASIC ||
        parameters.getInvocationCount() != 1 ||
        !JavaCompletionContributor.mayStartClassName(result, false) ||
        !JavaCompletionContributor.isClassNamePossible(parameters.getPosition())) {
      return;
    }

    final Ref<Boolean> empty = Ref.create(true);
    result.runRemainingContributors(parameters, new Consumer<CompletionResult>() {
      public void consume(final CompletionResult lookupElement) {
        empty.set(false);
        result.passResult(lookupElement);
      }
    });

    if (empty.get()) {
      JavaClassNameCompletionContributor.addAllClasses(parameters, JavaCompletionSorting.addJavaSorting(parameters, result), new Consumer<LookupElement>() {
        @Override
        public void consume(LookupElement element) {
          JavaPsiClassReferenceElement classElement = element.as(JavaPsiClassReferenceElement.CLASS_CONDITION_KEY);
          if (classElement != null) {
            classElement.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
          }
          result.addElement(element);
        }
      });
    }
  }
}
