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
package com.intellij.util.ui.table;

import com.intellij.ui.EditorTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JBListTable extends JPanel {
  protected final JTable myInternalTable;
  private final JBTable mainTable;

  public JBListTable(@NotNull final JTable t) {
    super(new BorderLayout());
    myInternalTable = t;
    final JBListTableModel model = new JBListTableModel(t) {
      @Override
      public JBTableRow getRow(int index) {
        return getRowAt(index);
      }

      @Override
      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return isRowEditable(rowIndex);
      }
    };
    mainTable = new JBTable(model) {
      @Override
      protected void processKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && e.getModifiers() == 0) {
          if (!isEditing() && e.getID() == KeyEvent.KEY_PRESSED) {
            editCellAt(getSelectedRow(), getSelectedColumn());
          }
          e.consume();
        }
        //todo[kb] JBTabsImpl breaks focus traversal policy. Need a workaround here
        //else if (e.getKeyCode() == KeyEvent.VK_TAB) {
        //  final KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        //  if (e.isShiftDown()) {
        //    keyboardFocusManager.focusPreviousComponent(this);
        //  } else {
        //    keyboardFocusManager.focusNextComponent(this);
        //  }
        //}
        else {
          super.processKeyEvent(e);
        }
      }

      @Override
      public TableCellRenderer getCellRenderer(int row, int column) {
        return new DefaultTableCellRenderer() {
          @Override
          public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean hasFocus, int row, int col) {
            return getRowRenderer(t, row, selected, hasFocus);
          }
        };
      }

      @Override
      protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
        //be careful. hardcore!!!
        if (isEditing() && e.getKeyCode() == KeyEvent.VK_TAB) {
          if (pressed) {
            final KeyboardFocusManager mgr = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            if (e.isShiftDown()) {
              mgr.focusPreviousComponent();
            } else {
              mgr.focusNextComponent();
            }
          }
          return true;
        }
        return super.processKeyBinding(ks, e, condition, pressed);
      }

      @Override
      public TableCellEditor getCellEditor(final int row, int column) {
        final JBTableRowEditor editor = getRowEditor(row);
        if (editor != null) {
          editor.prepareEditor(t, row);
          installPaddingAndBordersForEditors(editor);
          editor.setFocusCycleRoot(true);

          editor.setFocusTraversalPolicy(new JBListTableFocusTraversalPolicy(editor));

          return new AbstractTableCellEditor() {
            JTable curTable = null;
                @Override
                public Component getTableCellEditorComponent(final JTable table, Object value, boolean isSelected, final int row, int column) {
                  curTable = table;
                  final JPanel p = new JPanel(new BorderLayout()) {
                    @Override
                    public void addNotify() {
                      super.addNotify();
                      final int height = (int)getPreferredSize().getHeight();
                      if (height > table.getRowHeight(row)) {
                        new RowResizeAnimator(table, row, height).start();
                      }
                    }

                    public void removeNotify() {
                      super.removeNotify();
                      new RowResizeAnimator(table, row, table.getRowHeight()).start();
                    }
                  };
                  p.add(editor, BorderLayout.CENTER);
                  return p;
                }
          
                @Override
                public Object getCellEditorValue() {
                  return editor.getValue();
                }

            
            @Override
            public boolean stopCellEditing() {
              return super.stopCellEditing();
            }

            @Override
            public void cancelCellEditing() {
              super.cancelCellEditing();
            }
          };
        }
        return null;
      }

      @Override
      public Component prepareEditor(TableCellEditor editor, int row, int column) {
        Object value = getValueAt(row, column);
        boolean isSelected = isCellSelected(row, column);
        return editor.getTableCellEditorComponent(this, value, isSelected, row, column);
      }
    };
    mainTable.setStriped(true);
  }

  private void installPaddingAndBordersForEditors(JBTableRowEditor editor) {
    final List<EditorTextField> editors = UIUtil.findComponentsOfType(editor, EditorTextField.class);
    for (EditorTextField textField : editors) {
      textField.putClientProperty("JComboBox.isTableCellEditor", Boolean.FALSE);
      textField.putClientProperty("JBListTable.isTableCellEditor", Boolean.TRUE);
    }
  }

  public final JBTable getTable() {
    return mainTable;
  }

  protected abstract JComponent getRowRenderer(JTable table, int row, boolean selected, boolean focused);

  protected abstract JBTableRowEditor getRowEditor(int row);

  protected abstract JBTableRow getRowAt(int row);

  protected boolean isRowEditable(int row) {
    return true;
  }
  
  private static class RowResizeAnimator extends Thread {
    private final JTable myTable;
    private final int myRow;
    private int neededHeight;
    private int step = 5;
    private int currentHeight;

    private RowResizeAnimator(JTable table, int row, int height) {
      super("Row Animator");
      myTable = table;
      myRow = row;
      neededHeight = height;
      currentHeight = myTable.getRowHeight(myRow);
    }

    @Override
    public void run() {
      try {
        sleep(50);
        
        while (currentHeight != neededHeight) {
          if (Math.abs(currentHeight - neededHeight) < step) {
            currentHeight = neededHeight;
          } else {
            currentHeight += currentHeight < neededHeight ? step : -step;
          }
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              myTable.setRowHeight(myRow, currentHeight);
            }
          });
          sleep(15);
        }
      }
      catch (InterruptedException e) {        
      }
    }
  }
}
