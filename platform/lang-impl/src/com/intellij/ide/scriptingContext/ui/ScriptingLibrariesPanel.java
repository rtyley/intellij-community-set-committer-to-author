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

import com.intellij.ide.scriptingContext.LangScriptingContextProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.scripting.ScriptingLibraryManager;
import com.intellij.openapi.roots.libraries.scripting.ScriptingLibraryTable;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingLibrariesPanel {
  private JPanel myTopPanel;
  private JButton myAddLibraryButton;
  private JButton myRemoveLibraryButton;
  private JButton myEditLibraryButton;
  private JPanel myScriptingLibrariesPanel;
  private JBTable myLibraryTable;
  private ScriptingLibraryTableModel myLibTableModel;
  private String mySelectedLibName;
  private Project myProject;
  private LangScriptingContextProvider myProvider;

  public ScriptingLibrariesPanel(LangScriptingContextProvider provider, Project project, ScriptingLibraryManager libraryManager) {
    myProvider = provider;
    myLibTableModel = new ScriptingLibraryTableModel(libraryManager);
    myLibraryTable.setModel(myLibTableModel);
    myAddLibraryButton.addActionListener(new ActionListener(){
      @Override
      public void actionPerformed(ActionEvent e) {
        addLibrary();
      }
    });
    myRemoveLibraryButton.addActionListener(new ActionListener(){
      @Override
      public void actionPerformed(ActionEvent e) {
        if (mySelectedLibName != null) {
          removeLibrary(mySelectedLibName);
        }
      }
    });
    myEditLibraryButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        editLibrary(mySelectedLibName);
      }
    });
    myRemoveLibraryButton.setEnabled(false);
    myEditLibraryButton.setEnabled(false);
    myLibraryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myProject = project;
    myLibraryTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        onSelectionChange();
      }
    });
  }

  public JPanel getPanel() {
    return myTopPanel;
  }

  private void removeLibrary(final String libName) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myLibTableModel.removeLibrary(libName);
      }
    });
  }

  private void addLibrary() {
    EditLibraryDialog editLibDialog = new EditLibraryDialog("New Library", myProvider, myProject);
    editLibDialog.show();
    if (editLibDialog.isOK()) {
      myLibTableModel.createLibrary(editLibDialog.getLibName(), editLibDialog.getFiles());
    }
  }

  public boolean isModified() {
    return myLibTableModel.isChanged();
  }

  public void resetTable() {
    myLibTableModel.resetTable();
  }

  private void onSelectionChange() {
    int selectedRow = myLibraryTable.getSelectedRow();
    if (selectedRow >= 0) {
      mySelectedLibName = myLibTableModel.getLibNameAt(selectedRow);
      myEditLibraryButton.setEnabled(true);
      myRemoveLibraryButton.setEnabled(true);
    }
    else {
      myEditLibraryButton.setEnabled(false);
      myRemoveLibraryButton.setEnabled(false);
    }
  }

  private void editLibrary(String libName) {
    if (libName == null) return;
    ScriptingLibraryTable.LibraryModel lib = myLibTableModel.getLibrary(libName);
    if (lib != null) {
      EditLibraryDialog editLibDialog = new EditLibraryDialog("Edit Library", myProvider, myProject, lib);
      editLibDialog.show();
      if (editLibDialog.isOK()) {
        myLibTableModel.updateLibrary(libName, editLibDialog.getLibName(), editLibDialog.getFiles());
      }
    }
  }

}
