/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.propertyTable;

import com.intellij.designer.model.ErrorInfo;
import com.intellij.designer.model.PropertiesContainer;
import com.intellij.designer.model.Property;
import com.intellij.designer.model.PropertyContext;
import com.intellij.designer.propertyTable.renderers.LabelPropertyRenderer;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.plaf.TableUI;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class PropertyTable extends JBTable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.designer.propertyTable.PropertyTable");
  private static final Comparator<String> GROUP_COMPARATOR = new Comparator<String>() {
    @Override
    public int compare(String o1, String o2) {
      return StringUtil.compare(o1, o2, true);
    }
  };
  private static final Comparator<Property> PROPERTY_COMPARATOR = new Comparator<Property>() {
    @Override
    public int compare(Property o1, Property o2) {
      return StringUtil.compare(o1.getName(), o2.getName(), true);
    }
  };

  private boolean mySorted;
  private boolean myShowGroups;
  private boolean myShowExpertProperties;

  private final AbstractTableModel myModel = new PropertyTableModel();
  private List<PropertiesContainer> myContainers = Collections.emptyList();
  private List<Property> myProperties = Collections.emptyList();
  private final Set<String> myExpandedProperties = new HashSet<String>();

  private boolean mySkipUpdate;
  private boolean myStoppingEditing;

  private final TableCellRenderer myCellRenderer = new PropertyCellRenderer();
  private final PropertyCellEditor myCellEditor = new PropertyCellEditor();

  private final PropertyEditorListener myPropertyEditorListener = new PropertyCellEditorListener();


  public PropertyTable() {
    setModel(myModel);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    showColumns(false);

    setShowVerticalLines(false);
    setIntercellSpacing(new Dimension(0, 1));
    setGridColor(UIUtil.getSlightlyDarkerColor(getBackground()));

    setRowSelectionAllowed(true);
    setColumnSelectionAllowed(false);

    addMouseListener(new MouseTableListener());

    // TODO: Updates UI after LAF updated
  }

  public void showColumns(boolean value) {
    JTableHeader tableHeader = getTableHeader();
    tableHeader.setVisible(value);
    tableHeader.setPreferredSize(value ? null : new Dimension());
  }

  public void setSorted(boolean sorted) {
    mySorted = sorted;
    update();
  }

  public boolean isSorted() {
    return mySorted;
  }

  public void setShowGroups(boolean showGroups) {
    myShowGroups = showGroups;
    update();
  }

  public boolean isShowGroups() {
    return myShowGroups;
  }

  public void showExpert(boolean showExpert) {
    myShowExpertProperties = showExpert;
    update();
  }

  public boolean isShowExpertProperties() {
    return myShowExpertProperties;
  }

  public void setUI(TableUI ui) {
    super.setUI(ui);

    // Customize action and input maps
    ActionMap actionMap = getActionMap();
    InputMap focusedInputMap = getInputMap(JComponent.WHEN_FOCUSED);
    InputMap ancestorInputMap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    actionMap.put("selectPreviousRow", new MySelectNextPreviousRowAction(false));
    actionMap.put("selectNextRow", new MySelectNextPreviousRowAction(true));

    actionMap.put("startEditing", new MyStartEditingAction());
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "startEditing");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));

    actionMap.put("smartEnter", new MyEnterAction());
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "smartEnter");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));

    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
    ancestorInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");

    actionMap.put("restoreDefault", new MyRestoreDefaultAction());
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "restoreDefault");
    ancestorInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "restoreDefault");

    actionMap.put("expandCurrent", new MyExpandCurrentAction(true));
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0), "expandCurrent");
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "expandCurrent");
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_RIGHT, 0), "expandCurrent");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0));
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0));
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_KP_RIGHT, 0));

    actionMap.put("collapseCurrent", new MyExpandCurrentAction(false));
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0), "collapseCurrent");
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "collapseCurrent");
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, 0), "collapseCurrent");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0));
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0));
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, 0));
  }

  public TableCellRenderer getCellRenderer(int row, int column) {
    return myCellRenderer;
  }

  public void restoreDefaultValue() {
    final Property property = getSelectionProperty();
    if (property != null) {
      if (isEditing()) {
        cellEditor.stopCellEditing();
      }

      doRestoreDefault(new ThrowableRunnable<Exception>() {
        @Override
        public void run() throws Exception {
          for (PropertiesContainer component : myContainers) {
            if (!property.isRecursiveDefault(component)) {
              property.setDefaultValue(component);
            }
          }
        }
      });

      repaint();
    }
  }

  protected abstract boolean doRestoreDefault(ThrowableRunnable<Exception> runnable);

  @Nullable
  public ErrorInfo getErrorInfoForRow(int row) {
    if (myContainers.size() != 1) {
      return null;
    }

    Property property = myProperties.get(row);
    if (property.getParent() != null) {
      return null;
    }

    for (ErrorInfo errorInfo : getErrors(myContainers.get(0))) {
      if (property.getName().equals(errorInfo.getPropertyName())) {
        return errorInfo;
      }
    }
    return null;
  }

  protected abstract List<ErrorInfo> getErrors(@NotNull PropertiesContainer container);

  @Override
  public String getToolTipText(MouseEvent event) {
    int row = rowAtPoint(event.getPoint());
    if (row != -1 && !myProperties.isEmpty()) {
      ErrorInfo errorInfo = getErrorInfoForRow(row);
      if (errorInfo != null) {
        return errorInfo.getName();
      }
      if (columnAtPoint(event.getPoint()) == 0) {
        String tooltip = myProperties.get(row).getTooltip();
        if (tooltip != null) {
          return tooltip;
        }
      }
    }
    return super.getToolTipText(event);
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Nullable
  protected PropertyContext getPropertyContext() {
    return null;
  }

  public void update() {
    update(myContainers, null);
  }

  public void update(@NotNull List<? extends PropertiesContainer> containers, @Nullable Property initialSelection) {
    finishEditing();

    if (mySkipUpdate) {
      return;
    }
    mySkipUpdate = true;

    try {
      if (isEditing()) {
        cellEditor.stopCellEditing();
      }

      Property selection = initialSelection != null ? initialSelection : getSelectionProperty();
      myContainers = new ArrayList<PropertiesContainer>(containers);
      fillProperties();
      sortPropertiesAndCreateGroups();
      myModel.fireTableDataChanged();

      restoreSelection(selection);
    }
    finally {
      mySkipUpdate = false;
    }
  }

  private void sortPropertiesAndCreateGroups() {
    if (!mySorted && !myShowGroups) return;

    Collections.sort(myProperties, new Comparator<Property>() {
      @Override
      public int compare(Property o1, Property o2) {
        if (myShowGroups) {
          int result = getGroupComparator().compare(o1.getGroup(), o2.getGroup());
          if (result != 0) return result;
        }
        return mySorted ? getPropertyComparator().compare(o1, o2) : 0;
      }
    });

    if (myShowGroups) {
      for (int i = 0; i < myProperties.size() - 1; i++) {
        Property prev = i == 0 ? null : myProperties.get(i - 1);
        Property each = myProperties.get(i);

        String eachGroup = each.getGroup();
        String prevGroup = prev == null ? null : prev.getGroup();

        if (prevGroup != null || eachGroup != null) {
          if (!StringUtil.equalsIgnoreCase(eachGroup, prevGroup)) {
            myProperties.add(i, new GroupProperty(each.getGroup()));
            i++;
          }
        }
      }
    }
  }

  @NotNull
  protected Comparator<String> getGroupComparator() {
    return GROUP_COMPARATOR;
  }

  @NotNull
  protected Comparator<Property> getPropertyComparator() {
    return PROPERTY_COMPARATOR;
  }

  private void restoreSelection(Property selection) {
    List<Property> propertyPath = new ArrayList<Property>(2);
    while (selection != null) {
      propertyPath.add(0, selection);
      selection = selection.getParent();
    }

    int indexToSelect = -1;
    int size = propertyPath.size();
    for (int i = 0; i < size; i++) {
      int index = findFullPathProperty(myProperties, propertyPath.get(i));
      if (index == -1) {
        break;
      }
      if (i == size - 1) {
        indexToSelect = index;
      }
      else {
        expand(index);
      }
    }

    if (indexToSelect != -1) {
      getSelectionModel().setSelectionInterval(indexToSelect, indexToSelect);
    }
    else if (getRowCount() > 0) {
      indexToSelect = 0;
      for (int i = 0; i < myProperties.size(); i++) {
        if (!(myProperties.get(i) instanceof GroupProperty)) {
          indexToSelect = i;
          break;
        }
      }
      getSelectionModel().setSelectionInterval(indexToSelect, indexToSelect);
    }
    TableUtil.scrollSelectionToVisible(this);
  }

  private void fillProperties() {
    myProperties = new ArrayList<Property>();
    int size = myContainers.size();

    if (size > 0) {
      fillProperties(myContainers.get(0), myProperties);

      if (size > 1) {
        for (Iterator<Property> I = myProperties.iterator(); I.hasNext(); ) {
          if (!I.next().availableFor(myContainers)) {
            I.remove();
          }
        }

        for (int i = 1; i < size; i++) {
          List<Property> properties = new ArrayList<Property>();
          fillProperties(myContainers.get(i), properties);

          for (Iterator<Property> I = myProperties.iterator(); I.hasNext(); ) {
            Property property = I.next();

            int index = findFullPathProperty(properties, property);
            if (index == -1) {
              I.remove();
              continue;
            }

            Property testProperty = properties.get(index);
            if (!property.getClass().equals(testProperty.getClass())) {
              I.remove();
              continue;
            }

            List<Property> children = getChildren(property);
            List<Property> testChildren = getChildren(testProperty);
            int pSize = children.size();

            if (pSize != testChildren.size()) {
              I.remove();
              continue;
            }

            for (int j = 0; j < pSize; j++) {
              if (!children.get(j).getName().equals(testChildren.get(j).getName())) {
                I.remove();
                break;
              }
            }
          }
        }
      }
    }
  }

  private void fillProperties(PropertiesContainer<?> container, List<Property> properties) {
    for (Property property : container.getProperties()) {
      addProperty(container, property, properties);
    }
  }

  private void addProperty(PropertiesContainer<?> container, Property property, List<Property> properties) {
    if (property.isExpert() && !myShowExpertProperties) {
      try {
        if (property.isRecursiveDefault(container)) {
          return;
        }
      }
      catch (Throwable e) {
        return;
      }
    }

    properties.add(property);

    if (isExpanded(property)) {
      for (Property child : getChildren(property)) {
        addProperty(container, child, properties);
      }
    }
  }

  @Nullable
  public static Property findProperty(List<Property> properties, String name) {
    for (Property property : properties) {
      if (name.equals(property.getName())) {
        return property;
      }
    }
    return null;
  }

  public static int findProperty(List<Property> properties, Property property) {
    String name = property.getName();
    int size = properties.size();

    for (int i = 0; i < size; i++) {
      if (name.equals(properties.get(i).getName())) {
        return i;
      }
    }

    return -1;
  }

  private static int findFullPathProperty(List<Property> properties, Property property) {
    Property parent = property.getParent();
    if (parent == null) {
      return findProperty(properties, property);
    }

    String name = getFullPathName(property);
    int size = properties.size();

    for (int i = 0; i < size; i++) {
      if (name.equals(getFullPathName(properties.get(i)))) {
        return i;
      }
    }

    return -1;
  }

  private static String getFullPathName(Property property) {
    StringBuilder builder = new StringBuilder();
    for (; property != null; property = property.getParent()) {
      builder.insert(0, ".").insert(0, property.getName());
    }
    return builder.toString();
  }

  public static void moveProperty(List<Property> source, String name, List<Property> destination, int index) {
    Property property = extractProperty(source, name);
    if (property != null) {
      if (index == -1) {
        destination.add(property);
      }
      else {
        destination.add(index, property);
      }
    }
  }

  @Nullable
  public static Property extractProperty(List<Property> properties, String name) {
    int size = properties.size();
    for (int i = 0; i < size; i++) {
      if (name.equals(properties.get(i).getName())) {
        return properties.remove(i);
      }
    }
    return null;
  }

  @Nullable
  public Property getSelectionProperty() {
    int selectedRow = getSelectedRow();
    if (selectedRow >= 0 && selectedRow < myProperties.size()) {
      return myProperties.get(selectedRow);
    }
    return null;
  }

  @Nullable
  private PropertiesContainer getCurrentComponent() {
    return myContainers.size() == 1 ? myContainers.get(0) : null;
  }

  private List<Property> getChildren(Property property) {
    return property.getChildren(getCurrentComponent());
  }

  private List<Property> getFilterChildren(Property property) {
    List<Property> properties = new ArrayList<Property>(getChildren(property));
    for (Iterator<Property> I = properties.iterator(); I.hasNext(); ) {
      Property child = I.next();
      if (child.isExpert() && !myShowExpertProperties) {
        I.remove();
      }
    }
    return properties;
  }

  public boolean isDefault(Property property) throws Exception {
    for (PropertiesContainer component : myContainers) {
      if (!property.isRecursiveDefault(component)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  private Object getValue(Property property) throws Exception {
    int size = myContainers.size();
    if (size == 0) {
      return null;
    }

    Object value = property.getValue(myContainers.get(0));
    for (int i = 1; i < size; i++) {
      if (!Comparing.equal(value, property.getValue(myContainers.get(i)))) {
        return null;
      }
    }

    return value;
  }

  private boolean isExpanded(Property property) {
    return myExpandedProperties.contains(property.getPath());
  }

  private void collapse(int rowIndex) {
    int selectedRow = getSelectedRow();
    Property property = myProperties.get(rowIndex);

    LOG.assertTrue(myExpandedProperties.remove(property.getPath()));
    int size = getFilterChildren(property).size();
    for (int i = 0; i < size; i++) {
      myProperties.remove(rowIndex + 1);
    }
    myModel.fireTableDataChanged();

    if (selectedRow != -1) {
      if (selectedRow > rowIndex) {
        selectedRow -= size;
      }

      getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
    }
  }

  private void expand(int rowIndex) {
    int selectedRow = getSelectedRow();
    Property property = myProperties.get(rowIndex);
    String path = property.getPath();

    if (myExpandedProperties.contains(path)) {
      return;
    }
    myExpandedProperties.add(path);

    List<Property> properties = getFilterChildren(property);
    myProperties.addAll(rowIndex + 1, properties);

    myModel.fireTableDataChanged();

    if (selectedRow != -1) {
      if (selectedRow > rowIndex) {
        selectedRow += properties.size();
      }

      getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
    }

    Rectangle rectStart = getCellRect(selectedRow, 0, true);
    Rectangle rectEnd = getCellRect(selectedRow + properties.size(), 0, true);
    scrollRectToVisible(
      new Rectangle(rectStart.x, rectStart.y, rectEnd.x + rectEnd.width - rectStart.x, rectEnd.y + rectEnd.height - rectStart.y));
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public void setValueAt(Object aValue, int row, int column) {
    Property property = myProperties.get(row);
    super.setValueAt(aValue, row, column);

    if (property.needRefreshPropertyList()) {
      update();
    }

    repaint();
  }

  @Override
  public TableCellEditor getCellEditor(int row, int column) {
    PropertyEditor editor = myProperties.get(row).getEditor();
    editor.removePropertyEditorListener(myPropertyEditorListener); // reorder listener (first)
    editor.addPropertyEditorListener(myPropertyEditorListener);
    myCellEditor.setEditor(editor);
    return myCellEditor;
  }

  /*
  * This method is overriden due to bug in the JTree. The problem is that
  * JTree does not properly repaint edited cell if the editor is opaque or
  * has opaque child components.
  */
  public boolean editCellAt(int row, int column, EventObject e) {
    boolean result = super.editCellAt(row, column, e);
    repaint(getCellRect(row, column, true));
    return result;
  }

  private void startEditing(int index) {
    PropertyEditor editor = myProperties.get(index).getEditor();
    if (editor == null) {
      return;
    }

    editCellAt(index, convertColumnIndexToView(1));
    LOG.assertTrue(editorComp != null);

    JComponent preferredComponent = editor.getPreferredFocusedComponent();
    if (preferredComponent == null) {
      preferredComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent((JComponent)editorComp);
    }
    if (preferredComponent != null) {
      preferredComponent.requestFocusInWindow();
    }
  }

  private void finishEditing() {
    if (editingRow != -1) {
      editingStopped(null);
    }
  }

  public void editingStopped(@Nullable ChangeEvent event) {
    if (myStoppingEditing) {
      return;
    }
    myStoppingEditing = true;

    LOG.assertTrue(isEditing());
    LOG.assertTrue(editingRow != -1);

    PropertyEditor editor = myProperties.get(editingRow).getEditor();
    editor.removePropertyEditorListener(myPropertyEditorListener);

    try {
      setValueAt(editor.getValue(), editingRow, editingColumn);
    }
    catch (Exception e) {
      showInvalidInput(e);
    }
    finally {
      removeEditor();
      myStoppingEditing = false;
    }
  }

  private boolean setValueAtRow(int row, final Object newValue) {
    final Property property = myProperties.get(row);

    boolean isNewValue;
    try {
      Object oldValue = getValue(property);
      isNewValue = !Comparing.equal(oldValue, newValue);
      if (newValue == null && oldValue instanceof String && ((String)oldValue).length() == 0) {
        isNewValue = false;
      }
    }
    catch (Throwable e) {
      isNewValue = true;
    }

    boolean isSetValue = true;

    if (isNewValue) {
      isSetValue = doSetValue(new ThrowableRunnable<Exception>() {
        @Override
        public void run() throws Exception {
          for (PropertiesContainer component : myContainers) {
            property.setValue(component, newValue);
          }
        }
      });
    }

    if (isSetValue) {
      if (property.needRefreshPropertyList()) {
        update();
      }
      else {
        myModel.fireTableRowsUpdated(row, row);
      }
    }

    return isSetValue;
  }

  protected abstract boolean doSetValue(ThrowableRunnable<Exception> runnable);

  private static void showInvalidInput(Exception e) {
    Throwable cause = e.getCause();
    String message = cause == null ? e.getMessage() : cause.getMessage();

    if (message == null || message.length() == 0) {
      message = "No message";
    }

    Messages.showMessageDialog(formatErrorGettingValueMesage(message),
                               "Invalid Input",
                               Messages.getErrorIcon());
  }

  private static String formatErrorGettingValueMesage(String message) {
    return MessageFormat.format("Error setting value: {0}", message);
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Reimplementation of LookAndFeel's SelectNextRowAction action.
   * Standard implementation isn't smart enough.
   *
   * @see javax.swing.plaf.basic.BasicTableUI
   */
  private class MySelectNextPreviousRowAction extends AbstractAction {
    private boolean selectNext;

    private MySelectNextPreviousRowAction(boolean selectNext) {
      this.selectNext = selectNext;
    }

    public void actionPerformed(ActionEvent e) {
      int rowCount = getRowCount();
      LOG.assertTrue(rowCount > 0);

      int selectedRow = getSelectedRow();
      if (selectedRow == -1) {
        selectedRow = 0;
      }
      else {
        if (selectNext) {
          selectedRow = Math.min(rowCount - 1, getSelectedRow() + 1);
        }
        else {
          selectedRow = Math.max(0, selectedRow - 1);
        }
      }

      if (isEditing()) {
        finishEditing();
        getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
        scrollRectToVisible(getCellRect(selectedRow, 0, true));
        startEditing(selectedRow);
      }
      else {
        getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
        scrollRectToVisible(getCellRect(selectedRow, 0, true));
      }
    }
  }

  /**
   * Reimplementation of LookAndFeel's StartEditingAction action.
   * Standard implementation isn't smart enough.
   *
   * @see javax.swing.plaf.basic.BasicTableUI
   */
  private class MyStartEditingAction extends AbstractAction {
    public void actionPerformed(ActionEvent e) {
      int selectedRow = getSelectedRow();
      if (selectedRow == -1 || isEditing()) {
        return;
      }

      startEditing(selectedRow);
    }
  }

  private class MyEnterAction extends AbstractAction {
    public void actionPerformed(ActionEvent e) {
      int selectedRow = getSelectedRow();
      if (isEditing() || selectedRow == -1) {
        return;
      }

      Property property = myProperties.get(selectedRow);
      if (!getChildren(property).isEmpty()) {
        if (isExpanded(property)) {
          collapse(selectedRow);
        }
        else {
          expand(selectedRow);
        }
      }
      else {
        startEditing(selectedRow);
      }
    }
  }

  private class MyExpandCurrentAction extends AbstractAction {
    private final boolean myExpand;

    public MyExpandCurrentAction(boolean expand) {
      myExpand = expand;
    }

    public void actionPerformed(ActionEvent e) {
      int selectedRow = getSelectedRow();
      if (isEditing() || selectedRow == -1) {
        return;
      }

      Property property = myProperties.get(selectedRow);
      if (!getChildren(property).isEmpty()) {
        if (myExpand) {
          if (!isExpanded(property)) {
            expand(selectedRow);
          }
        }
        else {
          if (isExpanded(property)) {
            collapse(selectedRow);
          }
        }
      }
    }
  }

  private class MyRestoreDefaultAction extends AbstractAction {
    @Override
    public void actionPerformed(ActionEvent e) {
      restoreDefaultValue();
    }
  }

  private class MouseTableListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent e) {
      int row = rowAtPoint(e.getPoint());
      if (row == -1) {
        return;
      }

      Property property = myProperties.get(row);
      if (getChildren(property).isEmpty()) return;

      Icon icon = UIUtil.getTreeNodeIcon(false, true, true);

      Rectangle rect = getCellRect(row, convertColumnIndexToView(0), false);
      int indent = getBeforeIconAndAfterIndents(property, icon).first;
      if (e.getX() < rect.x + indent ||
          e.getX() > rect.x + indent + icon.getIconWidth() ||
          e.getY() < rect.y ||
          e.getY() > rect.y + rect.height) {
        return;
      }

      // TODO: disallow selection for this row
      if (isExpanded(property)) {
        collapse(row);
      }
      else {
        expand(row);
      }
    }
  }

  private class PropertyTableModel extends AbstractTableModel {
    private final String[] myColumnNames = {"Property", "Value"};

    @Override
    public int getColumnCount() {
      return myColumnNames.length;
    }

    @Override
    public String getColumnName(int column) {
      return myColumnNames[column];
    }

    public boolean isCellEditable(int row, int column) {
      return column == 1 && myProperties.get(row).getEditor() != null;
    }

    @Override
    public int getRowCount() {
      return myProperties.size();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return myProperties.get(rowIndex);
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      setValueAtRow(rowIndex, aValue);
    }
  }

  private static int getDepth(@NotNull Property property) {
    int result = 0;
    for (Property each = property.getParent(); each != null; each = each.getParent(), result++) {
      // empty
    }
    return result;
  }

  @NotNull
  private static Pair<Integer, Integer> getBeforeIconAndAfterIndents(@NotNull Property property, @NotNull Icon icon) {
    int nodeIndent = UIUtil.getTreeLeftChildIndent() + UIUtil.getTreeRightChildIndent();
    int beforeIcon = nodeIndent * getDepth(property);

    int leftIconOffset = Math.max(0, UIUtil.getTreeLeftChildIndent() - (icon.getIconWidth() / 2));
    beforeIcon += leftIconOffset;

    int afterIcon = Math.max(0, nodeIndent - leftIconOffset - icon.getIconWidth());

    return Pair.create(beforeIcon, afterIcon);
  }

  private class PropertyCellEditorListener implements PropertyEditorListener {
    @Override
    public void valueCommitted(PropertyEditor source, boolean continueEditing, boolean closeEditorOnError) {
      if (isEditing()) {
        Object value;
        TableCellEditor tableCellEditor = cellEditor;

        try {
          value = tableCellEditor.getCellEditorValue();
        }
        catch (Exception e) {
          showInvalidInput(e);
          return;
        }

        if (setValueAtRow(editingRow, value)) {
          if (!continueEditing) {
            tableCellEditor.stopCellEditing();
          }
        }
        else if (closeEditorOnError) {
          tableCellEditor.cancelCellEditing();
        }
      }
    }

    @Override
    public void editingCanceled(PropertyEditor source) {
      if (isEditing()) {
        cellEditor.cancelCellEditing();
      }
    }

    @Override
    public void preferredSizeChanged(PropertyEditor source) {
    }
  }

  private class PropertyCellEditor extends AbstractCellEditor implements TableCellEditor {
    private PropertyEditor myEditor;

    public void setEditor(PropertyEditor editor) {
      myEditor = editor;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      try {
        JComponent component = myEditor.getComponent(getCurrentComponent(), getPropertyContext(), getValue((Property)value), null);

        if (component instanceof JComboBox) {
          component.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);
          component.putClientProperty("tableCellEditor", this);
        }
        else if (component instanceof JCheckBox) {
          component.putClientProperty("JComponent.sizeVariant", UIUtil.isUnderAquaLookAndFeel() ? "small" : null);
        }

        return component;
      }
      catch (Throwable e) {
        LOG.debug(e);
        SimpleColoredComponent errComponent = new SimpleColoredComponent();
        errComponent
          .append(MessageFormat.format("Error getting value: {0}", e.getMessage()), SimpleTextAttributes.ERROR_ATTRIBUTES);
        return errComponent;
      }
    }

    @Override
    public Object getCellEditorValue() {
      try {
        return myEditor.getValue();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static void updateRenderer(JComponent component, boolean selected) {
    if (selected) {
      component.setForeground(UIUtil.getTableSelectionForeground());
      component.setBackground(UIUtil.getTableSelectionBackground());
    }
    else {
      component.setForeground(UIUtil.getTableForeground());
      component.setBackground(UIUtil.getTableBackground());
    }
  }

  @NotNull
  protected abstract TextAttributesKey getErrorAttributes(@NotNull HighlightSeverity severity);

  private class PropertyCellRenderer implements TableCellRenderer {
    private final ColoredTableCellRenderer myCellRenderer;
    private final ColoredTableCellRenderer myGroupRenderer;

    private PropertyCellRenderer() {
      myCellRenderer = new MyCellRenderer();
      myGroupRenderer = new MyCellRenderer() {
        private boolean mySelected;
        public boolean myDrawTopLine;


        @Override
        protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
          super.customizeCellRenderer(table, value, selected, hasFocus, row, column);
          mySelected = selected;
          myDrawTopLine = row > 0;
        }

        @Override
        protected void paintBackground(Graphics2D g, int x, int width, int height) {
          if (mySelected) {
            super.paintBackground(g, x, width, height);
          }
          else {
            UIUtil.drawHeader(g, x, width, height, true, myDrawTopLine);
          }
        }
      };
    }

    @Override
    public Component getTableCellRendererComponent(JTable table,
                                                   Object value,
                                                   boolean selected,
                                                   boolean cellHasFocus,
                                                   int row,
                                                   int column) {
      column = table.convertColumnIndexToModel(column);
      Property property = (Property)value;
      Color background = table.getBackground();

      Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      boolean tableHasFocus = focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, table);

      ColoredTableCellRenderer renderer = property instanceof GroupProperty ? myGroupRenderer : myCellRenderer;

      renderer.getTableCellRendererComponent(table, value, selected, cellHasFocus, row, column);
      renderer.setBackground(selected ? UIUtil.getTreeSelectionBackground(tableHasFocus) : background);

      if (property instanceof GroupProperty) {
        renderer.setIpad(new Insets(0, 5, 0, 0));
        if (column == 0) {
          renderer.append(property.getName());
        }
        return renderer;
      }

      boolean isDefault = true;
      try {
        for (PropertiesContainer container : myContainers) {
          if (!property.showAsDefault(container)) {
            isDefault = false;
            break;
          }
        }
      }
      catch (Exception e) {
        LOG.debug(e);
      }

      renderer.clear();

      if (column == 0) {
        SimpleTextAttributes attr = SimpleTextAttributes.REGULAR_ATTRIBUTES;

        if (!selected && !isDefault) {
          attr = attr.derive(-1, FileStatus.MODIFIED.getColor(), null, null);
        }
        if (property.isImportant()) {
          attr = attr.derive(attr.getStyle() | SimpleTextAttributes.STYLE_BOLD, null, null, null);
        }
        if (property.isExpert()) {
          attr = attr.derive(attr.getStyle() | SimpleTextAttributes.STYLE_ITALIC, null, null, null);
        }
        if (property.isDeprecated()) {
          attr = attr.derive(attr.getStyle() | SimpleTextAttributes.STYLE_STRIKEOUT, null, null, null);
        }

        ErrorInfo errorInfo = getErrorInfoForRow(row);
        if (errorInfo != null) {
          SimpleTextAttributes template = SimpleTextAttributes.fromTextAttributes(
            EditorColorsManager.getInstance().getGlobalScheme().getAttributes(getErrorAttributes(errorInfo.getLevel().getSeverity())));

          int style = ((template.getStyle() & SimpleTextAttributes.STYLE_WAVED) != 0 ? SimpleTextAttributes.STYLE_WAVED : 0)
                      | ((template.getStyle() & SimpleTextAttributes.STYLE_UNDERLINE) != 0 ? SimpleTextAttributes.STYLE_UNDERLINE : 0);
          attr = attr.derive(attr.getStyle() | style, template.getFgColor(), null, template.getWaveColor());
        }

        renderer.append(property.getName(), attr);

        Icon icon = UIUtil.getTreeNodeIcon(isExpanded(property), selected, tableHasFocus);
        boolean hasChildren = !getChildren(property).isEmpty();

        renderer.setIcon(hasChildren ? icon : null);

        Pair<Integer, Integer> indents = getBeforeIconAndAfterIndents(property, icon);
        int indent = indents.first;

        if (hasChildren) {
          renderer.setIconTextGap(indents.second);
        }
        else {
          indent += icon.getIconWidth() + indents.second;
        }
        renderer.setIpad(new Insets(0, indent, 0, 0));

        return renderer;
      }
      else {
        try {
          PropertyRenderer valueRenderer = property.getRenderer();
          JComponent component =
            valueRenderer.getComponent(getCurrentComponent(), getPropertyContext(), getValue(property), selected, tableHasFocus);

          component.setBackground(selected ? UIUtil.getTreeSelectionBackground(tableHasFocus) : background);
          component.setFont(table.getFont());

          if (component instanceof JCheckBox) {
            component.putClientProperty("JComponent.sizeVariant", UIUtil.isUnderAquaLookAndFeel() ? "small" : null);
          }

          return component;
        }
        catch (Exception e) {
          LOG.debug(e);
          renderer.append(formatErrorGettingValueMesage(e.getMessage()), SimpleTextAttributes.ERROR_ATTRIBUTES);
          return renderer;
        }
      }
    }

    private class MyCellRenderer extends ColoredTableCellRenderer {
      protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
        setPaintFocusBorder(false);
        setFocusBorderAroundIcon(true);
      }
    }
  }

  private static class GroupProperty extends Property {
    public GroupProperty(@Nullable String name) {
      super(null, StringUtil.notNullize(name));
    }

    @NotNull
    @Override
    public PropertyRenderer getRenderer() {
      return new LabelPropertyRenderer(null);
    }

    @Override
    public PropertyEditor getEditor() {
      return null;
    }
  }
}