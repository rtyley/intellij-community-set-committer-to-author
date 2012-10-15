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
package com.intellij.compiler.options;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.EditableModel;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: 5/28/12
 */
public class ProcessorProfilePanel extends JPanel {
  private final Project myProject;

  private JRadioButton myRbClasspath;
  private JRadioButton myRbProcessorsPath;
  private TextFieldWithBrowseButton myProcessorPathField;
  private JTextField myGeneratedProductionDirField;
  private JTextField myGeneratedTestsDirField;
  private ProcessorTableModel myProcessorsModel;
  private JCheckBox myCbEnableProcessing;
  private JBTable myProcessorTable;
  private JBTable myOptionsTable;
  private JPanel myProcessorPanel;
  private JPanel myOptionsPanel;
  private OptionsTableModel myOptionsModel;


  public ProcessorProfilePanel(Project project) {
    super(new GridBagLayout());
    myProject = project;

    myCbEnableProcessing = new JCheckBox("Enable annotation processing");

    myRbClasspath = new JRadioButton("Obtain processors from project classpath");
    myRbProcessorsPath = new JRadioButton("Processor path:");
    ButtonGroup group = new ButtonGroup();
    group.add(myRbClasspath);
    group.add(myRbProcessorsPath);

    myProcessorPathField = new TextFieldWithBrowseButton(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createAllButJarContentsDescriptor();
        final VirtualFile[] files = FileChooser.chooseFiles(descriptor, myProcessorPathField, myProject, null);
        if (files.length > 0) {
          final StringBuilder builder = new StringBuilder();
          for (VirtualFile file : files) {
            if (builder.length() > 0) {
              builder.append(File.pathSeparator);
            }
            builder.append(FileUtil.toSystemDependentName(file.getPath()));
          }
          myProcessorPathField.setText(builder.toString());
        }
      }
    });

    final JPanel processorTablePanel = new JPanel(new BorderLayout());
    myProcessorsModel = new ProcessorTableModel();
    processorTablePanel.setBorder(IdeBorderFactory.createTitledBorder("Annotation Processors", false));
    myProcessorTable = new JBTable(myProcessorsModel);
    myProcessorTable.getEmptyText().setText("Compiler will run all automatically discovered processors");
    myProcessorPanel = createTablePanel(myProcessorTable);
    processorTablePanel.add(myProcessorPanel, BorderLayout.CENTER);

    final JPanel optionsTablePanel = new JPanel(new BorderLayout());
    myOptionsModel = new OptionsTableModel();
    optionsTablePanel.setBorder(IdeBorderFactory.createTitledBorder("Annotation Processor options", false));
    myOptionsTable = new JBTable(myOptionsModel);
    myOptionsTable.getEmptyText().setText("No processor-specific options configured");
    myOptionsPanel = createTablePanel(myOptionsTable);
    optionsTablePanel.add(myOptionsPanel, BorderLayout.CENTER);

    myGeneratedProductionDirField = new JTextField();
    myGeneratedTestsDirField = new JTextField();

    final JLabel warning = new JLabel("<html>WARNING!<br>" +
                                              /*"All source files located in the generated sources output directory WILL BE EXCLUDED from annotation processing. " +*/
                                              "If option 'Clear output directory on rebuild' is enabled, " +
                                              "the entire contents of directories where generated sources are stored WILL BE CLEARED on rebuild.</html>");
    warning.setFont(warning.getFont().deriveFont(Font.BOLD));

    add(myCbEnableProcessing,
        new GridBagConstraints(0, 0, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    add(myRbClasspath,
        new GridBagConstraints(0, 1, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(10, 0, 0, 0), 0, 0));
    add(myRbProcessorsPath,
        new GridBagConstraints(0, 2, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 0, 0, 0), 0, 0));
    add(myProcessorPathField,
        new GridBagConstraints(1, 2, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 0, 0), 0, 0));

    final JLabel noteMessage = new JLabel("<html>Source files generated by annotation processors will be stored under the project output directory. " +
                                                  "To override this behaviour for this profile you may specify the directory name in the field below. " +
                                                  "If specified, the directory will be created under corresponding module's content root.</html>");
    add(noteMessage,
        new GridBagConstraints(0, 3, 2, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(10, 5, 0, 0), 0, 0));

    add(new JLabel("Production sources directory:"),
        new GridBagConstraints(0, 4, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(10, 5, 0, 0), 0, 0));
    add(myGeneratedProductionDirField,
        new GridBagConstraints(1, 4, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(10, 5, 0, 0), 0, 0));

    add(new JLabel("Test sources directory:"),
        new GridBagConstraints(0, 5, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(10, 5, 0, 0), 0, 0));
    add(myGeneratedTestsDirField,
        new GridBagConstraints(1, 5, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(10, 5, 0, 0), 0, 0));

    add(processorTablePanel,
        new GridBagConstraints(0, 6, 2, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(10, 0, 0, 0), 0, 0));
    add(optionsTablePanel,
        new GridBagConstraints(0, 7, 2, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(10, 0, 0, 0), 0, 0));
    add(warning,
        new GridBagConstraints(0, 8, 2, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(10, 5, 0, 0), 0, 0));

    myRbClasspath.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateEnabledState();
      }
    });

    myProcessorTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
          updateEnabledState();
        }
      }
    });

    myCbEnableProcessing.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateEnabledState();
      }
    });

    updateEnabledState();

  }

  public void setProfile(ProcessorConfigProfile config) {
    myCbEnableProcessing.setSelected(config.isEnabled());

    (config.isObtainProcessorsFromClasspath()? myRbClasspath : myRbProcessorsPath).setSelected(true);
    myProcessorPathField.setText(FileUtil.toSystemDependentName(config.getProcessorPath()));

    final String productionDirName = config.getGeneratedSourcesDirectoryName(false);
    myGeneratedProductionDirField.setText(productionDirName != null? productionDirName.trim() : "");
    final String testsDirName = config.getGeneratedSourcesDirectoryName(true);
    myGeneratedTestsDirField.setText(testsDirName != null? testsDirName.trim() : "");
    myProcessorsModel.setProcessors(config.getProcessors());
    myOptionsModel.setOptions(config.getProcessorOptions());

    updateEnabledState();
  }

  public void saveTo(ProcessorConfigProfile profile) {
    profile.setEnabled(myCbEnableProcessing.isSelected());
    profile.setObtainProcessorsFromClasspath(myRbClasspath.isSelected());
    profile.setProcessorPath(myProcessorPathField.getText().trim());

    final String productionDir = myGeneratedProductionDirField.getText().trim();
    profile.setGeneratedSourcesDirectoryName(StringUtil.isEmpty(productionDir)? null : productionDir, false);
    final String testsDir = myGeneratedTestsDirField.getText().trim();
    profile.setGeneratedSourcesDirectoryName(StringUtil.isEmpty(testsDir)? null : testsDir, true);

    profile.clearProcessors();
    for (String processor : myProcessorsModel.getProcessors()) {
      profile.addProcessor(processor);
    }
    profile.clearProcessorOptions();
    for (Map.Entry<String, String> entry : myOptionsModel.getOptions().entrySet()) {
      profile.setOption(entry.getKey(), entry.getValue());
    }
  }

  private static JPanel createTablePanel(final JBTable table) {
    return ToolbarDecorator.createDecorator(table)
      .disableUpAction()
      .disableDownAction()
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton anActionButton) {
          final TableCellEditor cellEditor = table.getCellEditor();
          if (cellEditor != null) {
            cellEditor.stopCellEditing();
          }
          final TableModel model = table.getModel();
          ((EditableModel)model).addRow();
          TableUtil.editCellAt(table, model.getRowCount() - 1, 0);
        }
      })
      .createPanel();
  }

  private void updateEnabledState() {
   final boolean enabled = myCbEnableProcessing.isSelected();
    final boolean useProcessorpath = !myRbClasspath.isSelected();
    myRbClasspath.setEnabled(enabled);
    myRbProcessorsPath.setEnabled(enabled);
    myProcessorPathField.setEnabled(enabled && useProcessorpath);
    updateTable(myProcessorPanel, myProcessorTable, enabled);
    updateTable(myOptionsPanel, myOptionsTable, enabled);
    myGeneratedProductionDirField.setEnabled(enabled);
    myGeneratedTestsDirField.setEnabled(enabled);
  }

  private static void updateTable(final JPanel tablePanel, final JBTable table, boolean enabled) {
    final AnActionButton addButton = ToolbarDecorator.findAddButton(tablePanel);
    if (addButton != null) {
      addButton.setEnabled(enabled);
    }
    final AnActionButton removeButton = ToolbarDecorator.findRemoveButton(tablePanel);
    if (removeButton != null) {
      removeButton.setEnabled(enabled && table.getSelectedRow() >= 0);
    }
    table.setEnabled(enabled);
    final JTableHeader header = table.getTableHeader();
    if (header != null) {
      header.repaint();
    }
  }

  private static class OptionsTableModel extends AbstractTableModel implements EditableModel {
    private final java.util.List<KeyValuePair> myRows = new ArrayList<KeyValuePair>();

    public String getColumnName(int column) {
      switch (column) {
        case 0: return "Option Name";
        case 1: return "Value";
      }
      return super.getColumnName(column);
    }

    public Class<?> getColumnClass(int columnIndex) {
      return String.class;
    }

    public int getRowCount() {
      return myRows.size();
    }

    public int getColumnCount() {
      return 2;
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 0 || columnIndex == 1;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case 0: return myRows.get(rowIndex).key;
        case 1: return myRows.get(rowIndex).value;
      }
      return null;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (aValue != null) {
        switch (columnIndex) {
          case 0:
            myRows.get(rowIndex).key = (String)aValue;
            break;
          case 1:
            myRows.get(rowIndex).value = (String)aValue;
            break;
        }
      }
    }

    public void removeRow(int idx) {
      myRows.remove(idx);
      fireTableRowsDeleted(idx, idx);
    }

    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
    }

    @Override
    public boolean canExchangeRows(int oldIndex, int newIndex) {
      return false;
    }

    public void addRow() {
      myRows.add(new KeyValuePair());
      final int index = myRows.size() - 1;
      fireTableRowsInserted(index, index);
    }

    public void setOptions(Map<String, String> options) {
      clear();
      if (!options.isEmpty()) {
        for (Map.Entry<String, String> entry : options.entrySet()) {
          myRows.add(new KeyValuePair(entry.getKey(), entry.getValue()));
        }
        Collections.sort(myRows, new Comparator<KeyValuePair>() {
          @Override
          public int compare(KeyValuePair o1, KeyValuePair o2) {
            return o1.key.compareToIgnoreCase(o2.key);
          }
        });
        fireTableRowsInserted(0, options.size()-1);
      }
    }

    public void clear() {
      final int count = myRows.size();
      if (count > 0) {
        myRows.clear();
        fireTableRowsDeleted(0, count-1);
      }
    }

    public Map<String, String> getOptions() {
      final Map<String, String> map = new java.util.HashMap<String, String>();
      for (KeyValuePair pair : myRows) {
        map.put(pair.key.trim(), pair.value.trim());
      }
      map.remove("");
      return map;
    }

    private static final class KeyValuePair {
      String key;
      String value;

      KeyValuePair() {
        this("", "");
      }

      KeyValuePair(String key, String value) {
        this.key = key;
        this.value = value;
      }
    }
  }

  private static class ProcessorTableModel extends AbstractTableModel implements EditableModel {
    private final List<String> myRows = new ArrayList<String>();

    public String getColumnName(int column) {
      switch (column) {
        case 0: return "Processor FQ Name";
      }
      return super.getColumnName(column);
    }

    public Class<?> getColumnClass(int columnIndex) {
      return String.class;
    }

    public int getRowCount() {
      return myRows.size();
    }

    public int getColumnCount() {
      return 1;
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 0;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case 0: return myRows.get(rowIndex);
      }
      return null;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (aValue != null) {
        switch (columnIndex) {
          case 0:
            myRows.set(rowIndex, (String)aValue);
            break;
        }
      }
    }

    public void removeRow(int idx) {
      myRows.remove(idx);
      fireTableRowsDeleted(idx, idx);
    }

    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
    }

    @Override
    public boolean canExchangeRows(int oldIndex, int newIndex) {
      return false;
    }

    public void addRow() {
      myRows.add("");
      final int index = myRows.size() - 1;
      fireTableRowsInserted(index, index);
    }

    public void setProcessors(Collection<String> processors) {
      clear();
      if (!processors.isEmpty()) {
        for (String processor : processors) {
          myRows.add(processor);
        }
        Collections.sort(myRows, new Comparator<String>() {
          public int compare(String o1, String o2) {
            return o1.compareToIgnoreCase(o2);
          }
        });
        fireTableRowsInserted(0, processors.size()-1);
      }
    }

    public void clear() {
      final int count = myRows.size();
      if (count > 0) {
        myRows.clear();
        fireTableRowsDeleted(0, count-1);
      }
    }

    public Collection<String> getProcessors() {
      final Set<String> set = new HashSet<String>();
      for (String row : myRows) {
        if (row != null) {
          set.add(row.trim());
        }
      }
      set.remove("");
      return set;
    }
  }

}
