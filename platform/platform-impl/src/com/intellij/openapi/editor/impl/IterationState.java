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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings({"ForLoopReplaceableByForEach"}) // Way too many garbage in AbstractList.iterator() produced otherwise.
public final class IterationState {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.IterationState");
  private final TextAttributes myMergedAttributes = new TextAttributes();

  private final HighlighterIterator myHighlighterIterator;
  private final List<RangeHighlighterImpl> myViewHighlighters;
  private int myCurrentViewHighlighterIdx;

  private final List<RangeHighlighterImpl> myDocumentHighlighters;
  private int myCurrentDocHighlighterIdx;

  private int myStartOffset;
  private int myEndOffset;
  private final int myEnd;

  private final int mySelectionStart;
  private final int mySelectionEnd;

  private ArrayList<RangeHighlighterImpl> myCurrentHighlighters;

  private RangeHighlighterImpl myNextViewHighlighter = null;
  private RangeHighlighterImpl myNextDocumentHighlighter = null;

  private final FoldingModelEx myFoldingModel;

  private final boolean hasSelection;
  private FoldRegion myCurrentFold = null;
  private final TextAttributes myFoldTextAttributes;
  private final TextAttributes mySelectionAttributes;
  private final TextAttributes myCaretRowAttributes;
  private final Color myDefaultBackground;
  private final Color myDefaultForeground;
  private final int myCaretRowStart;
  private final int myCaretRowEnd;
  private final ArrayList<TextAttributes> myCachedAttributesList;
  private final DocumentEx myDocument;
  private final EditorEx myEditor;
  private final Color myReadOnlyColor;

  public IterationState(EditorEx editor, int start, boolean useCaretAndSelection) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myDocument = (DocumentEx)editor.getDocument();
    myStartOffset = start;
    myEnd = editor.getDocument().getTextLength();
    myEditor = editor;

    LOG.assertTrue(myStartOffset <= myEnd);
    myHighlighterIterator = editor.getHighlighter().createIterator(start);

    HighlighterList editorList = ((MarkupModelEx)editor.getMarkupModel()).getHighlighterList();

    int longestViewHighlighterLength = editorList == null ? 0 : editorList.getLongestHighlighterLength();
    myViewHighlighters = editorList == null ? null : editorList.getSortedHighlighters();

    final MarkupModelEx docMarkup = (MarkupModelEx)editor.getDocument().getMarkupModel(editor.getProject());

    final HighlighterList docList = docMarkup.getHighlighterList();
    myDocumentHighlighters = docList != null
                             ? docList.getSortedHighlighters()
                             : new ArrayList<RangeHighlighterImpl>();

    int longestDocHighlighterLength = docList != null
                                      ? docList.getLongestHighlighterLength()
                                      : 0;

    hasSelection = useCaretAndSelection && editor.getSelectionModel().hasSelection();
    mySelectionStart = hasSelection ? editor.getSelectionModel().getSelectionStart() : -1;
    mySelectionEnd = hasSelection ? editor.getSelectionModel().getSelectionEnd() : -1;

    myFoldingModel = (FoldingModelEx)editor.getFoldingModel();
    myFoldTextAttributes = myFoldingModel.getPlaceholderAttributes();
    mySelectionAttributes = editor.getSelectionModel().getTextAttributes();

    myReadOnlyColor = myEditor.getColorsScheme().getColor(EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR);

    CaretModel caretModel = editor.getCaretModel();
    myCaretRowAttributes = editor.isRendererMode() ? null : caretModel.getTextAttributes();
    myDefaultBackground = editor.getColorsScheme().getDefaultBackground();
    myDefaultForeground = editor.getColorsScheme().getDefaultForeground();

    myCurrentHighlighters = new ArrayList<RangeHighlighterImpl>();

