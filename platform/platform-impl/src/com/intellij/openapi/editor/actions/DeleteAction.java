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
 * Date: May 13, 2002
 * Time: 8:20:36 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.util.text.StringUtil;

public class DeleteAction extends EditorAction {
  public DeleteAction() {
    super(new Handler());
  }

  public static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      SelectionModel selectionModel = editor.getSelectionModel();
      if (selectionModel.hasBlockSelection()) {
        final LogicalPosition start = selectionModel.getBlockStart();
        final LogicalPosition end = selectionModel.getBlockEnd();
        if (start.column == end.column) {
          int column = start.column;
          int startLine = Math.min(start.line, end.line);
          int endLine = Math.max(start.line, end.line);
          for (int i = startLine; i <= endLine; i++) {
            editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(i, column));
            deleteCharAtCaret(editor);
          }
          selectionModel.setBlockSelection(new LogicalPosition(startLine, column), new LogicalPosition(endLine, column));
          return;
        }
        EditorModificationUtil.deleteBlockSelection(editor);
      }
      else if (!selectionModel.hasSelection()) {
        deleteCharAtCaret(editor);
      } else {
        EditorModificationUtil.deleteSelectedText(editor);
      }
    }
  }

  private static int getCaretLineLength(Editor editor) {
    Document document = editor.getDocument();
    if(document.getLineCount() == 0)
      return 0;
    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    if(lineNumber >= document.getLineCount()) {
      return 0;
    }
    else {
      return document.getLineEndOffset(lineNumber) - document.getLineStartOffset(lineNumber);
    }
  }

  private static int getCaretLineStart(Editor editor) {
    Document document = editor.getDocument();
    if(document.getLineCount() == 0)
      return 0;
    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    if(lineNumber >= document.getLineCount()) {
      return document.getLineStartOffset(document.getLineCount() - 1);
    }
    else {
      return document.getLineStartOffset(lineNumber);
    }
  }

  public static void deleteCharAtCaret(Editor editor) {
    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    int afterLineEnd = EditorModificationUtil.calcAfterLineEnd(editor);
    Document document = editor.getDocument();
    if(afterLineEnd < 0) {
      int offset = editor.getCaretModel().getOffset();
      document.deleteString(offset, offset + 1);
      editor.getCaretModel().moveToOffset(offset);
      return;
    }
    if(lineNumber + 1 >= document.getLineCount())
      return;

    // Do not group delete newline and other deletions.
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.setCurrentCommandGroupId(null);

    int nextLineStart = document.getLineStartOffset(lineNumber + 1);
    int nextLineEnd = document.getLineEndOffset(lineNumber + 1);
    if(nextLineEnd - nextLineStart > 0) {
      StringBuilder buf = new StringBuilder();
      StringUtil.repeatSymbol(buf, ' ', afterLineEnd);
      document.insertString(getCaretLineStart(editor) + getCaretLineLength(editor), buf.toString());
      nextLineStart = document.getLineStartOffset(lineNumber + 1);
    }
    int thisLineEnd = document.getLineEndOffset(lineNumber);
    document.deleteString(thisLineEnd, nextLineStart);
  }
}
