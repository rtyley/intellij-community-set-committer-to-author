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

package com.intellij.injected.editor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.HighlighterList;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.ProperTextRange;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
*/
class MarkupModelWindow extends UserDataHolderBase implements MarkupModelEx {
  private final DocumentWindow myDocument;
  private final MarkupModelEx myHostModel;

  public MarkupModelWindow(MarkupModelEx editorMarkupModel, final DocumentWindow document) {
    myDocument = document;
    myHostModel = editorMarkupModel;
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @NotNull
  public RangeHighlighter addRangeHighlighter(final int startOffset,
                                              final int endOffset,
                                              final int layer,
                                              final TextAttributes textAttributes,
                                              @NotNull final HighlighterTargetArea targetArea) {
    TextRange hostRange = myDocument.injectedToHost(new ProperTextRange(startOffset, endOffset));
    return myHostModel.addRangeHighlighter(hostRange.getStartOffset(), hostRange.getEndOffset(), layer, textAttributes, targetArea);
  }

  @NotNull
  public RangeHighlighter addLineHighlighter(final int line, final int layer, final TextAttributes textAttributes) {
    int hostLine = myDocument.injectedToHostLine(line);
    return myHostModel.addLineHighlighter(hostLine, layer, textAttributes);
  }

  public void removeHighlighter(final RangeHighlighter rangeHighlighter) {
    myHostModel.removeHighlighter(rangeHighlighter);
  }

  public void removeAllHighlighters() {
    myHostModel.removeAllHighlighters();
  }

  @NotNull
  public RangeHighlighter[] getAllHighlighters() {
    return myHostModel.getAllHighlighters();
  }

  public void dispose() {
    myHostModel.dispose();
  }

  public HighlighterList getHighlighterList() {
    return myHostModel.getHighlighterList();
  }

  public RangeHighlighter addPersistentLineHighlighter(final int line, final int layer, final TextAttributes textAttributes) {
    int hostLine = myDocument.injectedToHostLine(line);
    return myHostModel.addPersistentLineHighlighter(hostLine, layer, textAttributes);
  }


  public boolean containsHighlighter(final RangeHighlighter highlighter) {
    return myHostModel.containsHighlighter(highlighter);
  }

  public void addMarkupModelListener(MarkupModelListener listener) {
    myHostModel.addMarkupModelListener(listener);
  }


  public void removeMarkupModelListener(MarkupModelListener listener) {
    myHostModel.removeMarkupModelListener(listener);
  }

  public void setRangeHighlighterAttributes(final RangeHighlighter highlighter, final TextAttributes textAttributes) {
    myHostModel.setRangeHighlighterAttributes(highlighter, textAttributes);
  }

}