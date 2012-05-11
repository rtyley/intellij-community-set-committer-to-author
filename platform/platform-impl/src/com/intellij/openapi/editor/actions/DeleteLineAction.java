/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
 * Date: May 14, 2002
 * Time: 7:18:30 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;

public class DeleteLineAction extends TextComponentEditorAction {
  public DeleteLineAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    @Override
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      SelectionModel selectionModel = editor.getSelectionModel();

      if (selectionModel.hasSelection()) {
        Document document = editor.getDocument();
        int selectionStart = selectionModel.getSelectionStart();
        int selectionEnd = selectionModel.getSelectionEnd();
        selectionModel.removeSelection();
        int lineStartOffset = document.getLineStartOffset(document.getLineNumber(selectionStart));
        final int nextLine = document.getLineNumber(selectionEnd) + 1;
        int nextLineStartOffset = nextLine == document.getLineCount()
                                  ? document.getTextLength()
                                  : Math.min(document.getTextLength(), document.getLineStartOffset(nextLine));
        document.deleteString(lineStartOffset, nextLineStartOffset);
        return;
      }
      editor.getSelectionModel().selectLineAtCaret();
      EditorModificationUtil.deleteSelectedText(editor);
    }
  }
}
