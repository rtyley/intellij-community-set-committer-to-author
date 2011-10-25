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
package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer;
import com.intellij.psi.impl.cache.impl.todo.TodoOccurrenceConsumer;
import com.intellij.psi.impl.source.tree.StdTokenSets;

public class JavaTodoIndexer extends LexerBasedTodoIndexer {
  @Override
  protected Lexer createLexer(final TodoOccurrenceConsumer consumer) {
    final JavaLexer javaLexer = new JavaLexer(LanguageLevel.JDK_1_3);
    final JavaFilterLexer filterLexer = new JavaFilterLexer(javaLexer, consumer);
    return new FilterLexer(filterLexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
  }
}
