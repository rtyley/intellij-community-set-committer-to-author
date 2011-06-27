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

/**
 * @author Jeka
 * @author Konstantin Bulenkov
 */
package com.intellij.ui;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class BooleanTableCellRenderer extends JCheckBox implements TableCellRenderer {
  private final JPanel myPanel = new JPanel(new BorderLayout());

  public BooleanTableCellRenderer() {
    super();
    setBorderPainted(true);
    setVerticalAlignment(CENTER);
    setHorizontalAlignment(CENTER);
  }

  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSel, boolean hasFocus, int row, int column) {
    final Color bg = UIUtil.getTableCellBackground(table, row);
    final Color fg = table.getForeground();
    final Color selBg = table.getSelectionBackground();
    final Color selFg = table.getSelectionForeground();

    myPanel.setBackground(isSel ? selBg : bg);
    if (value == null) {
      return myPanel;
    }

    setForeground(isSel ? selFg : fg);
    myPanel.setForeground(getForeground());
    setBackground(bg);
    myPanel.setBackground(getBackground());
    //if (isSel) {
    //  super.setBackground(selBg);
    //} else {
    //  setBackground(bg);
    //}

    if (value instanceof String) {
      setSelected(Boolean.parseBoolean((String)value));
    } else {
      setSelected(((Boolean)value).booleanValue());
    }


    setEnabled(table.isCellEditable(row, column));
    setBorder(null);
    myPanel.removeAll();
    myPanel.add(this, BorderLayout.CENTER);
    final ListSelectionModel selModel = table.getSelectionModel();
    final Color color = (selModel.getMaxSelectionIndex() - selModel.getMinSelectionIndex()) == 0
                        ? table.getSelectionBackground() : table.getForeground();
    myPanel.setBorder(hasFocus ? BorderFactory.createLineBorder(color) : IdeBorderFactory.createEmptyBorder(1));
    return myPanel;
  }
}
