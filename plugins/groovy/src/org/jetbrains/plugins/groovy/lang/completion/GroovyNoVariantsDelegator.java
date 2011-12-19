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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.util.Consumer;

/**
 * @author peter
 */
public class GroovyNoVariantsDelegator extends NoVariantsDelegator {

  @Override
  protected void delegate(CompletionParameters parameters, CompletionResultSet result, Consumer<CompletionResult> passResult) {
    if (parameters.getCompletionType() == CompletionType.BASIC &&
        parameters.getInvocationCount() <= 1 &&
        JavaCompletionContributor.mayStartClassName(result, false) &&
        GroovyCompletionContributor.isClassNamePossible(parameters.getPosition())) {
      final ClassByNameMerger merger = new ClassByNameMerger(parameters.getInvocationCount() == 0, result);

      GroovyCompletionContributor.addAllClasses(parameters, result,
                                                       new Consumer<LookupElement>() {
        @Override
        public void consume(LookupElement element) {
          JavaPsiClassReferenceElement classElement = element.as(JavaPsiClassReferenceElement.CLASS_CONDITION_KEY);
          if (classElement != null) {
            classElement.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
          }
          merger.consume(classElement);
        }
      }, new InheritorsHolder(parameters.getPosition(), result));

      merger.finishedClassProcessing();

    }
  }
}
