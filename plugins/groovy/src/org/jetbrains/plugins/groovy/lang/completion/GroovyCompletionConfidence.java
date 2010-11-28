/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.psi.PsiElement;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.gpp.GppTypeConverter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * @author peter
 */
public class GroovyCompletionConfidence extends CompletionConfidence {
  @NotNull
  @Override
  public ThreeState shouldFocusLookup(@NotNull CompletionParameters parameters) {
    final PsiElement position = parameters.getPosition();

    if (!GppTypeConverter.hasTypedContext(position)) {
      return ThreeState.NO;
    }

    if (position.getParent() instanceof GrReferenceExpression) {
      final GrExpression expression = ((GrReferenceExpression)position.getParent()).getQualifierExpression();
      if (expression == null) {
        return ThreeState.YES;
      }
      if (expression.getType() == null) {
        return ThreeState.NO;
      }
      return ThreeState.YES;
    }
    return ThreeState.UNSURE;
  }
}
