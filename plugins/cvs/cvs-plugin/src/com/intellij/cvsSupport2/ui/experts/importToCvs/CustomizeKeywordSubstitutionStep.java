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
package com.intellij.cvsSupport2.ui.experts.importToCvs;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.ImportConfiguration;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionWrapper;
import com.intellij.cvsSupport2.ui.experts.CvsWizard;
import com.intellij.cvsSupport2.ui.experts.WizardStep;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ComboBoxTableCellEditor;
import com.intellij.util.ui.ComboBoxTableCellRenderer;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * author: lesya
 */
public class CustomizeKeywordSubstitutionStep extends WizardStep {
  private static final ColumnInfo KEYWORD_SUBSTITUTION = new ColumnInfo(CvsBundle.message("import.wizard.keyword.substitution.column.name")) {
    public Object valueOf(Object object) {
      return ((FileExtension)object).getKeywordSubstitutionsWithSelection();
    }

    public Comparator getComparator() {
      return new Comparator() {
        public int compare(Object o1, Object o2) {
          KeywordSubstitutionWrapper firstSubstitution = ((FileExtension)o1).getKeywordSubstitutionsWithSelection()
            .getSelection();
          KeywordSubstitutionWrapper secondSubstitution = ((FileExtension)o2).getKeywordSubstitutionsWithSelection()
            .getSelection();
          return
            firstSubstitution.toString().compareTo(secondSubstitution.toString());
        }
      };
    }

    public boolean isCellEditable(Object o) {
      return true;
    }

    public void setValue(Object o, Object aValue) {
      ((FileExtension)o).setKeywordSubstitution(((KeywordSubstitutionWrapper)aValue));
    }

    public TableCellRenderer getRenderer(Object o) {
      return ComboBoxTableCellRenderer.INSTANCE;
    }

    public TableCellEditor getEditor(Object item) {
      return ComboBoxTableCellEditor.INSTANCE;
    }
  };

  private final static ColumnInfo EXTENSION_COLUMN = new ColumnInfo(CvsBundle.message("import.wizard.file.extension.column.name")) {
    public Object valueOf(Object o) {
      return ((FileExtension)o).getExtension();
    }

    public Comparator getComparator() {
      return new Comparator(){
        public int compare(Object o, Object o1) {
          return ((FileExtension)o).getExtension().compareTo(((FileExtension)o1).getExtension());
        }
      };
    }
  };

  private final static ColumnInfo[] COLUMNS = new ColumnInfo[]{
    EXTENSION_COLUMN, KEYWORD_SUBSTITUTION
  };

  private final ListTableModel<FileExtension> myModel;
  private final ImportConfiguration myImportConfiguration;

  public CustomizeKeywordSubstitutionStep(String description, CvsWizard wizard,
                                          ImportConfiguration importConfiguration) {
    super(description, wizard);
    myModel = new ListTableModel<FileExtension>(COLUMNS);
    myImportConfiguration = importConfiguration;
    myModel.setItems(collectFileTypes());
    init();
  }

  protected void dispose() {
  }

  private List<FileExtension> collectFileTypes() {
    Collection<FileExtension> storedExtensions = myImportConfiguration.getExtensions();

    ArrayList<FileExtension> result = new ArrayList<FileExtension>();
    result.addAll(storedExtensions);
    FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    for (FileType fileType : fileTypes) {
      String[] extensions = FileTypeManager.getInstance().getAssociatedExtensions(fileType);
      for (String extension : extensions) {
        FileExtension fileExtension = new FileExtension(extension);
        if (!result.contains(fileExtension)) result.add(fileExtension);
      }
    }
    return result;
  }

  public void saveState() {
    myImportConfiguration.setExtensions(myModel.getItems());
  }

  public boolean nextIsEnabled() {
    return true;
  }

  public boolean setActive() {
    return true;
  }

  protected JComponent createComponent() {
    TableView<FileExtension> myTable = new TableView<FileExtension>(myModel);
    myTable.setMinRowHeight(new JComboBox().getPreferredSize().height + 2);
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);
    scrollPane.setOpaque(false);
    return scrollPane;
  }

  public List<FileExtension> getFileExtensions() {
    return myModel.getItems();
  }
}
