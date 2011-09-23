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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapHelper;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

public class CaretModelImpl implements CaretModel, PrioritizedDocumentListener, Disposable {
  
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.CaretModelImpl");
  
  private final EditorImpl myEditor;
  private final List<CaretListener> myCaretListeners = ContainerUtil.createEmptyCOWList();
  
  private LogicalPosition myLogicalCaret;
  private VerticalInfo myCaretInfo;
  private VisualPosition myVisibleCaret;
  private int myOffset;
  private int myVisualLineStart;
  private int myVisualLineEnd;
  private TextAttributes myTextAttributes;
  private boolean myIsInUpdate;
  private RangeMarker savedBeforeBulkCaretMarker;
  private boolean ignoreWrongMoves = false;

  /**
   * There is a possible case that user defined non-monospaced font for editor. That means that various symbols have different
   * visual widths. That means that if we move caret vertically it may deviate to the left/right. However, we can try to preserve
   * its initial visual position when possible.
   * <p/>
   * This field holds desired value for visual <code>'x'</code> caret coordinate (negative value if no coordinate should be preserved).
   */
  private int myDesiredX = -1;

  public CaretModelImpl(EditorImpl editor) {
    myEditor = editor;
    myLogicalCaret = new LogicalPosition(0, 0);
    myVisibleCaret = new VisualPosition(0, 0);
    myCaretInfo = new VerticalInfo(0, 0);
    myOffset = 0;
    myVisualLineStart = 0;
    Document doc = editor.getDocument();
    myVisualLineEnd = doc.getLineCount() > 1 ? doc.getLineStartOffset(1) : doc.getLineCount() == 0 ? 0 : doc.getLineEndOffset(0);
    DocumentBulkUpdateListener bulkUpdateListener = new DocumentBulkUpdateListener() {
      @Override
      public void updateStarted(Document doc) {
        if (doc != myEditor.getDocument()) return;
        savedBeforeBulkCaretMarker = doc.createRangeMarker(myOffset, myOffset);
      }
      @Override
      public void updateFinished(Document doc) {
        if (doc != myEditor.getDocument() || myIsInUpdate) return;
        if (savedBeforeBulkCaretMarker != null && savedBeforeBulkCaretMarker.isValid()) {
          moveToOffset(savedBeforeBulkCaretMarker.getStartOffset());
        }
        releaseBulkCaretMarker();
      }
    };
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(DocumentBulkUpdateListener.TOPIC, bulkUpdateListener);
  }

  private void releaseBulkCaretMarker() {
    if (savedBeforeBulkCaretMarker != null) {
      savedBeforeBulkCaretMarker.dispose();
      savedBeforeBulkCaretMarker = null;
    }
  }

  public void moveToVisualPosition(@NotNull VisualPosition pos) {
    assertIsDispatchThread();
    validateCallContext();
    myDesiredX = -1;
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
    moveToOffset(offset, false);
  }

  public void moveToOffset(int offset, boolean locateBeforeSoftWrap) {
    assertIsDispatchThread();
    validateCallContext();
    moveToLogicalPosition(myEditor.offsetToLogicalPosition(offset), locateBeforeSoftWrap);
    if (!ignoreWrongMoves && !myEditor.offsetToLogicalPosition(myOffset).equals(myEditor.offsetToLogicalPosition(offset))) {
      LOG.error(
        "caret moved to wrong offset. Requested:" + offset + " but actual:" + myOffset 
        + ". Soft wraps data: " + myEditor.getSoftWrapModel() + ", folding data: " + myEditor.getFoldingModel());
    }
  }

