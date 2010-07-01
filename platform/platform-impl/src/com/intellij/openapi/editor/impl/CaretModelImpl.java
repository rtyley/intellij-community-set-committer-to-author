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
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 18, 2002
 * Time: 9:12:05 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;

import java.awt.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class CaretModelImpl implements CaretModel, PrioritizedDocumentListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.CaretModelImpl");
  private final EditorImpl myEditor;
  private final CopyOnWriteArrayList<CaretListener> myCaretListeners = ContainerUtil.createEmptyCOWList();
  private LogicalPosition myLogicalCaret;
  private VerticalInfo myCaretInfo;
  private VisualPosition myVisibleCaret;
  private int myOffset;
  private int myVisualLineStart;
  private int myVisualLineEnd;
  private TextAttributes myTextAttributes;
  private boolean myIsInUpdate;

  public CaretModelImpl(EditorImpl editor) {
    myEditor = editor;
    myLogicalCaret = new LogicalPosition(0, 0);
    myVisibleCaret = new VisualPosition(0, 0);
    myCaretInfo = new VerticalInfo(0, 0);
    myOffset = 0;
    myVisualLineStart = 0;
    Document doc = editor.getDocument();
    myVisualLineEnd = doc.getLineCount() > 1 ? doc.getLineStartOffset(1) : doc.getLineCount() == 0 ? 0 : doc.getLineEndOffset(0);
  }

  public void moveToVisualPosition(VisualPosition pos) {
    assertIsDispatchThread();
    validateCallContext();
    int column = pos.column;
    int line = pos.line;

    if (column < 0) column = 0;

    if (line < 0) line = 0;

    int lastLine = myEditor.getVisibleLineCount() - 1;
    if (lastLine <= 0) {
      lastLine = 0;
    }

    if (line > lastLine) {
      line = lastLine;
    }

    EditorSettings editorSettings = myEditor.getSettings();

    if (!editorSettings.isVirtualSpace() && line <= lastLine) {
      int lineEndColumn = EditorUtil.getLastVisualLineColumnNumber(myEditor, line);
      if (column > lineEndColumn) {
        column = lineEndColumn;
      }

      if (column < 0 && line > 0) {
        line--;
        column = EditorUtil.getLastVisualLineColumnNumber(myEditor, line);
      }
    }

    myVisibleCaret = new VisualPosition(line, column);

    VerticalInfo oldInfo = myCaretInfo;
    LogicalPosition oldPosition = myLogicalCaret;

    setCurrentLogicalCaret(myEditor.visualToLogicalPosition(myVisibleCaret));
    myOffset = myEditor.logicalPositionToOffset(myLogicalCaret);
    LOG.assertTrue(myOffset >= 0 && myOffset <= myEditor.getDocument().getTextLength());

    myVisualLineStart = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line, 0)));
    myVisualLineEnd = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line + 1, 0)));

    ((FoldingModelImpl)myEditor.getFoldingModel()).flushCaretPosition();

    myEditor.setLastColumnNumber(myVisibleCaret.column);
    myEditor.updateCaretCursor();
    requestRepaint(oldInfo);

    if (oldPosition.column != myLogicalCaret.column || oldPosition.line != myLogicalCaret.line) {
      CaretEvent event = new CaretEvent(myEditor, oldPosition, myLogicalCaret);

      for (CaretListener listener : myCaretListeners) {
        listener.caretPositionChanged(event);
      }
    }
  }

  private void assertIsDispatchThread() {
    myEditor.assertIsDispatchThread();
  }

  public void moveToOffset(int offset) {
    assertIsDispatchThread();
    validateCallContext();
    moveToLogicalPosition(myEditor.offsetToLogicalPosition(offset));
    if (!myEditor.offsetToLogicalPosition(myOffset).equals(myEditor.offsetToLogicalPosition(offset))) {
      LOG.error("caret moved to wrong offset. Requested:" + offset + " but actual:" + myOffset);
    }
  }

  public void moveCaretRelatively(int columnShift,
                                  int lineShift,
                                  boolean withSelection,
                                  boolean blockSelection,
                                  boolean scrollToCaret)
  {
    assertIsDispatchThread();
    SelectionModel selectionModel = myEditor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    LogicalPosition blockSelectionStart = selectionModel.hasBlockSelection()
                                          ? selectionModel.getBlockStart()
                                          : getLogicalPosition();
    EditorSettings editorSettings = myEditor.getSettings();
    VisualPosition visualCaret = getVisualPosition();

    int newColumnNumber = visualCaret.column + columnShift;
    int newLineNumber = visualCaret.line + lineShift;

    if (!editorSettings.isVirtualSpace() && columnShift == 0) {
      newColumnNumber = myEditor.getLastColumnNumber();
    }
    else if (!editorSettings.isVirtualSpace() && lineShift == 0 && columnShift == 1) {
      int lastLine = myEditor.getDocument().getLineCount() - 1;
      if (lastLine < 0) lastLine = 0;
      if (EditorModificationUtil.calcAfterLineEnd(myEditor) >= 0 &&
          newLineNumber < myEditor.logicalToVisualPosition(new LogicalPosition(lastLine, 0)).line) {
        newColumnNumber = 0;
        newLineNumber++;
      }
    }
    else if (!editorSettings.isVirtualSpace() && lineShift == 0 && columnShift == -1) {
      if (newColumnNumber < 0 && newLineNumber > 0) {
        newLineNumber--;
        newColumnNumber = EditorUtil.getLastVisualLineColumnNumber(myEditor, newLineNumber);
      }
    }

    if (newColumnNumber < 0) newColumnNumber = 0;
    if (newLineNumber < 0) newLineNumber = 0;

    int lastColumnNumber = newColumnNumber;
    if (!editorSettings.isCaretInsideTabs()) {
      LogicalPosition log = myEditor.visualToLogicalPosition(new VisualPosition(newLineNumber, newColumnNumber));
      int offset = myEditor.logicalPositionToOffset(log);
      CharSequence text = myEditor.getDocument().getCharsSequence();
      if (offset >= 0 && offset < myEditor.getDocument().getTextLength()) {
        if (text.charAt(offset) == '\t') {
          if (columnShift <= 0) {
            newColumnNumber = myEditor.offsetToVisualPosition(offset).column;
          }
          else {
            if (myEditor.offsetToVisualPosition(offset).column < newColumnNumber) {
              newColumnNumber = myEditor.offsetToVisualPosition(offset + 1).column;
            }
          }
        }
      }
    }

    VisualPosition pos = new VisualPosition(newLineNumber, newColumnNumber);
    if (columnShift != 0 && lineShift == 0 && myEditor.getSoftWrapModel().isInsideSoftWrap(pos)) {
      LogicalPosition logical = myEditor.visualToLogicalPosition(pos);
      int offsetToUse = myEditor.logicalPositionToOffset(logical);
      if (columnShift < 0) {
        offsetToUse--;

      }
      moveToOffset(offsetToUse);
    }
    else {
      moveToVisualPosition(pos);
      if (!editorSettings.isVirtualSpace() && columnShift == 0) {
        myEditor.setLastColumnNumber(lastColumnNumber);
      }
    }

    if (withSelection) {
      if (blockSelection) {
        selectionModel.setBlockSelection(blockSelectionStart, getLogicalPosition());
      }
      else {
        selectionModel.setSelection(selectionStart, getOffset());
      }
    }
    else {
      selectionModel.removeSelection();
    }

    if (scrollToCaret) {
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }

  public void moveToLogicalPosition(LogicalPosition pos) {
    assertIsDispatchThread();
    validateCallContext();
    int column = pos.column;
    int line = pos.line;
    int softWrapLinesBefore = pos.softWrapLinesBeforeCurrentLogicalLine;
    int softWrapLinesCurrent = pos.softWrapLinesOnCurrentLogicalLine;
    int softWrapColumns = pos.softWrapColumnDiff;

    Document doc = myEditor.getDocument();

    if (column < 0) {
      column = 0;
      softWrapColumns = 0;
    }
    if (line < 0) {
      line = 0;
      softWrapLinesBefore = 0;
      softWrapLinesCurrent = 0;
    }

    int lineCount = doc.getLineCount();
    if (lineCount == 0) {
      line = 0;
    }
    else if (line > lineCount - 1) {
      line = lineCount - 1;
      softWrapLinesBefore = 0;
      softWrapLinesCurrent = 0;
    }

    EditorSettings editorSettings = myEditor.getSettings();

    if (!editorSettings.isVirtualSpace() && line < lineCount) {
      int lineEndOffset = doc.getLineEndOffset(line);
      int lineEndColumnNumber = myEditor.offsetToLogicalPosition(lineEndOffset).column;
      if (column > lineEndColumnNumber) {
        column = lineEndColumnNumber;
        if (softWrapColumns != 0) {
          softWrapColumns -= column - lineEndColumnNumber;
        }
      }
    }

    ((FoldingModelImpl)myEditor.getFoldingModel()).flushCaretPosition();

    VerticalInfo oldInfo = myCaretInfo;
    LogicalPosition oldCaretPosition = myLogicalCaret;

    setCurrentLogicalCaret(new LogicalPosition(
      line, column, softWrapLinesBefore, softWrapLinesCurrent, softWrapColumns, pos.foldedLines, pos.foldingColumnDiff
    ));

    final int offset = myEditor.logicalPositionToOffset(myLogicalCaret);

    FoldRegion collapsedAt = myEditor.getFoldingModel().getCollapsedRegionAtOffset(offset);

    if (collapsedAt != null && offset > collapsedAt.getStartOffset()) {
      Runnable runnable = new Runnable() {
        public void run() {
          FoldRegion[] allCollapsedAt = ((FoldingModelImpl)myEditor.getFoldingModel()).fetchCollapsedAt(offset);
          for (FoldRegion foldRange : allCollapsedAt) {
            foldRange.setExpanded(true);
          }
        }
      };

      myEditor.getFoldingModel().runBatchFoldingOperation(runnable);
    }

    myEditor.setLastColumnNumber(myLogicalCaret.column);
    myVisibleCaret = myEditor.logicalToVisualPosition(myLogicalCaret);

    myOffset = myEditor.logicalPositionToOffset(myLogicalCaret);
    LOG.assertTrue(myOffset >= 0 && myOffset <= myEditor.getDocument().getTextLength());

    int caretOffset = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line, 0)));
    int caretLine = doc.getLineNumber(caretOffset);
    myVisualLineStart = doc.getLineStartOffset(caretLine);
    myVisualLineEnd = doc.getLineEndOffset(caretLine) + 1;

    myEditor.updateCaretCursor();
    requestRepaint(oldInfo);

    if (oldCaretPosition.column + oldCaretPosition.softWrapColumnDiff != myLogicalCaret.column + myLogicalCaret.softWrapColumnDiff
        || oldCaretPosition.line + oldCaretPosition.softWrapLinesBeforeCurrentLogicalLine != myLogicalCaret.line + myLogicalCaret.softWrapLinesBeforeCurrentLogicalLine)
    {
      CaretEvent event = new CaretEvent(myEditor, oldCaretPosition, myLogicalCaret);
      for (CaretListener listener : myCaretListeners) {
        listener.caretPositionChanged(event);
      }
    }
  }

  private void requestRepaint(VerticalInfo oldCaretInfo) {
    int lineHeight = myEditor.getLineHeight();
    Rectangle visibleArea = myEditor.getScrollingModel().getVisibleArea();
    final EditorGutterComponentEx gutter = myEditor.getGutterComponentEx();
    final EditorComponentImpl content = (EditorComponentImpl)myEditor.getContentComponent();

    int updateWidth = myEditor.getScrollPane().getHorizontalScrollBar().getValue() + visibleArea.width;
    if (Math.abs(myCaretInfo.y - oldCaretInfo.y) <= 2 * lineHeight) {
      int minY = Math.min(oldCaretInfo.y, myCaretInfo.y);
      int maxY = Math.max(oldCaretInfo.y + oldCaretInfo.height, myCaretInfo.y + myCaretInfo.height);
      content.repaintEditorComponent(0, minY, updateWidth, maxY - minY);
      gutter.repaint(0, minY, gutter.getWidth(), maxY - minY);
    }
    else {
      content.repaintEditorComponent(0, oldCaretInfo.y, updateWidth, oldCaretInfo.height + lineHeight);
      gutter.repaint(0, oldCaretInfo.y, updateWidth, oldCaretInfo.height + lineHeight);
      content.repaintEditorComponent(0, myCaretInfo.y, updateWidth, myCaretInfo.height + lineHeight);
      gutter.repaint(0, myCaretInfo.y, updateWidth, myCaretInfo.height + lineHeight);
    }
  }

  public LogicalPosition getLogicalPosition() {
    validateCallContext();
    return myLogicalCaret;
  }

  private void validateCallContext() {
    LOG.assertTrue(!myIsInUpdate, "Caret model is in its update process. All requests are illegal at this point.");
  }

  public VisualPosition getVisualPosition() {
    validateCallContext();
    return myVisibleCaret;
  }

  public int getOffset() {
    validateCallContext();
    return myOffset;
  }

  /**
   * There is a possible case that single logical line is spread to more than one visual lines because of soft wraps. This method
   * allows to receive information about vertical range occupied by the active logical line, i.e. it identifies
   * <code>'y'</code> coordinate of the first visual line that corresponds to the logical line and total height
   * of all visual lines that correspond to the active logical line.
   *
   * @return    object that encapsulates information about visual vertical range occupied by the current logical line on a screen
   */
  public VerticalInfo getVisualCaretInfo() {
    return myCaretInfo;
  }

  public int getVisualLineStart() {
    return myVisualLineStart;
  }

  public int getVisualLineEnd() {
    return myVisualLineEnd;
  }

  public void addCaretListener(CaretListener listener) {
    myCaretListeners.add(listener);
  }

  public void removeCaretListener(CaretListener listener) {
    boolean success = myCaretListeners.remove(listener);
    LOG.assertTrue(success);
  }

  public TextAttributes getTextAttributes() {
    if (myTextAttributes == null) {
      myTextAttributes = new TextAttributes();
      myTextAttributes.setBackgroundColor(myEditor.getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR));
    }

    return myTextAttributes;
  }

  public void reinitSettings() {
    myTextAttributes = null;
  }

  public void documentChanged(DocumentEvent e) {
    myIsInUpdate = false;

    DocumentEventImpl event = (DocumentEventImpl)e;
    final Document document = myEditor.getDocument();

    if (event.isWholeTextReplaced()) {
      int newLength = document.getTextLength();
      if (myOffset == newLength - e.getNewLength() + e.getOldLength() || newLength == 0) {
        moveToOffset(newLength);
      }
      else {
        final int line = event.translateLineViaDiff(myLogicalCaret.line);
        moveToLogicalPosition(new LogicalPosition(line, myLogicalCaret.column));
      }
    }
    else {
      if (document instanceof DocumentEx && ((DocumentEx)document).isInBulkUpdate()) return;
      int startOffset = e.getOffset();
      int oldEndOffset = startOffset + e.getOldLength();

      int newOffset = myOffset;

      if (myOffset > oldEndOffset || myOffset == oldEndOffset && needToShiftWhiteSpaces(e)) {
        newOffset += e.getNewLength() - e.getOldLength();
      }
      else if (myOffset >= startOffset && myOffset <= oldEndOffset) {
        newOffset = Math.min(newOffset, startOffset + e.getNewLength());
      }

      newOffset = Math.min(newOffset, document.getTextLength());

      //TODO:ask max about this code
      // if (newOffset != myOffset) {
        moveToOffset(newOffset);
      //}
      //else {
      //  moveToVisualPosition(oldPosition);
      //}
    }

    myVisualLineStart = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line, 0)));
    myVisualLineEnd = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line + 1, 0)));
  }

  private boolean needToShiftWhiteSpaces(final DocumentEvent e) {
    if(!CharArrayUtil.containsOnlyWhiteSpaces(e.getNewFragment()) || CharArrayUtil.containLineBreaks(e.getNewFragment()))
      return e.getOldLength() > 0;
    if(e.getOffset() == 0) return false;
    final char charBefore = myEditor.getDocument().getCharsSequence().charAt(e.getOffset() - 1);
    //final char charAfter = myEditor.getDocument().getCharsSequence().charAt(e.getOffset() + e.getNewLength());
    return Character.isWhitespace(charBefore)/* || !Character.isWhitespace(charAfter)*/;
  }

  public void beforeDocumentChange(DocumentEvent e) {
    myIsInUpdate = true;
  }

  public int getPriority() {
    return 3;
  }

  private void setCurrentLogicalCaret(LogicalPosition position) {
    myLogicalCaret = position;
    myCaretInfo = createVerticalInfo(position);
  }

  private VerticalInfo createVerticalInfo(LogicalPosition position) {
    Document document = myEditor.getDocument();
    int line = position.line;

    // There is a possible case that active logical line is represented on multiple lines due to soft wraps processing.
    // We want to highlight those visual lines as 'active' then, so, we calculate 'y' position for the logical line start
    // and height in accordance with the number of occupied visual lines.
    int y = myEditor.logicalPositionToXY(myEditor.offsetToLogicalPosition(document.getLineStartOffset(line))).y;
    int height = myEditor.getLineHeight();
    if (line < document.getLineCount() - 1) {
      int nextLineY = myEditor.logicalPositionToXY(myEditor.offsetToLogicalPosition(document.getLineStartOffset(line + 1))).y;
      int heightCandidate = nextLineY - y;

      // There is a possible case that active line is the one that ends with folding, so, 'y' position
      // of its next logical line is the same as the previous. We explicitly check that in order to use non-standard
      // line height only in case of visible soft-wrapped line.
      if (heightCandidate > height) {
        height = heightCandidate;
      }
    }
    return new VerticalInfo(y, height);
  }

  /**
   * Encapsulates information about target vertical range info - its <code>'y'</code> coordinate and height in pixels.
   */
  public static class VerticalInfo {
    public final int y;
    public final int height;

    private VerticalInfo(int y, int height) {
      this.y = y;
      this.height = height;
    }
  }
}
