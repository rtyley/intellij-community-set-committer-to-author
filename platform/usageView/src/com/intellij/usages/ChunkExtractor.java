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
package com.intellij.usages;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class ChunkExtractor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usages.ChunkExtractor");

  private final PsiElement myElement;
  private final Document myDocument;
  private final int myLineNumber;
  private final int myColumnNumber;

  private final List<RangeMarker> myRangeMarkers;
  private final EditorColorsScheme myColorsScheme;

  public ChunkExtractor(final PsiElement element, final List<RangeMarker> rangeMarkers) {
    myElement = element;
    myRangeMarkers = new ArrayList<RangeMarker>(rangeMarkers.size());
    for (RangeMarker rangeMarker : rangeMarkers) {
      if (rangeMarker.isValid()) myRangeMarkers.add(rangeMarker);
    }
    Collections.sort(myRangeMarkers, RangeMarker.BY_START_OFFSET);
    myColorsScheme = UsageTreeColorsScheme.getInstance().getScheme();

    int absoluteStartOffset = getStartOffset(myRangeMarkers);
    assert absoluteStartOffset != -1;

    myDocument = PsiDocumentManager.getInstance(myElement.getProject()).getDocument(myElement.getContainingFile());
    myLineNumber = myDocument.getLineNumber(absoluteStartOffset);
    myColumnNumber = absoluteStartOffset - myDocument.getLineStartOffset(myLineNumber);
  }

  public static int getStartOffset(final List<RangeMarker> rangeMarkers) {
    LOG.assertTrue(!rangeMarkers.isEmpty());
    int minStart = Integer.MAX_VALUE;
    for (RangeMarker rangeMarker : rangeMarkers) {
      if (!rangeMarker.isValid()) continue;
      final int startOffset = rangeMarker.getStartOffset();
      if (startOffset < minStart) minStart = startOffset;
    }
    return minStart == Integer.MAX_VALUE ? -1 : minStart;
  }

  public TextChunk[] extractChunks() {
    final int lineStartOffset = myDocument.getLineStartOffset(myLineNumber);
    final int lineEndOffset = lineStartOffset < myDocument.getTextLength() ? myDocument.getLineEndOffset(myLineNumber):0;
    if (lineStartOffset > lineEndOffset) return new TextChunk[0];
    final FileType fileType = myElement.getContainingFile().getFileType();
    SyntaxHighlighter highlighter =
      SyntaxHighlighter.PROVIDER.create(fileType, myElement.getProject(), myElement.getContainingFile().getVirtualFile());
    if (highlighter == null) {
      highlighter = new PlainSyntaxHighlighter();
    }
    return createTextChunks(myDocument.getCharsSequence(), highlighter, lineStartOffset, lineEndOffset);
  }

  private TextChunk[] createTextChunks(final CharSequence chars,
                                       SyntaxHighlighter highlighter,
                                       int start,
                                       int end) {
    LOG.assertTrue(start <= end);
    List<TextChunk> result = new ArrayList<TextChunk>();

    appendPrefix(result);

    Lexer lexer = highlighter.getHighlightingLexer();
    lexer.start(chars);

    int i = StringUtil.indexOf(chars, '\n', start, end);
    if (i != -1) end = i;

    boolean isBeginning = true;

    while (lexer.getTokenType() != null) {
      try {
        int hiStart = lexer.getTokenStart();
        int hiEnd = lexer.getTokenEnd();

        if (hiStart >= end) break;

        hiStart = Math.max(hiStart, start);
        hiEnd = Math.min(hiEnd, end);
        if (hiStart >= hiEnd) { continue; }

        String text = chars.subSequence(hiStart, hiEnd).toString();
        if (isBeginning && text.trim().length() == 0) continue;
        isBeginning = false;
        IElementType tokenType = lexer.getTokenType();
        TextAttributesKey[] tokenHighlights = highlighter.getTokenHighlights(tokenType);

        processIntersectingRange(chars, hiStart, hiEnd, tokenHighlights, result);
      }
      finally {
        lexer.advance();
      }
    }

    return result.toArray(new TextChunk[result.size()]);
  }

  private void processIntersectingRange(CharSequence chars,
                                        int hiStart,
                                        int hiEnd,
                                        TextAttributesKey[] tokenHighlights,
                                        List<TextChunk> result) {
    TextAttributes originalAttrs = convertAttributes(tokenHighlights);
    int lastOffset = hiStart;
    for(RangeMarker rangeMarker: myRangeMarkers) {
      int usageStart = rangeMarker.getStartOffset();
      int usageEnd = rangeMarker.getEndOffset();
      if (rangeMarker.isValid() && rangeIntersect(lastOffset, hiEnd, usageStart, usageEnd)) {
        addChunk(chars, lastOffset, Math.max(lastOffset, usageStart), originalAttrs, false, result);
        addChunk(chars, Math.max(lastOffset, usageStart), Math.min(hiEnd, usageEnd), originalAttrs, true, result);
        if (usageEnd > hiEnd) {
          return;
        }
        lastOffset = usageEnd;
      }
    }
    if (lastOffset < hiEnd) {
      addChunk(chars, lastOffset, hiEnd, originalAttrs, false, result);
    }
  }

  private static void addChunk(CharSequence chars, int start, int end, TextAttributes originalAttrs, boolean bold, List<TextChunk> result) {
    if (start >= end) return;

    TextAttributes attrs = bold
                           ? TextAttributes.merge(originalAttrs, new TextAttributes(null, null, null, null, Font.BOLD))
                           : originalAttrs;
    result.add(new TextChunk(attrs, new String(chars.subSequence(start, end).toString())));
  }

  private static boolean rangeIntersect(int s1, int e1, int s2, int e2) {
    return s2 < s1 && s1 < e2 || s2 < e1 && e1 < e2
           || s1 < s2 && s2 < e1 || s1 < e2 && e2 < e1
           || s1 == s2 && e1 == e2;
  }

  private TextAttributes convertAttributes(TextAttributesKey[] keys) {
    TextAttributes attrs = myColorsScheme.getAttributes(HighlighterColors.TEXT);

    for (TextAttributesKey key : keys) {
      TextAttributes attrs2 = myColorsScheme.getAttributes(key);
      if (attrs2 != null) {
        attrs = TextAttributes.merge(attrs, attrs2);
      }
    }

    attrs = attrs.clone();
    attrs.setFontType(Font.PLAIN);
    return attrs;
  }

  private void appendPrefix(List<TextChunk> result) {
    StringBuilder buffer = new StringBuilder("(");
    buffer.append(myLineNumber + 1);
    buffer.append(", ");
    buffer.append(myColumnNumber + 1);
    buffer.append(") ");
    TextChunk prefixChunk = new TextChunk(myColorsScheme.getAttributes(UsageTreeColors.USAGE_LOCATION), buffer.toString());
    result.add(prefixChunk);
  }
}
