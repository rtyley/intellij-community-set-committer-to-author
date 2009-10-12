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
package com.intellij.ide.actions;

import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;

public class UndoAction extends UndoRedoAction implements DumbAware {
  @Override
  protected boolean isAvailable(FileEditor editor, UndoManager undoManager) {
    return undoManager.isUndoAvailable(editor);
  }

  @Override
  protected void perform(FileEditor editor, UndoManager undoManager) {
    undoManager.undo(editor);
  }

  @Override
  protected String formatAction(FileEditor editor, UndoManager undoManager) {
    return undoManager.formatAvailableUndoAction(editor);
  }

  protected String getActionMessageKey() {
    return "action.$Undo.text";
  }

  @Override
  protected String getActionDescriptionMessageKey() {
    return "action.$Undo.description";
  }

  @Override
  protected String getActionDescriptionEmptyMessageKey() {
    return "action.$Undo.description.empty";
  }
}
