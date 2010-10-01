/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.scriptingContext.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.ui.components.JBList;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingLibrariesPanel {
  private JPanel myTopPanel;
  private JButton myAddLibrarytButton;
  private JButton myRemoveLibraryButton;
  private JButton myEditLibrarytButton;
  private JPanel myScriptingLibrariesPanel;
  private JBTable myLibraryTable;

  public ScriptingLibrariesPanel(LibraryTable libTable) {
    myLibraryTable.setModel(new ScriptingLibraryTableModel(libTable));
    myAddLibrarytButton.addActionListener(new ActionListener(){
      @Override
      public void actionPerformed(ActionEvent e) {
        addLibrary();
      }
    });
  }

  public JPanel getPanel() {
    return myTopPanel;
  }

  private void addLibrary() {
    EditLibraryDialog editLibDialog = new EditLibraryDialog();
    editLibDialog.show();
    if (editLibDialog.isOK()) {
      //TODO: Implement
    }
  }

}
