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
package com.intellij.ui.components.editors;

import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import java.awt.*;

/**
 * Solves rendering problems in JTable components when JComboBox objects are used as cell
 * editors components. Known issues of using JComboBox component are the following:
 *   1. Ugly view if row height is small enough
 *   2. Truncated strings in the combobox popup if column width is less than text value width
 *
 * @author Konstantin Bulenkov
 */
public class JBComboBoxTableCellEditorComponent extends JBLabel {
  private JTable myTable;
  private int myRow = 0;
  private int myColumn = 0;
  private final JBList myList = new JBList();
  private Object[] myOptions = {};
  private Object myValue;
  private ListCellRenderer myRenderer;

  public JBComboBoxTableCellEditorComponent() {
  }

  public JBComboBoxTableCellEditorComponent(JTable table) {
    myTable = table;
  }

  public void setTable(JTable table) {
    myTable = table;
  }

  public void setCell(JTable table, int row, int column) {
    setTable(table);
    setRow(row);
    setColumn(column);
  }

  public void setRow(int row) {
    myRow = row;
  }

  public void setColumn(int column) {
    myColumn = column;
  }

  public void setOptions(Object... options) {
    myOptions = options;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    initAndShowPopup();
  }

  private void initAndShowPopup() {
    myList.removeAll();
    final Rectangle rect = myTable.getCellRect(myRow, myColumn, true);
    final Point point = new Point(rect.x, rect.y);
    myList.setModel(JBList.createDefaultListModel(myOptions));
    if (myRenderer != null) {
      myList.setCellRenderer(myRenderer);
    }
    JBPopupFactory.getInstance()
      .createListPopupBuilder(myList)
      .setItemChoosenCallback(new Runnable() {
        @Override
        public void run() {
          myValue = myList.getSelectedValue();
        }
      }).createPopup()
      .show(new RelativePoint(myTable, point));
  }

  public Object getEditorValue() {
    return myValue;
  }

  public void setRenderer(ListCellRenderer renderer) {
    myRenderer = renderer;
  }

  public void setDefaultValue(Object value) {
    myValue = value;
  }
}
