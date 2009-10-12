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
package com.intellij.codeInsight.editorActions;

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.Ref;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate;

public class EnterBetweenXmlTagsHandler implements EnterHandlerDelegate {
  public Result preprocessEnter(final PsiFile file, final Editor editor, final Ref<Integer> caretOffset, final Ref<Integer> caretAdvance,
                                final DataContext dataContext, final EditorActionHandler originalHandler) {
    if (file instanceof XmlFile && isBetweenXmlTags(editor, caretOffset.get().intValue())) {
      originalHandler.execute(editor, dataContext);
      return Result.DefaultForceIndent;
    }
    return Result.Continue;
  }

  private static boolean isBetweenXmlTags(Editor editor, int offset) {
    if (offset == 0) return false;
    CharSequence chars = editor.getDocument().getCharsSequence();
    if (chars.charAt(offset - 1) != '>') return false;

    EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    HighlighterIterator iterator = highlighter.createIterator(offset - 1);
    if (iterator.getTokenType() != XmlTokenType.XML_TAG_END) return false;
    iterator.retreat();

    int retrieveCount = 1;
    while(!iterator.atEnd()) {
      final IElementType tokenType = iterator.getTokenType();
      if (tokenType == XmlTokenType.XML_END_TAG_START) return false;
      if (tokenType == XmlTokenType.XML_START_TAG_START) break;
      ++retrieveCount;
      iterator.retreat();
    }

    for(int i = 0; i < retrieveCount; ++i) iterator.advance();
    iterator.advance();
    return !iterator.atEnd() && iterator.getTokenType() == XmlTokenType.XML_END_TAG_START;
  }
}
