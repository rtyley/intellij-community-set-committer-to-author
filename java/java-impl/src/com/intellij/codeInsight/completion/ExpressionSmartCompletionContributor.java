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

import com.intellij.openapi.util.Pair;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public abstract class ExpressionSmartCompletionContributor extends AbstractCompletionContributor<JavaSmartCompletionParameters> {

  private final List<Pair<ElementPattern<? extends PsiElement>, CompletionProvider<JavaSmartCompletionParameters>>> myList =
      new ArrayList<Pair<ElementPattern<? extends PsiElement>, CompletionProvider<JavaSmartCompletionParameters>>>();

  public final void extend(final ElementPattern<? extends PsiElement> place, CompletionProvider<JavaSmartCompletionParameters> provider) {
    myList.add(new Pair<ElementPattern<? extends PsiElement>, CompletionProvider<JavaSmartCompletionParameters>>(place, provider));
  }

  public void fillCompletionVariants(final JavaSmartCompletionParameters parameters, CompletionResultSet result) {
    for (final Pair<ElementPattern<? extends PsiElement>, CompletionProvider<JavaSmartCompletionParameters>> pair : myList) {
      final ProcessingContext context = new ProcessingContext();
      if (pair.first.accepts(parameters.getPosition(), context)) {
        pair.second.addCompletionVariants(parameters, context, result);
      }
    }
  }

}
