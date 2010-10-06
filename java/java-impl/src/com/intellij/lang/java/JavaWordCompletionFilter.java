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

/*
 * @author max
 */
package com.intellij.lang.java;

import com.intellij.codeInsight.completion.CompletionProcess;
import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.lang.WordCompletionElementFilter;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public class JavaWordCompletionFilter implements WordCompletionElementFilter {
  private static final TokenSet ENABLED_TOKENS = TokenSet.create(JavaTokenType.C_STYLE_COMMENT, JavaTokenType.END_OF_LINE_COMMENT,
                                                                 JavaDocTokenType.DOC_COMMENT_DATA, JavaTokenType.STRING_LITERAL);

  public boolean isWordCompletionEnabledIn(final IElementType element) {
    final CompletionProcess process = CompletionService.getCompletionService().getCurrentCompletion();
    if (process != null && process.isAutopopupCompletion()) {
      return false;
    }

    return ENABLED_TOKENS.contains(element);
  }
}