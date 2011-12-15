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
package com.intellij.ui.table;

import com.intellij.ui.TableUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class TableView<Item> extends BaseTableView implements ItemsProvider, SelectionProvider {
  public TableView() {
    this(new ListTableModel<Item>(ColumnInfo.EMPTY_ARRAY));
  }

  public TableView(final ListTableModel<Item> model) {
    super(model);
    setModel(model);
  }

  public void setModel(final TableModel dataModel) {
    assert dataModel instanceof SortableColumnModel : "SortableColumnModel required";
    super.setModel(dataModel);
  }

  public void setModel(final ListTableModel<Item> model) {
    super.setModel(model);
    updateColumnSizes();
  }

  public ListTableModel<Item> getListTableModel() {
    return (ListTableModel<Item>)super.getModel();
  }

  public TableCellRenderer getCellRenderer(int row, int column) {
    final ColumnInfo<Item, ?> columnInfo = getListTableModel().getColumnInfos()[convertColumnIndexToModel(column)];
    final Item item = getListTableModel().getItems().get(convertRowIndexToModel(row));
    final TableCellRenderer renderer = columnInfo.getCustomizedRenderer(item, columnInfo.getRenderer(item));
    if (renderer == null) {
      return super.getCellRenderer(row, column);
    }
    else {
      return renderer;
    }
  }

  public void tableChanged(TableModelEvent e) {
    if (isEditing()) getCellEditor().cancelCellEditing();
    super.tableChanged(e);
  }

  public void setSelection(Collection<Item> selection) {
    clearSelection();
    for (Object aSelection : selection) {
      addSelection(aSelection);
    }
  }

  public void updateColumnSizes() {
    final JTableHeader header = getTableHeader();
    final TableCellRenderer defaultRenderer = header == null? null : header.getDefaultRenderer();

    final RowSorter<? extends TableModel> sorter = getRowSorter();
    final List<? extends RowSorter.SortKey> current = sorter == null ? null : sorter.getSortKeys();
    ColumnInfo[] columns = getListTableModel().getColumnInfos();
    for (int i = 0; i < columns.length; i++) {
      final ColumnInfo columnInfo = columns[i];
      final TableColumn column = getColumnModel().getColumn(i);
      // hack to get sort arrow included into the renderer component
      if (sorter != null && columnInfo.isSortable()) {
        sorter.setSortKeys(Collections.singletonList(new RowSorter.SortKey(i, SortOrder.ASCENDING)));
      }

      final Component headerComponent = defaultRenderer == null? null :
        defaultRenderer.getTableCellRendererComponent(this, column.getHeaderValue(), false, false, 0, 0);
      
      if (sorter != null && columnInfo.isSortable()) {
        sorter.setSortKeys(current);
      }
      final Dimension headerSize = headerComponent == null? new Dimension(0, 0) : headerComponent.getPreferredSize();
      final String maxStringValue;
      final String preferredValue;
      if (columnInfo.getWidth(this) > 0) {
        int width = columnInfo.getWidth(this);
        column.setMaxWidth(width);
        column.setPreferredWidth(width);
        column.setMinWidth(width);
      }
      else if ((maxStringValue = columnInfo.getMaxStringValue()) != null) {
        int width = getFontMetrics(getFont()).stringWidth(maxStringValue) + columnInfo.getAdditionalWidth();
        width = Math.max(width, headerSize.width);
        column.setPreferredWidth(width);
        column.setMaxWidth(width);
      }
      else if ((preferredValue = columnInfo.getPreferredStringValue()) != null) {
        int width = getFontMetrics(getFont()).stringWidth(preferredValue) + columnInfo.getAdditionalWidth();
        width = Math.max(width, headerSize.width);
        column.setPreferredWidth(width);
      }
    }
  }


  public Collection<Item> getSelection() {
    ArrayList<Item> result = new ArrayList<Item>();
    int[] selectedRows = getSelectedRows();
    if (selectedRows == null) return result;
    final List<Item> items = getItems();
    if (! items.isEmpty()) {
      for (int selectedRow : selectedRows) {
        final int modelIndex = convertRowIndexToModel(selectedRow);
        if (modelIndex >= 0 && modelIndex < items.size()) {
          result.add(items.get(modelIndex));
        }
      }
    }
    return result;
  }

  @Nullable
  public Item getSelectedObject() {
    final int row = getSelectedRow();
    final List<Item> list = getItems();
    return row >= 0 && row < list.size() ? list.get(convertRowIndexToModel(row)) : null;
  }

  @Nullable
  public List<Item> getSelectedObjects() {
    final int[] selectedRows = getSelectedRows();
    if (selectedRows == null || (selectedRows.length == 0)) return Collections.emptyList();
    final List<Item> items = getItems();
    final List<Item> result = new ArrayList<Item>();
    for (int selectedRow : selectedRows) {
      result.add(items.get(convertRowIndexToModel(selectedRow)));
    }
    return result;
  }

  public void addSelection(Object item) {
    List items = getItems();
    if (!items.contains(item)) return;
    int index = items.indexOf(item);
    getSelectionModel().addSelectionInterval(convertRowIndexToView(index), convertRowIndexToView(index));
    // fix cell selection case
    getColumnModel().getSelectionModel().addSelectionInterval(0, getColumnCount()-1);
  }

  public TableCellEditor getCellEditor(int row, int column) {
    final ColumnInfo<Item, ?> columnInfo = getListTableModel().getColumnInfos()[convertColumnIndexToModel(column)];
    final TableCellEditor editor = columnInfo.getEditor(getListTableModel().getItems().get(convertRowIndexToModel(row)));
    return editor == null ? super.getCellEditor(row, column) : editor;
  }

  public List<Item> getItems() {
    return getListTableModel().getItems();
  }

  public void setMinRowHeight(int i) {
    setRowHeight(Math.max(i, getRowHeight()));
  }

  public JTable getComponent() {
    return this;
  }

  public TableViewModel getTableViewModel() {
    return getListTableModel();
  }

  public void stopEditing() {
    TableUtil.stopEditing(this);
  }
}
