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
package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.GlobalLibrariesConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectLibrariesConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.ui.CreateLibraryDialog;

import javax.swing.*;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public abstract class AbstractGroovyLibraryManager extends LibraryManager {
  public static final ExtensionPointName<AbstractGroovyLibraryManager> EP_NAME = ExtensionPointName.create("org.intellij.groovy.libraryManager");

  @NotNull
  private static String generatePointerName(final String defaultName, final Set<String> usedLibraryNames) {
    String newName = defaultName;
    int index = 1;
    while (usedLibraryNames.contains(newName)) {
      newName = defaultName + " (" + index + ")";
      index++;
    }
    return newName;
  }

  public Icon getDialogIcon() {
    return getIcon();
  }

  private void fillLibrary(String path, Library.ModifiableModel model) {
    NewLibraryEditor editor = new NewLibraryEditor();
    editor.setName(model.getName());
    fillLibrary(path, editor);
    editor.apply(model);
    Disposer.dispose(editor);
  }

  protected abstract void fillLibrary(String path, LibraryEditor libraryEditor);

  @Nullable
  private Library createSDKLibrary(final String path,
                                  final String name,
                                  final Project project,
                                  final boolean inModuleSettings,
                                  final boolean inProject) {
    Library library;
    final Library.ModifiableModel model;
    LibraryTable.ModifiableModel globalModel = null;
    if (inModuleSettings) {
      globalModel = project != null && inProject ?
                    ProjectLibrariesConfigurable.getInstance(project).getModelProvider().getModifiableModel() :
                    GlobalLibrariesConfigurable.getInstance(project).getModelProvider().getModifiableModel();
      assert globalModel != null;
      library = globalModel.createLibrary(name);
      model = ((LibrariesModifiableModel)globalModel).getLibraryEditor(library).getModel();
    } else {
      LibraryTable table =
        project != null && inProject ? ProjectLibraryTable.getInstance(project) : LibraryTablesRegistrar.getInstance().getLibraryTable();
      library = LibraryUtil.createLibrary(table, name);
      model = library.getModifiableModel();
    }

    assert library != null;


    fillLibrary(path, model);


    if (!inModuleSettings) {
      model.commit();
    }
    else {
      globalModel.commit();
    }

    return library;
  }

  @Nullable
  public Library createLibrary(@NotNull final String path, final LibrariesContainer container, final boolean inModuleSettings) {
    final List<String> versions = CollectionFactory.arrayList();
    final Set<String> usedLibraryNames = CollectionFactory.newTroveSet();
    for (Library library : container.getAllLibraries()) {
      usedLibraryNames.add(library.getName());
      final VirtualFile[] libraryFiles = container.getLibraryFiles(library, OrderRootType.CLASSES);
      if (managesLibrary(libraryFiles)) {
        ContainerUtil.addIfNotNull(getLibraryVersion(libraryFiles), versions);
      }
    }

    final String newVersion = getSDKVersion(path);
    final String libraryKind = getLibraryCategoryName();

    boolean addVersion = !versions.contains(newVersion) ||
                         Messages.showOkCancelDialog("Add one more " + libraryKind + " library of version " + newVersion + "?",
                                                     "Duplicate library version", getDialogIcon()) == 0;

    if (addVersion && !AbstractConfigUtils.UNDEFINED_VERSION.equals(newVersion)) {
      final Project project = container.getProject();
      final String name = generatePointerName(getLibraryPrefix() + "-" + newVersion, usedLibraryNames);
      final CreateLibraryDialog dialog = new CreateLibraryDialog(project, "Create " + libraryKind + " library",
                                                                 "Create Project " + libraryKind + " library '" + name + "'",
                                                                 "Create Global " + libraryKind + " library '" + name + "'");
      dialog.show();
      if (dialog.isOK()) {
        return ApplicationManager.getApplication().runWriteAction(new Computable<Library>() {
          @Nullable
          public Library compute() {
            return createSDKLibrary(path, name, project, inModuleSettings, dialog.isInProject());
          }
        });
      }
    }
    return null;
  }
}
