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

package com.intellij.codeInsight.template.impl;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.macro.MacroFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.EditableModel;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

class EditVariableDialog extends DialogWrapper {
  private ArrayList<Variable> myVariables = new ArrayList<Variable>();

  private JTable myTable;
  private Editor myEditor;
  private final List<TemplateContextType> myContextTypes;

  public EditVariableDialog(Editor editor, Component parent, ArrayList<Variable> variables, List<TemplateContextType> contextTypes) {
    super(parent, true);
    myContextTypes = contextTypes;
    setButtonsMargin(null);
    myVariables = variables;
    myEditor = editor;
    init();
    setTitle(CodeInsightBundle.message("templates.dialog.edit.variables.title"));
    setOKButtonText(CommonBundle.getOkButtonText());
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("editing.templates.defineTemplates.editTemplVars");
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.codeInsight.template.impl.EditVariableDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(IdeBorderFactory.createTitledBorder(
      CodeInsightBundle.message("templates.dialog.edit.variables.border.title"), false, false, true));
    panel.setLayout(new BorderLayout());
    panel.add(createVariablesTable(), BorderLayout.CENTER);
    return panel;
  }

  private JComponent createVariablesTable() {
    final String[] names = {
      CodeInsightBundle.message("templates.dialog.edit.variables.table.column.name"),
      CodeInsightBundle.message("templates.dialog.edit.variables.table.column.expression"),
      CodeInsightBundle.message("templates.dialog.edit.variables.table.column.default.value"),
      CodeInsightBundle.message("templates.dialog.edit.variables.table.column.skip.if.defined")
    };

    // Create a model of the data.
    TableModel dataModel = new VariablesModel(names);

    // Create the table
    myTable = new JBTable(dataModel);
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.setPreferredScrollableViewportSize(new Dimension(500, myTable.getRowHeight() * 8));
    myTable.getColumn(names[0]).setPreferredWidth(120);
    myTable.getColumn(names[1]).setPreferredWidth(200);
    myTable.getColumn(names[2]).setPreferredWidth(200);
    myTable.getColumn(names[3]).setPreferredWidth(100);
    if (myVariables.size() > 0) {
      myTable.getSelectionModel().setSelectionInterval(0, 0);
    }

    JComboBox comboField = new JComboBox();
    Macro[] macros = MacroFactory.getMacros();
    Arrays.sort(macros, new Comparator<Macro> () {
      public int compare(Macro m1, Macro m2) {
        return m1.getPresentableName().compareTo(m2.getPresentableName());
      }
    });
    eachMacro:
    for (Macro macro : macros) {
      for (TemplateContextType contextType : myContextTypes) {
        if (macro.isAcceptableInContext(contextType)) {
          comboField.addItem(macro.getPresentableName());
          continue eachMacro;
        }
      }
    }
    comboField.setEditable(true);
    DefaultCellEditor cellEditor = new DefaultCellEditor(comboField);
    cellEditor.setClickCountToStart(1);
    myTable.getColumn(names[1]).setCellEditor(cellEditor);
    myTable.setRowHeight(comboField.getPreferredSize().height);

    JTextField textField = new JTextField();

    /*textField.addMouseListener(
      new PopupHandler(){
        public void invokePopup(Component comp,int x,int y){
          showCellPopup((JTextField)comp,x,y);
        }
      }
    );*/

    cellEditor = new DefaultCellEditor(textField);
    cellEditor.setClickCountToStart(1);
    myTable.setDefaultEditor(String.class, cellEditor);

    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTable).disableAddAction().disableRemoveAction();
    return decorator.createPanel();
  }

  protected void doOKAction() {
    if (myTable.isEditing()) {
      TableCellEditor editor = myTable.getCellEditor();
      if (editor != null) {
        editor.stopCellEditing();
      }
    }
    super.doOKAction();
  }

  /*private void showCellPopup(final JTextField field,int x,int y) {
    JPopupMenu menu = new JPopupMenu();
    final Macro[] macros = MacroFactory.getMacros();
    for (int i = 0; i < macros.length; i++) {
      final Macro macro = macros[i];
      JMenuItem item = new JMenuItem(macro.getName());
      item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          try {
            field.saveToString().insertString(field.getCaretPosition(), macro.getName() + "()", null);
          }
          catch (BadLocationException e1) {
            LOG.error(e1);
          }
        }
      });
      menu.add(item);
    }
    menu.show(field, x, y);
  }*/

  private void updateTemplateTextByVarNameChange(final Variable oldVar, final Variable newVar) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(null, new Runnable() {
          public void run() {
            Document document = myEditor.getDocument();
            String templateText = document.getText();
            templateText = templateText.replaceAll("\\$" + oldVar.getName() + "\\$", "\\$" + newVar.getName() + "\\$");
            document.replaceString(0, document.getTextLength(), templateText);
          }
        }, null, null);
      }
    });
  }

  private class VariablesModel extends AbstractTableModel implements EditableModel {
    private final String[] myNames;

    public VariablesModel(String[] names) {
      myNames = names;
    }

    public int getColumnCount() {
      return myNames.length;
    }

    public int getRowCount() {
      return myVariables.size();
    }

    public Object getValueAt(int row, int col) {
      Variable variable = myVariables.get(row);
      if (col == 0) {
        return variable.getName();
      }
      if (col == 1) {
        return variable.getExpressionString();
      }
      if (col == 2) {
        return variable.getDefaultValueString();
      }
      else {
        return variable.isAlwaysStopAt() ? Boolean.FALSE : Boolean.TRUE;
      }
    }

    public String getColumnName(int column) {
      return myNames[column];
    }

    public Class getColumnClass(int c) {
      if (c <= 2) {
        return String.class;
      }
      else {
        return Boolean.class;
      }
    }

    public boolean isCellEditable(int row, int col) {
      return true;
    }

    public void setValueAt(Object aValue, int row, int col) {
      Variable variable = myVariables.get(row);
      if (col == 0) {
         String varName = (String) aValue;
        Variable newVar = new Variable (varName, variable.getExpressionString(), variable.getDefaultValueString(),
                        variable.isAlwaysStopAt());
        myVariables.set(row, newVar);
        updateTemplateTextByVarNameChange(variable, newVar);
      }
      else if (col == 1) {
        variable.setExpressionString((String)aValue);
      }
      else if (col == 2) {
        variable.setDefaultValueString((String)aValue);
      }
      else {
        variable.setAlwaysStopAt(!((Boolean)aValue).booleanValue());
      }
    }

    @Override
    public void addRow() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removeRow(int index) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
      Collections.swap(myVariables, oldIndex, newIndex);
    }
  }
}
