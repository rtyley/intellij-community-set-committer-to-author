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
package com.intellij.lang.java.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.parsing.ParseUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.lang.java.parser.JavaParserUtil.setLanguageLevel;


public class JavaParser implements PsiParser {
  private final LanguageLevel myLanguageLevel;

  public JavaParser(final LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
  }

  @NotNull
  public ASTNode parse(final IElementType rootType, final PsiBuilder builder) {
    setLanguageLevel(builder, myLanguageLevel);

    final PsiBuilder.Marker root = builder.mark();
    FileParser.parse(builder);
    root.done(rootType);

    final ASTNode rootNode = builder.getTreeBuilt();
    ParseUtil.bindComments(rootNode);
    return rootNode;
  }
}
