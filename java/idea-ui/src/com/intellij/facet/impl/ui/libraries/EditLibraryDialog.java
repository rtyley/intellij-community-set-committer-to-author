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
package com.intellij.facet.impl.ui.libraries;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableEditor;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;

import javax.swing.*;
import java.awt.*;

/**
 * @author Dmitry Avdeev
 */
public class EditLibraryDialog extends DialogWrapper {

  private JPanel myEditorPanel;
  private JPanel myPanel;
  private JPanel myNameAndLevelPanelWrapper;
  private final LibraryNameAndLevelPanel myNameAndLevelPanel;
  private LibraryCompositionSettings mySettings;
  private LibraryTableEditor myLibraryTableEditor;

  public EditLibraryDialog(Component parent, LibraryCompositionSettings settings) {
    super(parent, true);
    mySettings = settings;
    final Library library = settings.getOrCreateLibrary();

    myLibraryTableEditor = LibraryTableEditor.editLibrary(new LibraryTableModifiableModelProvider() {

      @Override
      public LibraryTable.ModifiableModel getModifiableModel() {
        return library.getTable().getModifiableModel();
      }

      @Override
      public String getTableLevel() {
        return library.getTable().getTableLevel();
      }

      @Override
      public LibraryTablePresentation getLibraryTablePresentation() {
        return library.getTable().getPresentation();
      }

      @Override
      public boolean isLibraryTableEditable() {
        return false;
      }
    }, library);

    Disposer.register(getDisposable(), myLibraryTableEditor);

    setTitle("Edit Library");

    myNameAndLevelPanel = new LibraryNameAndLevelPanel();
    myNameAndLevelPanel.reset(mySettings);
    init();

  }

  @Override
  protected JComponent createCenterPanel() {

    JComponent editor = myLibraryTableEditor.getComponent();
    myEditorPanel.add(editor);
    myNameAndLevelPanelWrapper.add(myNameAndLevelPanel.getPanel());
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    myNameAndLevelPanel.apply(mySettings);
    super.doOKAction();
  }
}