    myCurrentViewHighlighterIdx = initHighlighterIterator(start, myViewHighlighters, longestViewHighlighterLength);
    while (myCurrentViewHighlighterIdx < myViewHighlighters.size()) {
      myNextViewHighlighter = myViewHighlighters.get(myCurrentViewHighlighterIdx);
      if (!skipHighlighter(myNextViewHighlighter)) break;
      myCurrentViewHighlighterIdx++;
    }
    if (myCurrentViewHighlighterIdx == myViewHighlighters.size()) myNextViewHighlighter = null;

    myCurrentDocHighlighterIdx = initHighlighterIterator(start, myDocumentHighlighters, longestDocHighlighterLength);
    myNextDocumentHighlighter = null;
    while (myCurrentDocHighlighterIdx < myDocumentHighlighters.size()) {
      myNextDocumentHighlighter = myDocumentHighlighters.get(myCurrentDocHighlighterIdx);
      if (!skipHighlighter(myNextDocumentHighlighter)) break;
      myCurrentDocHighlighterIdx++;
    }
    if (myCurrentDocHighlighterIdx == myDocumentHighlighters.size()) myNextDocumentHighlighter = null;

    advanceSegmentHighlighters();

    myCaretRowStart = caretModel.getVisualLineStart();
    myCaretRowEnd = caretModel.getVisualLineEnd();

    myEndOffset = Math.min(getHighlighterEnd(myStartOffset), getSelectionEnd(myStartOffset));
    myEndOffset = Math.min(myEndOffset, getSegmentHighlightersEnd());
    myEndOffset = Math.min(myEndOffset, getFoldRangesEnd(myStartOffset));
    myEndOffset = Math.min(myEndOffset, getCaretEnd(myStartOffset));
    myEndOffset = Math.min(myEndOffset, getGuardedBlockEnd(myStartOffset));

    myCurrentFold = myFoldingModel.getCollapsedRegionAtOffset(myStartOffset);
    if (myCurrentFold != null) {
      myEndOffset = myCurrentFold.getEndOffset();
    }

    myCachedAttributesList = new ArrayList<TextAttributes>(5);

