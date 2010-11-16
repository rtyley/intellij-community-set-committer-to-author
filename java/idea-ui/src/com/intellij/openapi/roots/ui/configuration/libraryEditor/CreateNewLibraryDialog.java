/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.impl.libraries.LibraryTableBase;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author nik
 */
public class CreateNewLibraryDialog extends LibraryEditorDialogBase {
  private final StructureConfigurableContext myContext;
  private NewLibraryEditor myLibraryEditor;
  private ComboBox myLibraryLevelCombobox;

  public CreateNewLibraryDialog(@NotNull JComponent parent, @NotNull StructureConfigurableContext context, @NotNull NewLibraryEditor libraryEditor,
                                 @NotNull List<LibraryTable> libraryTables, int selectedTable) {
    super(parent, new LibraryRootsComponent(context.getProject(), libraryEditor));
    myContext = context;
    myLibraryEditor = libraryEditor;
    final DefaultComboBoxModel model = new DefaultComboBoxModel();
    for (LibraryTable table : libraryTables) {
      model.addElement(table);
    }
    myLibraryLevelCombobox = new ComboBox(model);
    myLibraryLevelCombobox.setSelectedIndex(selectedTable);
    myLibraryLevelCombobox.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof LibraryTable) {
          setText(((LibraryTable)value).getPresentation().getDisplayName(false));
        }
        return component;
      }
    });
    init();
  }

  @NotNull @Override
  protected LibraryTable.ModifiableModel getTableModifiableModel() {
    final LibraryTable selectedTable = (LibraryTable)myLibraryLevelCombobox.getSelectedItem();
    return myContext.getModifiableLibraryTable(selectedTable);
  }

  public Library createLibrary() {
    final LibraryTableBase.ModifiableModelEx modifiableModel = (LibraryTableBase.ModifiableModelEx)getTableModifiableModel();
    final Library library = modifiableModel.createLibrary(myLibraryEditor.getName(), myLibraryEditor.getType());
    final Library.ModifiableModel model = library.getModifiableModel();
    myLibraryEditor.apply(model);
    new WriteAction() {
      protected void run(final Result result) {
        model.commit();
      }
    }.execute();
    return library;
  }

  @Override
  protected void addNorthComponents(FormBuilder formBuilder) {
    formBuilder.addLabeledComponent("Level:", myLibraryLevelCombobox);
  }

  protected boolean shouldCheckName(String newName) {
    return true;
  }
}
