/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi.formatter;

import com.intellij.formatting.LanguageWhiteSpaceFormattingStrategy;
import com.intellij.formatting.WhiteSpaceFormattingStrategy;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Contains utility methods for working with {@link WhiteSpaceFormattingStrategy}.
 *
 * @author Denis Zhdanov
 * @since 10/1/10 3:31 PM
 */
public class WhiteSpaceFormattingStrategyFactory {

  private static final List<WhiteSpaceFormattingStrategy> SHARED_STRATEGIES = Arrays.<WhiteSpaceFormattingStrategy>asList(
    new StaticSymbolWhiteSpaceDefinitionStrategy(' ', '\t', '\n'), new CdataWhiteSpaceDefinitionStrategy()
  );

  private WhiteSpaceFormattingStrategyFactory() {
  }

  /**
   * @return    default language-agnostic white space strategy
   */
  public static WhiteSpaceFormattingStrategy getStrategy() {
    return new CompositeWhiteSpaceFormattingStrategy(SHARED_STRATEGIES);
  }

  /**
   * Tries to return white space strategy to use for the given language.
   *
   * @param language    target language
   * @return            white space strategy to use for the given language
   * @throws IllegalStateException      if white space strategies configuration is invalid
   */
  public static WhiteSpaceFormattingStrategy getStrategy(@NotNull Language language) throws IllegalStateException {
    CompositeWhiteSpaceFormattingStrategy result = new CompositeWhiteSpaceFormattingStrategy(SHARED_STRATEGIES);
    WhiteSpaceFormattingStrategy strategy = LanguageWhiteSpaceFormattingStrategy.INSTANCE.forLanguage(language);
    if (strategy != null) {
      result.addStrategy(strategy);
    }
    return result;
  }

  /**
   * Returns white space strategy to use for the document managed by the given editor.
   *
   * @param editor      editor that manages target document
   * @return            white space strategy for the document managed by the given editor
   * @throws IllegalStateException    if white space strategies configuration is invalid
   */
  public static WhiteSpaceFormattingStrategy getStrategy(@NotNull Editor editor) throws IllegalStateException {
    Project project = editor.getProject();
    if (project != null) {
      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (psiFile != null) {
        return getStrategy(psiFile.getLanguage());
      }
    }
    return getStrategy();
  }
}
