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
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author peter
 */
public class GroovyCompletionConfidence extends CompletionConfidence {

  private static boolean isPossibleClosureParameter(GrReferenceExpression ref) {
    return PsiJavaPatterns.psiElement().withParent(GrClosableBlock.class).afterLeaf("{").accepts(ref) || GroovyCompletionContributor.isInPossibleClosureParameter(ref);
  }

  @NotNull
  @Override
  public ThreeState shouldFocusLookup(@NotNull CompletionParameters parameters) {
    final PsiElement position = parameters.getPosition();

    if (position.getParent() instanceof GrReferenceExpression) {
      final GrReferenceExpression ref = (GrReferenceExpression)position.getParent();
      final GrExpression expression = ref.getQualifierExpression();
      if (expression == null) {
        if (isPossibleClosureParameter(ref)) return ThreeState.NO;

        GrExpression runtimeQualifier = PsiImplUtil.getRuntimeQualifier(ref);
        if (runtimeQualifier != null && runtimeQualifier.getType() == null) {
          return ThreeState.NO;
        }

        return ThreeState.YES;
      }

      if (expression.getType() == null) {
        return ThreeState.NO;
      }
      return ThreeState.YES;
    }
    return ThreeState.UNSURE;
  }

  @NotNull
  @Override
  public ThreeState shouldSkipAutopopup(@Nullable PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    if (PsiUtil.isLeafElementOfType(contextElement, TokenSets.STRING_LITERALS)) {
      @SuppressWarnings("ConstantConditions")
      PsiElement parent = contextElement.getParent();
      if (parent != null) {
        for (PsiReference reference : parent.getReferences()) {
          if (!reference.isSoft() && reference.getRangeInElement().shiftRight(parent.getTextOffset()).containsOffset(offset)) {
            return ThreeState.NO;
          }
        }
      }

      return ThreeState.YES;
    }

    return ThreeState.UNSURE;
  }
}
