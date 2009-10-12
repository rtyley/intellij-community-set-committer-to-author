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
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.GotoLineNumberDialog;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.UIBundle;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class PositionPanel extends TextPanel implements StatusBarPatch {
  public PositionPanel(StatusBar statusBar) {
    super(false, "#############");

    addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2) {
          final Project project = getProject();
          if (project == null) return;
          final Editor editor = getEditor(project);
          if (editor == null) return;
          final CommandProcessor processor = CommandProcessor.getInstance();
          processor.executeCommand(
              project, new Runnable(){
              public void run() {
                final GotoLineNumberDialog dialog = new GotoLineNumberDialog(project, editor);
                dialog.show();
                IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
              }
            },
              UIBundle.message("go.to.line.command.name"),
            null
          );
        }
      }
    });

    StatusBarTooltipper.install(this, statusBar);
  }

  public JComponent getComponent() {
    return this;
  }

  public String updateStatusBar(final Editor editor, final JComponent componentSelected) {
    if (editor != null) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (!editor.isDisposed()) {
            StringBuilder message = new StringBuilder();

            SelectionModel selectionModel = editor.getSelectionModel();
            if (selectionModel.hasBlockSelection()) {
              LogicalPosition start = selectionModel.getBlockStart();
              LogicalPosition end = selectionModel.getBlockEnd();
              appendLogicalPosition(start, message);
              message.append("-");
              appendLogicalPosition(new LogicalPosition(Math.abs(end.line - start.line), Math.abs(end.column - start.column) - 1), message);
            }
            else {
              LogicalPosition caret = editor.getCaretModel().getLogicalPosition();

              appendLogicalPosition(caret, message);
              if (selectionModel.hasSelection()) {
                int len = Math.abs(selectionModel.getSelectionStart() - selectionModel.getSelectionEnd());
                message.append("/");
                message.append(len);
              }
            }

            setText(message.toString());
          }
        }
      });
      return UIBundle.message("go.to.line.command.double.click");
    }
    clear();
    return null;
  }

  private static void appendLogicalPosition(LogicalPosition caret, StringBuilder message) {
    message.append(caret.line + 1);
    message.append(":");
    message.append(caret.column + 1);
  }

  public void clear() {
    setText("");
  }

  private static Editor getEditor(final Project project) {
    return FileEditorManager.getInstance(project).getSelectedTextEditor();
  }

  private Project getProject() {
    return PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this));
  }
}