  public void setIgnoreWrongMoves(boolean ignoreWrongMoves) {
    this.ignoreWrongMoves = ignoreWrongMoves;
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

    int desiredX = myDesiredX;
    if (columnShift == 0) {
      if (myDesiredX < 0) {
        desiredX = myEditor.visualPositionToXY(visualCaret).x;
      }
    }
    else {
      myDesiredX = desiredX = -1;
    }

    int newLineNumber = visualCaret.line + lineShift;
    int newColumnNumber = visualCaret.column + columnShift;
    if (desiredX >= 0 && !ApplicationManager.getApplication().isUnitTestMode()) {
      newColumnNumber = myEditor.xyToVisualPosition(new Point(desiredX, Math.max(0, newLineNumber) * myEditor.getLineHeight())).column;
    }

    Document document = myEditor.getDocument();
    if (!editorSettings.isVirtualSpace() && columnShift == 0 && getLogicalPosition().softWrapLinesOnCurrentLogicalLine <= 0) {
      newColumnNumber = myEditor.getLastColumnNumber();
    }
    else if (!editorSettings.isVirtualSpace() && lineShift == 0 && columnShift == 1) {
      int lastLine = document.getLineCount() - 1;
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

    // There is a possible case that caret is located at the first line and user presses 'Shift+Up'. We want to select all text
    // from the document start to the current caret position then. So, we have a dedicated flag for tracking that.
    boolean selectToDocumentStart = false;
    if (newLineNumber < 0) {
      selectToDocumentStart = true;
      newLineNumber = 0;

      // We want to move caret to the first column if it's already located at the first line and 'Up' is pressed.
      newColumnNumber = 0;
      desiredX = -1;
    }

    VisualPosition pos = new VisualPosition(newLineNumber, newColumnNumber);
    int lastColumnNumber = newColumnNumber;
    if (!editorSettings.isCaretInsideTabs() && !myEditor.getSoftWrapModel().isInsideSoftWrap(pos)) {
      LogicalPosition log = myEditor.visualToLogicalPosition(new VisualPosition(newLineNumber, newColumnNumber));
      int offset = myEditor.logicalPositionToOffset(log);
      if (offset >= document.getTextLength()) {
        int lastOffsetColumn = myEditor.offsetToVisualPosition(document.getTextLength()).column;
        // We want to move caret to the last column if if it's located at the last line and 'Down' is pressed.
        newColumnNumber = lastColumnNumber = Math.max(lastOffsetColumn, newColumnNumber);
        desiredX = -1;
      }
      CharSequence text = document.getCharsSequence();
      if (offset >= 0 && offset < document.getTextLength()) {
        if (text.charAt(offset) == '\t' && (columnShift <= 0 || offset == myOffset)) {
          if (columnShift <= 0) {
            newColumnNumber = myEditor.offsetToVisualPosition(offset).column;
          }
          else {
            SoftWrap softWrap = myEditor.getSoftWrapModel().getSoftWrap(offset + 1);
            // There is a possible case that tabulation symbol is the last document symbol represented on a visual line before
            // soft wrap. We can't just use column from 'offset + 1' because it would point on a next visual line.
            if (softWrap == null) {
              newColumnNumber = myEditor.offsetToVisualPosition(offset + 1).column;
            }
            else {
              newColumnNumber = EditorUtil.getLastVisualLineColumnNumber(myEditor, newLineNumber);
            }
          }
        }
      }
    }

    pos = new VisualPosition(newLineNumber, newColumnNumber);
    if (columnShift != 0 && lineShift == 0 && myEditor.getSoftWrapModel().isInsideSoftWrap(pos)) {
      LogicalPosition logical = myEditor.visualToLogicalPosition(pos);
      int softWrapOffset = myEditor.logicalPositionToOffset(logical);
      if (columnShift >= 0) {
        moveToOffset(softWrapOffset);
      }
      else {
        int line = myEditor.offsetToVisualLine(softWrapOffset - 1);
        moveToVisualPosition(new VisualPosition(line, EditorUtil.getLastVisualLineColumnNumber(myEditor, line)));
      }
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
        if (selectToDocumentStart) {
          selectionModel.setSelection(selectionStart, 0);
        }
        else if (pos.line >= myEditor.getVisibleLineCount()) {
          if (selectionStart < document.getTextLength()) {
            selectionModel.setSelection(selectionStart, document.getTextLength());
          }
        }
        else {
          selectionModel.setSelection(selectionStart, getVisualPosition(), getOffset());
        }
      }
    }
    else {
      selectionModel.removeSelection();
    }

    if (scrollToCaret) {
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
    
    if (desiredX >= 0) {
      myDesiredX = desiredX;
    }

    selectNonexpandableFold();
  }

  LogicalPosition prevPos = null;
  public void selectNonexpandableFold() {
    final LogicalPosition pos = getLogicalPosition();
    
    if (prevPos == null) {
      prevPos = pos;
    }
    else {
      int columnShift = pos.line == prevPos.line ? pos.column - prevPos.column : 0;
      prevPos = pos;

      final FoldRegion collapsedUndeCaret = myEditor.getFoldingModel().getCollapsedRegionAtOffset(getOffset());
      if (collapsedUndeCaret != null && collapsedUndeCaret.shouldNeverExpand()) {
        if (columnShift > 0) {
          moveToOffset(collapsedUndeCaret.getEndOffset());
        }
        else if (columnShift < 0) {
          moveToOffset(collapsedUndeCaret.getStartOffset());
        }
        myEditor.getSelectionModel().setSelection(collapsedUndeCaret.getStartOffset(), collapsedUndeCaret.getEndOffset());
      }
    }

  }

  public void moveToLogicalPosition(@NotNull LogicalPosition pos) {
    moveToLogicalPosition(pos, false);
  }

  private void moveToLogicalPosition(LogicalPosition pos, boolean locateBeforeSoftWrap) {
    assertIsDispatchThread();
    myDesiredX = -1;
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

    if (!editorSettings.isVirtualSpace() && line < lineCount && !myEditor.getSelectionModel().hasBlockSelection()) {
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

    LogicalPosition logicalPositionToUse;
    if (pos.visualPositionAware) {
      logicalPositionToUse = new LogicalPosition(
        line, column, softWrapLinesBefore, softWrapLinesCurrent, softWrapColumns, pos.foldedLines, pos.foldingColumnDiff
      );
    }
    else {
      logicalPositionToUse = new LogicalPosition(line, column);
    }
    setCurrentLogicalCaret(logicalPositionToUse);

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

    myVisualLineStart = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line, 0)));
    myVisualLineEnd = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line + 1, 0)));

    myEditor.updateCaretCursor();
    requestRepaint(oldInfo);

    if (locateBeforeSoftWrap && SoftWrapHelper.isCaretAfterSoftWrap(myEditor)) {
      int lineToUse = myVisibleCaret.line - 1;
      if (lineToUse >= 0) {
        moveToVisualPosition(new VisualPosition(lineToUse, EditorUtil.getLastVisualLineColumnNumber(myEditor, lineToUse)));
        return;
      }
    }

    if (!oldCaretPosition.toVisualPosition().equals(myLogicalCaret.toVisualPosition())) {
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
    final EditorComponentImpl content = myEditor.getContentComponent();

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

  @Override
  public boolean isUpToDate() {
    return !myIsInUpdate;
  }

  @NotNull
  public LogicalPosition getLogicalPosition() {
    validateCallContext();
    return myLogicalCaret;
  }

  private void validateCallContext() {
    LOG.assertTrue(!myIsInUpdate, "Caret model is in its update process. All requests are illegal at this point.");
  }

  @NotNull
  public VisualPosition getVisualPosition() {
    validateCallContext();
    return myVisibleCaret;
  }

  public int getOffset() {
    validateCallContext();
    return myOffset;
  }

  public int getVisualLineStart() {
    return myVisualLineStart;
  }

  public int getVisualLineEnd() {
    return myVisualLineEnd;
  }

  public void addCaretListener(@NotNull CaretListener listener) {
    myCaretListeners.add(listener);
  }

  public void removeCaretListener(@NotNull CaretListener listener) {
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
    finishUpdate();

    DocumentEventImpl event = (DocumentEventImpl)e;
    final Document document = myEditor.getDocument();
    boolean performSoftWrapAdjustment = e.getNewLength() > 0 // We want to put caret just after the last added symbol
      // There is a possible case that the user removes text just before the soft wrap. We want to keep caret
      // on a visual line with soft wrap start then.
      || myEditor.getSoftWrapModel().getSoftWrap(e.getOffset()) != null;

    if (event.isWholeTextReplaced()) {
      int newLength = document.getTextLength();
      if (myOffset == newLength - e.getNewLength() + e.getOldLength() || newLength == 0) {
        moveToOffset(newLength, performSoftWrapAdjustment);
      }
      else {
        final int line;
        try {
          line = event.translateLineViaDiff(myLogicalCaret.line);
          moveToLogicalPosition(new LogicalPosition(line, myLogicalCaret.column), performSoftWrapAdjustment);
        }
        catch (FilesTooBigForDiffException e1) {
          LOG.info(e1);
          moveToOffset(0);
        }
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

      // if (newOffset != myOffset) {
        moveToOffset(newOffset, performSoftWrapAdjustment);
      //}
      //else {
      //  moveToVisualPosition(oldPosition);
      //}
    }

    myVisualLineStart = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line, 0)));
    myVisualLineEnd = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line + 1, 0)));
  }

  public void finishUpdate() {
    myIsInUpdate = false;
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
    return EditorDocumentPriorities.CARET_MODEL;
  }

  private void setCurrentLogicalCaret(@NotNull LogicalPosition position) {
    myLogicalCaret = position;
    myCaretInfo = createVerticalInfo(position);
  }

  private VerticalInfo createVerticalInfo(LogicalPosition position) {
    Document document = myEditor.getDocument();
    int logicalLine = position.line;
    if (logicalLine >= document.getLineCount()) {
      logicalLine = Math.max(0, document.getLineCount() - 1);
    }
    int startOffset = document.getLineStartOffset(logicalLine);
    int endOffset = document.getLineEndOffset(logicalLine);

    // There is a possible case that active logical line is represented on multiple lines due to soft wraps processing.
    // We want to highlight those visual lines as 'active' then, so, we calculate 'y' position for the logical line start
    // and height in accordance with the number of occupied visual lines.
    VisualPosition visualPosition = myEditor.offsetToVisualPosition(document.getLineStartOffset(logicalLine));
    int y = myEditor.visualPositionToXY(visualPosition).y;
    int lineHeight = myEditor.getLineHeight();
    int height = lineHeight;
    List<? extends SoftWrap> softWraps = myEditor.getSoftWrapModel().getSoftWrapsForRange(startOffset, endOffset);
    for (SoftWrap softWrap : softWraps) {
      height += StringUtil.countNewLines(softWrap.getText()) * lineHeight;
    }

    return new VerticalInfo(y, height);
  }

  @Override
  public void dispose() {
    releaseBulkCaretMarker();
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
