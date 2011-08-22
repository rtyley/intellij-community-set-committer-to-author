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
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.ElementProducer;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableModel;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
class TableToolbarDecorator extends ToolbarDecorator {
  private final JTable myTable;
  private TableModel myTableModel;

  TableToolbarDecorator(@NotNull JTable table, @Nullable final ElementProducer<?> producer) {
    myTable = table;
    myTableModel = table.getModel();
    myAddActionEnabled = myRemoveActionEnabled = myUpActionEnabled = myDownActionEnabled = myTableModel instanceof EditableModel;
    if (myTableModel instanceof EditableModel) {
      createDefaultTableActions(producer);
    }
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateButtons();
      }
    });
  }

  @Override
  protected JComponent getComponent() {
    return myTable;
  }

  protected void updateButtons() {
    final AddRemoveUpDownPanel p = getPanel();
    if (myTable.isEnabled() && p != null) {
      final int index = myTable.getSelectedRow();
      final int size = myTableModel.getRowCount();
      if (0 <= index && index < size) {
        final boolean downEnable = myTable.getSelectionModel().getMaxSelectionIndex() < size - 1;
        final boolean upEnable = myTable.getSelectionModel().getMinSelectionIndex() > 0;
        p.setEnabled(AddRemoveUpDownPanel.Buttons.REMOVE, true);
        p.setEnabled(AddRemoveUpDownPanel.Buttons.UP, upEnable);
        p.setEnabled(AddRemoveUpDownPanel.Buttons.DOWN, downEnable);
      } else {
        p.setEnabled(AddRemoveUpDownPanel.Buttons.REMOVE, false);
        p.setEnabled(AddRemoveUpDownPanel.Buttons.UP, false);
        p.setEnabled(AddRemoveUpDownPanel.Buttons.DOWN, false);
      }
      p.setEnabled(AddRemoveUpDownPanel.Buttons.ADD, true);
    }
  }

  private void createDefaultTableActions(@Nullable final ElementProducer<?> producer) {
    final JTable table = myTable;
    final EditableModel tableModel = (EditableModel)myTableModel;

    myAddAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        TableUtil.stopEditing(table);
        final int rowCount = table.getRowCount();
        if (tableModel instanceof ListTableModel && producer != null) {
          //noinspection unchecked
          ((ListTableModel)tableModel).addRow(producer.createElement());
        } else {
          tableModel.addRow();
        }
        if (rowCount == table.getRowCount()) return;
        final int index = myTableModel.getRowCount() - 1;
        table.editCellAt(index, 0);
        table.setRowSelectionInterval(index, index);
        table.setColumnSelectionInterval(0, 0);
        table.getParent().repaint();
        final Component editorComponent = table.getEditorComponent();
        if (editorComponent != null) {
          final Rectangle bounds = editorComponent.getBounds();
          table.scrollRectToVisible(bounds);
          editorComponent.requestFocus();
        }
      }
    };

    myRemoveAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        TableUtil.stopEditing(table);
        int index = table.getSelectedRow();
        if (0 <= index && index < myTableModel.getRowCount()) {
          tableModel.removeRow(index);
          if (index < myTableModel.getRowCount()) {
            table.setRowSelectionInterval(index, index);
          }
          else {
            if (index > 0) {
              table.setRowSelectionInterval(index - 1, index - 1);
            }
          }
          updateButtons();
        }

        table.getParent().repaint();
        table.requestFocus();
      }
    };

    myUpAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        TableUtil.stopEditing(table);
        final int[] indexes = table.getSelectedRows();
        for (int index : indexes) {
          if (0 < index && index < myTableModel.getRowCount()) {
            tableModel.exchangeRows(index, index - 1);
            table.setRowSelectionInterval(index - 1, index - 1);
          }
        }
        table.requestFocus();
      }
    };

    myDownAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        TableUtil.stopEditing(table);
        final int[] indexes = table.getSelectedRows();
        for (int index : indexes) {
          if (0 <= index && index < myTableModel.getRowCount() - 1) {
            tableModel.exchangeRows(index, index + 1);
            table.setRowSelectionInterval(index + 1, index + 1);
          }
        }
        table.requestFocus();
      }
    };
  }

  @Override
  protected void installDnD() {
    if (myUpAction != null && myUpActionEnabled
        && myDownAction != null && myDownActionEnabled
        && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      TableRowsDnDSupport.install(myTable, (EditableModel)myTableModel);
    }
  }
}
