/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.dir;

import com.intellij.ide.diff.DiffElement;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.diff.impl.dir.actions.DirDiffToolbarActions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.Icons;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffPanel {
  public static final JBLabel CANT_OPEN_LABEL = new JBLabel("Can't open file content", SwingConstants.CENTER);
  private JPanel myDiffPanel;
  private JBTable myTable;
  private JPanel myComponent;
  private JSplitPane mySplitPanel;
  private TextFieldWithBrowseButton mySourceDirField;
  private TextFieldWithBrowseButton myTargetDirField;
  private JBLabel myTargetDirLabel;
  private JBLabel mySourceDirLabel;
  private JPanel myActionsPanel;
  private JPanel myActionsCenterPanel;
  private JComboBox myFileFilter;
  private JPanel myToolBarPanel;
  private final DirDiffTableModel myModel;
  private final DirDiffDialog myDialog;
  private JComponent myDiffPanelComponent;
  private JComponent myViewComponent;
  private DiffElement myCurrentElement;

  public DirDiffPanel(DirDiffTableModel model, DirDiffDialog dirDiffDialog) {
    myModel = model;
    myDialog = dirDiffDialog;
    mySourceDirField.setText(model.getSourceDir().getPath());
    myTargetDirField.setText(model.getTargetDir().getPath());
    mySourceDirLabel.setIcon(Icons.FOLDER_ICON);
    myTargetDirLabel.setIcon(Icons.FOLDER_ICON);
    myTable.setModel(myModel);
    final DirDiffTableCellRenderer renderer = new DirDiffTableCellRenderer(myTable);
    myTable.setDefaultRenderer(Object.class, renderer);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        final DirDiffElement last = myModel.getElementAt(e.getLastIndex());
        final DirDiffElement first = myModel.getElementAt(e.getFirstIndex());
        if (last.isSeparator()) {
          myTable.getSelectionModel().setLeadSelectionIndex(e.getFirstIndex());
        }
        else if (first.isSeparator()) {
          myTable.getSelectionModel().setLeadSelectionIndex(e.getLastIndex());
        }
        else {
          final DirDiffElement element = myModel.getElementAt(myTable.getSelectedRow());
          final Project project = myModel.getProject();
          clearDiffPanel();
          if (element.getType() == DirDiffElement.ElementType.CHANGED) {
            myDiffPanelComponent = element.getSource().getDiffComponent(element.getTarget(), project, myDialog.getWindow());
            if (myDiffPanelComponent != null) {
              myDiffPanel.add(myDiffPanelComponent, BorderLayout.CENTER);
              myCurrentElement = element.getSource();
            }

          } else {
            final DiffElement object = element.isSource() ? element.getSource() : element.getTarget();
            myViewComponent = object.getViewComponent(project);

            if (myViewComponent != null) {
              myCurrentElement = object;
              myDiffPanel.add(myViewComponent, BorderLayout.CENTER);
            } else {
              myDiffPanel.add(CANT_OPEN_LABEL, BorderLayout.CENTER);
            }

            if (myViewComponent != null) {
              myViewComponent.revalidate();
            } else {
              myDiffPanel.repaint();
            }
          }
        }
        myDialog.setTitle(myModel.getTitle());
      }
    });
    myTable.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        final int keyCode = e.getKeyCode();
        final int rows = myTable.getRowCount();
        int row = myTable.getSelectedRow();
        if (keyCode == KeyEvent.VK_DOWN && row != rows - 1) {
          row++;
          if (myModel.getElementAt(row).isSeparator()) {
            row++;
          }
        } else if (keyCode == KeyEvent.VK_UP && row != 0) {
          row--;
          if (myModel.getElementAt(row).isSeparator()) {
            row--;
          }
        } else {
          return;
        }
        if (0 <= row && row < rows && !myModel.getElementAt(row).isSeparator()) {
          e.consume();
          myTable.changeSelection(row, 3, false, false);
        }
      }
    });
    final TableColumn operationColumn = myTable.getColumnModel().getColumn(3);
    operationColumn.setMaxWidth(25);
    operationColumn.setMinWidth(25);
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("DirDiff", new DirDiffToolbarActions(myModel), true);
    myToolBarPanel.add(toolbar.getComponent(), BorderLayout.CENTER);
  }

  private void clearDiffPanel() {
    if (myDiffPanelComponent != null) {
      myDiffPanel.remove(myDiffPanelComponent);
      myDiffPanelComponent = null;
      if (myCurrentElement != null) {
        myCurrentElement.disposeDiffComponent();
      }
    }
    if (myViewComponent != null) {
      myDiffPanel.remove(myViewComponent);
      myViewComponent = null;
      if (myCurrentElement != null) {
        myCurrentElement.disposeViewComponent();
      }
    }
    myCurrentElement = null;
    myDiffPanel.remove(CANT_OPEN_LABEL);
  }

  private void createUIComponents() {
  }

  public JComponent getPanel() {
    return myComponent;
  }

  public JBTable getTable() {
    return myTable;
  }

  public JSplitPane getSplitPanel() {
    return mySplitPanel;
  }

  public void dispose() {
    clearDiffPanel();
  }
}