    reinit();
  }

  private int initHighlighterIterator(int start, List<RangeHighlighterImpl> sortedHighlighters, int longestHighlighterLength) {
    int low = 0;
    int high = sortedHighlighters.size();
    int search = myDocument.getLineStartOffset(myDocument.getLineNumber(start)) -
                 longestHighlighterLength - 1;

    if (search > 0) {
      while (low < high) {
        int mid = (low + high) / 2;
        while (mid > 0 && !sortedHighlighters.get(mid).isValid()) mid--;
        if (mid < low + 1) break;
        RangeHighlighterImpl midHighlighter = sortedHighlighters.get(mid);
        if (midHighlighter.getStartOffset() < search) {
          low = mid + 1;
        }
        else {
          high = mid - 1;
        }
      }
    }

    for (int i = low == high ? low : 0; i < sortedHighlighters.size(); i++) {
      RangeHighlighterImpl rangeHighlighter = sortedHighlighters.get(i);
      if (!skipHighlighter(rangeHighlighter) &&
          rangeHighlighter.getAffectedAreaEndOffset() >= start) {
        return i;
      }
    }
    return sortedHighlighters.size();
  }

  private boolean skipHighlighter(RangeHighlighterImpl highlighter) {
    if (!highlighter.isValid() || highlighter.isAfterEndOfLine() || highlighter.getTextAttributes() == null) return true;
    final FoldRegion region = myFoldingModel.getCollapsedRegionAtOffset(highlighter.getAffectedAreaStartOffset());
    if (region != null && region == myFoldingModel.getCollapsedRegionAtOffset(highlighter.getAffectedAreaEndOffset())) return true;
    return !highlighter.getEditorFilter().avaliableIn(myEditor);
  }

  public void advance() {
    myStartOffset = myEndOffset;
    advanceSegmentHighlighters();

    myCurrentFold = myFoldingModel.fetchOutermost(myStartOffset);
    if (myCurrentFold != null) {
      myEndOffset = myCurrentFold.getEndOffset();
    }
    else {
      myEndOffset = Math.min(getHighlighterEnd(myStartOffset), getSelectionEnd(myStartOffset));
      myEndOffset = Math.min(myEndOffset, getSegmentHighlightersEnd());
      myEndOffset = Math.min(myEndOffset, getFoldRangesEnd(myStartOffset));
      myEndOffset = Math.min(myEndOffset, getCaretEnd(myStartOffset));
      myEndOffset = Math.min(myEndOffset, getGuardedBlockEnd(myStartOffset));
    }

    reinit();
  }

  private int getHighlighterEnd(int start) {
    while (!myHighlighterIterator.atEnd()) {
      int end = myHighlighterIterator.getEnd();
      if (end > start) {
        return end;
      }
      myHighlighterIterator.advance();
    }
    return myEnd;
  }

  private int getCaretEnd(int start) {
    if (myCaretRowStart > start) {
      return myCaretRowStart;
    }

    if (myCaretRowEnd > start) {
      return myCaretRowEnd;
    }

    return myEnd;
  }

  private int getGuardedBlockEnd(int start) {
    List<RangeMarker> blocks = myDocument.getGuardedBlocks();
    int min = myEnd;
    for (int i = 0; i < blocks.size(); i++) {
      RangeMarker block = blocks.get(i);
      if (block.getStartOffset() > start) {
        min = Math.min(min, block.getStartOffset());
      }
      else if (block.getEndOffset() > start) {
        min = Math.min(min, block.getEndOffset());
      }
    }
    return min;
  }

  private int getSelectionEnd(int start) {
    if (!hasSelection) {
      return myEnd;
    }
    if (mySelectionStart > start) {
      return mySelectionStart;
    }
    if (mySelectionEnd > start) {
      return mySelectionEnd;
    }
    return myEnd;
  }

  private void advanceSegmentHighlighters() {
    if (myNextDocumentHighlighter != null) {
      if (myNextDocumentHighlighter.getAffectedAreaStartOffset() <= myStartOffset) {
        myCurrentHighlighters.add(myNextDocumentHighlighter);
        myNextDocumentHighlighter = null;
      }
    }

    if (myNextViewHighlighter != null) {
      if (myNextViewHighlighter.getAffectedAreaStartOffset() <= myStartOffset) {
        myCurrentHighlighters.add(myNextViewHighlighter);
        myNextViewHighlighter = null;
      }
    }


    RangeHighlighterImpl highlighter;

    final int docHighlightersSize = myDocumentHighlighters.size();
    while (myNextDocumentHighlighter == null && myCurrentDocHighlighterIdx < docHighlightersSize) {
      highlighter = myDocumentHighlighters.get(myCurrentDocHighlighterIdx++);
      if (!skipHighlighter(highlighter)) {
        if (highlighter.getAffectedAreaStartOffset() > myStartOffset) {
          myNextDocumentHighlighter = highlighter;
          break;
        }
        else {
          myCurrentHighlighters.add(highlighter);
        }
      }
    }

    final int viewHighlightersSize = myViewHighlighters.size();
    while (myNextViewHighlighter == null && myCurrentViewHighlighterIdx < viewHighlightersSize) {
      highlighter = myViewHighlighters.get(myCurrentViewHighlighterIdx++);
      if (!skipHighlighter(highlighter)) {
        if (highlighter.getAffectedAreaStartOffset() > myStartOffset) {
          myNextViewHighlighter = highlighter;
          break;
        }
        else {
          myCurrentHighlighters.add(highlighter);
        }
      }
    }

    if (myCurrentHighlighters.size() == 1) {
      //Optimization
      if (myCurrentHighlighters.get(0).getAffectedAreaEndOffset() <= myStartOffset) {
        myCurrentHighlighters = new ArrayList<RangeHighlighterImpl>();
      }
    }
    else if (!myCurrentHighlighters.isEmpty()) {
      ArrayList<RangeHighlighterImpl> copy = new ArrayList<RangeHighlighterImpl>(myCurrentHighlighters.size());
      for (int i = 0; i < myCurrentHighlighters.size(); i++) {
        highlighter = myCurrentHighlighters.get(i);
        if (highlighter.getAffectedAreaEndOffset() > myStartOffset) {
          copy.add(highlighter);
        }
      }
      myCurrentHighlighters = copy;
    }
  }

  private int getFoldRangesEnd(int startOffset) {
    int end = myEnd;
    FoldRegion[] topLevelCollapsed = myFoldingModel.fetchTopLevel();
    if (topLevelCollapsed != null) {
      for (int i = myFoldingModel.getLastCollapsedRegionBefore(startOffset) + 1;
           i >= 0 && i < topLevelCollapsed.length;
           i++) {
        FoldRegion range = topLevelCollapsed[i];
        if (!range.isValid()) continue;

        int rangeEnd = range.getStartOffset();
        if (rangeEnd > startOffset) {
          if (rangeEnd < end) {
            end = rangeEnd;
          }
          else {
            break;
          }
        }
      }
    }

    return end;
  }

  private int getSegmentHighlightersEnd() {
    int end = myEnd;

    for (RangeHighlighterImpl highlighter : myCurrentHighlighters) {
      if (highlighter.getAffectedAreaEndOffset() < end) {
        end = highlighter.getAffectedAreaEndOffset();
      }
    }

    if (myNextDocumentHighlighter != null && myNextDocumentHighlighter.getAffectedAreaStartOffset() < end) {
      end = myNextDocumentHighlighter.getAffectedAreaStartOffset();
    }

    if (myNextViewHighlighter != null && myNextViewHighlighter.getAffectedAreaStartOffset() < end) {
      end = myNextViewHighlighter.getAffectedAreaStartOffset();
    }

    return end;
  }

  private void reinit() {
    if (myHighlighterIterator.atEnd()) {
      return;
    }

    boolean isInSelection = hasSelection && myStartOffset >= mySelectionStart && myStartOffset < mySelectionEnd;
    boolean isInCaretRow = myStartOffset >= myCaretRowStart && myStartOffset < myCaretRowEnd;
    boolean isInGuardedBlock = myDocument.getOffsetGuard(myStartOffset) != null;

    TextAttributes syntax = myHighlighterIterator.getTextAttributes();

    TextAttributes selection = isInSelection ? mySelectionAttributes : null;
    TextAttributes caret = isInCaretRow ? myCaretRowAttributes : null;
    TextAttributes fold = myCurrentFold != null ? myFoldTextAttributes : null;
    TextAttributes guard = isInGuardedBlock
                           ? new TextAttributes(null, myReadOnlyColor, null, EffectType.BOXED, Font.PLAIN)
                           : null;

    final int size = myCurrentHighlighters.size();
    if (size > 1) {
      ContainerUtil.quickSort(myCurrentHighlighters, LayerComparator.INSTANCE);
    }

    for (int i = 0; i < size; i++) {
      RangeHighlighterImpl highlighter = myCurrentHighlighters.get(i);
      if (highlighter.getTextAttributes() == TextAttributes.ERASE_MARKER) {
        syntax = null;
      }
    }

    myCachedAttributesList.clear();

    for (int i = 0; i < size; i++) {
      RangeHighlighterImpl highlighter = myCurrentHighlighters.get(i);
      if (selection != null && highlighter.getLayer() < HighlighterLayer.SELECTION) {
        myCachedAttributesList.add(selection);
        selection = null;
      }

      if (syntax != null && highlighter.getLayer() < HighlighterLayer.SYNTAX) {
        if (fold != null) {
          myCachedAttributesList.add(fold);
          fold = null;
        }

        myCachedAttributesList.add(syntax);
        syntax = null;
      }

      if (guard != null && highlighter.getLayer() < HighlighterLayer.GUARDED_BLOCKS) {
        myCachedAttributesList.add(guard);
        guard = null;
      }

      if (caret != null && highlighter.getLayer() < HighlighterLayer.CARET_ROW) {
        myCachedAttributesList.add(caret);
        caret = null;
      }

      TextAttributes textAttributes = highlighter.getTextAttributes();
      if (textAttributes != null) {
        myCachedAttributesList.add(textAttributes);
      }
    }

    if (selection != null) myCachedAttributesList.add(selection);
    if (fold != null) myCachedAttributesList.add(fold);
    if (guard != null) myCachedAttributesList.add(guard);
    if (syntax != null) myCachedAttributesList.add(syntax);
    if (caret != null) myCachedAttributesList.add(caret);

    Color fore = null;
    Color back = isInGuardedBlock ? myReadOnlyColor : null;
    Color effect = null;
    EffectType effectType = null;
    int fontType = 0;

    for (int i = 0; i < myCachedAttributesList.size(); i++) {
      TextAttributes attrs = myCachedAttributesList.get(i);

      if (fore == null) {
        fore = ifDiffers(attrs.getForegroundColor(), myDefaultForeground);
      }

      if (back == null) {
        back = ifDiffers(attrs.getBackgroundColor(), myDefaultBackground);
      }

      if (fontType == Font.PLAIN) {
        fontType = attrs.getFontType();
      }

      if (effect == null) {
        effect = attrs.getEffectColor();
        effectType = attrs.getEffectType();
      }
    }

    if (fore == null) fore = myDefaultForeground;
    if (back == null) back = myDefaultBackground;
    if (effectType == null) effectType = EffectType.BOXED;

    myMergedAttributes.setForegroundColor(fore);
    myMergedAttributes.setBackgroundColor(back);
    myMergedAttributes.setFontType(fontType);
    myMergedAttributes.setEffectColor(effect);
    myMergedAttributes.setEffectType(effectType);
  }

  @Nullable
  private static Color ifDiffers(final Color c1, final Color c2) {
    return c1 == c2 ? null : c1;
  }

  public boolean atEnd() {
    return myStartOffset >= myEnd;
  }


  public int getStartOffset() {
    return myStartOffset;
  }

  public int getEndOffset() {
    return myEndOffset;
  }

  public TextAttributes getMergedAttributes() {
    return myMergedAttributes;
  }

  public FoldRegion getCurrentFold() {
    return myCurrentFold;
  }

  @Nullable
  public Color getPastFileEndBackground() {
    boolean isInCaretRow = myEditor.getCaretModel().getLogicalPosition().line >= myDocument.getLineCount() - 1;

    Color caret = isInCaretRow && myCaretRowAttributes != null ? myCaretRowAttributes.getBackgroundColor() : null;

      ContainerUtil.quickSort(myCurrentHighlighters, LayerComparator.INSTANCE);

    for (int i = 0; i < myCurrentHighlighters.size(); i++) {
      RangeHighlighterImpl highlighter = myCurrentHighlighters.get(i);
      if (caret != null && highlighter.getLayer() < HighlighterLayer.CARET_ROW) {
        return caret;
      }

      if (highlighter.getTargetArea() != HighlighterTargetArea.LINES_IN_RANGE
          || myDocument.getLineNumber(highlighter.getEndOffset()) < myDocument.getLineCount() - 1) {
        continue;
      }

      TextAttributes textAttributes = highlighter.getTextAttributes();
      if (textAttributes != null) {
        Color backgroundColor = textAttributes.getBackgroundColor();
        if (backgroundColor != null) return backgroundColor;
      }
    }

    return caret;
  }

  private static class LayerComparator implements Comparator<RangeHighlighterImpl> {
    private static final LayerComparator INSTANCE = new LayerComparator();
    public int compare(RangeHighlighterImpl o1, RangeHighlighterImpl o2) {
      int layerDiff = o2.getLayer() - o1.getLayer();
      if (layerDiff != 0) {
        return layerDiff;
      }
      // prefer more specific region
      int o1Length = o1.getEndOffset() - o1.getStartOffset();
      int o2Length = o2.getEndOffset() - o2.getStartOffset();
      return o1Length - o2Length;
    }
  }
}
