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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.StdLanguages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;

/**
 * @author peter
 */
public class XmlBasicToClassNameDelegator extends CompletionContributor {

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, final CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    PsiFile file = position.getContainingFile();
    if (parameters.getCompletionType() != CompletionType.BASIC ||
        !JavaCompletionContributor.mayStartClassName(result) ||
        !file.getLanguage().isKindOf(StdLanguages.XML)) {
      return;
    }

    final boolean[] empty = {true};
    result.runRemainingContributors(parameters, new Consumer<CompletionResult>() {
      @Override
      public void consume(CompletionResult completionResult) {
        empty[0] = false;
        LookupElement lookupElement = completionResult.getLookupElement();
        lookupElement
          .putUserData(XmlCompletionContributor.WORD_COMPLETION_COMPATIBLE, Boolean.TRUE); //todo think of a less dirty interaction
        result.passResult(completionResult);
      }
    });

    if (!empty[0] && parameters.getInvocationCount() == 0) {
      result.restartCompletionWhenNothingMatches();
    }

    if (JavaClassReferenceCompletionContributor.findJavaClassReference(file, parameters.getOffset()) == null &&
        parameters.isExtendedCompletion()) {
      CompletionService.getCompletionService().getVariantsFromContributors(parameters.delegateToClassName(), null, new Consumer<CompletionResult>() {
        @Override
        public void consume(final CompletionResult completionResult) {
          LookupElement lookupElement = completionResult.getLookupElement();
          JavaPsiClassReferenceElement classElement = lookupElement.as(JavaPsiClassReferenceElement.CLASS_CONDITION_KEY);
          if (classElement != null) {
            classElement.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
          }
          lookupElement.putUserData(XmlCompletionContributor.WORD_COMPLETION_COMPATIBLE, Boolean.TRUE); //todo think of a less dirty interaction
          result.passResult(completionResult);
        }
      });
    }
  }

}
