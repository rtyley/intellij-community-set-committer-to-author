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

package com.intellij.lang.impl;

import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PsiBuilderFactoryImpl extends PsiBuilderFactory {
  @Override
  public PsiBuilder createBuilder(@NotNull final Project project, @NotNull final ASTNode tree) {
    return createBuilder(project, tree, null, tree.getElementType().getLanguage(), tree.getChars());
  }

  @Override
  public PsiBuilder createBuilder(@NotNull final Project project, @NotNull final ASTNode tree, @NotNull final Language lang,
                                  @NotNull final CharSequence seq) {
    return createBuilder(project, tree, null, lang, seq);
  }

  @Override
  public PsiBuilder createBuilder(@NotNull final Project project, @NotNull final ASTNode tree, @Nullable Lexer lexer,
                                  @NotNull final Language lang, @NotNull final CharSequence seq) {
    if (lexer == null) {
      final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
      assert parserDefinition != null : "ParserDefinition absent for language: " + lang.getID();
      lexer = parserDefinition.createLexer(project);
    }
    return new PsiBuilderImpl(lang, lexer, tree, seq);
  }

  @Override
  public PsiBuilder createBuilder(@NotNull final Lexer lexer, @NotNull final Language lang, @NotNull final CharSequence seq) {
    ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
    return new PsiBuilderImpl(lexer, parserDefinition.getWhitespaceTokens(), parserDefinition.getCommentTokens(), seq);
  }
}
