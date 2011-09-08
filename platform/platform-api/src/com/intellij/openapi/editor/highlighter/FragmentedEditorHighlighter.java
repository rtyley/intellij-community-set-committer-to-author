/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.editor.highlighter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 9/8/11
 * Time: 12:52 PM
 */
public class FragmentedEditorHighlighter implements EditorHighlighter {
  private final TreeMap<Integer, Element> myPieces;
  private final Document myDocument;

  public FragmentedEditorHighlighter(HighlighterIterator sourceIterator, List<TextRange> ranges) {
    myDocument = sourceIterator.getDocument();
    myPieces = new TreeMap<Integer, Element>();
    translate(sourceIterator, ranges);
  }

  private void translate(HighlighterIterator iterator, List<TextRange> ranges) {
    if (iterator.atEnd()) return;
    int offset = 0;
    for (TextRange range : ranges) {
      while (range.getStartOffset() > iterator.getStart()) {
        iterator.advance();
        if (iterator.atEnd()) return;
      }
      while (range.getEndOffset() >= iterator.getEnd()) {
        int relativeStart = iterator.getStart() - range.getStartOffset();
        myPieces.put(offset + relativeStart, new Element(offset + relativeStart,
          offset + (iterator.getEnd() - range.getStartOffset()), iterator.getTokenType(), iterator.getTextAttributes()));
        iterator.advance();
        if (iterator.atEnd()) return;
      }
      offset += range.getLength();
    }
  }

  @Override
  public HighlighterIterator createIterator(int startOffset) {
    Map.Entry<Integer, Element> entry = myPieces.ceilingEntry(startOffset);
    return new ProxyIterator(myDocument, entry == null ? -1 : entry.getKey());
  }

  @Override
  public void setText(CharSequence text) {
  }

  @Override
  public void setEditor(HighlighterClient editor) {
  }

  @Override
  public void setColorScheme(EditorColorsScheme scheme) {
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
  }

  @Override
  public void documentChanged(DocumentEvent event) {
  }

  private class ProxyIterator implements HighlighterIterator {
    private final Document myDocument;
    private int myIdx;

    private ProxyIterator(Document document, int idx) {
      myDocument = document;
      myIdx = idx;
    }

    @Override
    public TextAttributes getTextAttributes() {
      return myPieces.get(myIdx).getAttributes();
    }

    @Override
    public int getStart() {
      return myPieces.get(myIdx).getStart();
    }

    @Override
    public int getEnd() {
      return myPieces.get(myIdx).getEnd();
    }

    @Override
    public IElementType getTokenType() {
      return myPieces.get(myIdx).myElementType;
    }

    @Override
    public void advance() {
      if (myIdx == myPieces.lastKey() || myIdx == -1) {
        myIdx = -1;
        return;
      }
      Map.Entry<Integer, Element> entry = myPieces.tailMap(myIdx, false).firstEntry();
      myIdx = entry.getKey();
    }

    @Override
    public void retreat() {
      if (myIdx == myPieces.firstKey() || myIdx == -1) {
        myIdx = -1;
        return;
      }
      Map.Entry<Integer, Element> entry = myPieces.headMap(myIdx, false).lastEntry();
      myIdx = entry.getKey();
    }

    @Override
    public boolean atEnd() {
      return myIdx < 0;
    }

    @Override
    public Document getDocument() {
      return myDocument;
    }
  }
  
  private static class Element {
    private final int myStart;
    private final int myEnd;
    private final IElementType myElementType;
    private final TextAttributes myAttributes;

    private Element(int start, int end, IElementType elementType, TextAttributes attributes) {
      myStart = start;
      myEnd = end;
      myElementType = elementType;
      myAttributes = attributes;
    }

    public int getStart() {
      return myStart;
    }

    public int getEnd() {
      return myEnd;
    }

    public IElementType getElementType() {
      return myElementType;
    }

    public TextAttributes getAttributes() {
      return myAttributes;
    }
  }
}
