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
package com.intellij.lexer;

import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlTokenType;

/**
 * @author ik
 */
public class OldXmlLexer extends MergingLexerAdapter {
  private final static TokenSet TOKENS_TO_MERGE =
    TokenSet.create(XmlTokenType.XML_DATA_CHARACTERS, XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, XmlTokenType.XML_PI_TARGET);

  public OldXmlLexer() {
    super(new FlexAdapter(new _OldXmlLexer()), TOKENS_TO_MERGE);
  }
}
