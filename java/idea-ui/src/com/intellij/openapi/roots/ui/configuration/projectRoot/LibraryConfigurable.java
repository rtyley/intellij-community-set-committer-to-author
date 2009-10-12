/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableEditor;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 02-Jun-2006
 */
public class LibraryConfigurable extends NamedConfigurable<Library> {
  private static final Icon ICON = IconLoader.getIcon("/modules/library.png");

  private LibraryTableEditor myLibraryEditor;
  private final Library myLibrary;
  private final LibraryTableModifiableModelProvider myModel;
  private final Project myProject;

  protected LibraryConfigurable(final LibraryTableModifiableModelProvider libraryTable,
                                final Library library,
                                final Project project,
                                final Runnable updateTree) {
    super(true, updateTree);
    myModel = libraryTable;
    myProject = project;
    myLibrary = library;
  }

  public JComponent createOptionsPanel() {
    myLibraryEditor = LibraryTableEditor.editLibrary(myModel, myLibrary, myProject);
    return myLibraryEditor.getComponent();
  }

  public boolean isModified() {
    return myLibraryEditor != null && myLibraryEditor.hasChanges();
  }

  public void apply() throws ConfigurationException {
    //do nothing
  }

  public void reset() {
    //do nothing
  }

  public void disposeUIResources() {
    if (myLibraryEditor != null) {
      myLibraryEditor.cancelChanges();
      Disposer.dispose(myLibraryEditor);
      myLibraryEditor = null;
    }
  }

  public void setDisplayName(final String name) {
    getLibraryEditor().setName(name);
  }

  private LibraryEditor getLibraryEditor() {
    return ((LibrariesModifiableModel)myModel.getModifiableModel()).getLibraryEditor(myLibrary);
  }

  public Library getEditableObject() {
    return myLibrary;
  }

  public String getBannerSlogan() {
    final LibraryTable libraryTable = myLibrary.getTable();
    String libraryType = libraryTable == null
                         ? ProjectBundle.message("module.library.display.name", 1)
                         : libraryTable.getPresentation().getDisplayName(false);
    return ProjectBundle.message("project.roots.library.banner.text", getDisplayName(), libraryType);
  }

  public String getDisplayName() {
    return getLibraryEditor().getName();
  }

  public Icon getIcon() {
    return ICON;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "preferences.jdkGlobalLibs";  //todo
  }
}
