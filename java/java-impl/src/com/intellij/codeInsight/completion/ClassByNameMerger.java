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

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.util.Consumer;

/**
* @author peter
*/
public class ClassByNameMerger implements Consumer<LookupElement> {
  private int number = 0;
  private LookupElement lastElement;
  private final boolean myShouldMerge;
  private final CompletionResultSet myResult;

  public ClassByNameMerger(CompletionParameters parameters, CompletionResultSet result) {
    myShouldMerge = parameters.getInvocationCount() == 0 && CodeInsightSettings.getInstance().SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS;
    myResult = result;
  }

  @Override
  public void consume(LookupElement element) {
    if (!myShouldMerge) {
      myResult.addElement(element);
      return;
    }

    if (lastElement != null) {
      if (lastElement.getLookupString().equals(element.getLookupString())) {
        number++;
        lastElement = LookupElementBuilder.create(element.getLookupString()).withTailText(" (" + number + " variants...)", true);
        return;
      }

      myResult.addElement(lastElement);
    }
    lastElement = element;
    number = 1;
  }

  public void finishedClassProcessing() {
    if (lastElement != null) {
      myResult.addElement(lastElement);
    }
  }
}
