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
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryTableBase;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.ui.LibraryRootsComponentDescriptor;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.DefaultLibraryRootsComponentDescriptor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.RootDetectionUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import java.util.*;

/**
* @author nik
*/
public class CreateModuleLibraryChooser implements ClasspathElementChooser<Library> {
  private final JComponent myParentComponent;
  private final Module myModule;
  private final LibraryTable.ModifiableModel myModuleLibrariesModel;
  private final HashMap<LibraryRootsComponentDescriptor,LibraryType> myLibraryTypes;
  private LibraryType myLibraryType;
  private final DefaultLibraryRootsComponentDescriptor myDefaultDescriptor;
  private List<OrderRoot> myChosenRoots;

  public CreateModuleLibraryChooser(ClasspathPanel classpathPanel, LibraryTable.ModifiableModel moduleLibraryModel) {
    this(LibraryEditingUtil.getSuitableTypes(classpathPanel), classpathPanel.getComponent(), classpathPanel.getRootModel().getModule(),
         moduleLibraryModel);
  }

  public CreateModuleLibraryChooser(List<? extends LibraryType> libraryTypes, JComponent parentComponent,
                                    Module module,
                                    final LibraryTable.ModifiableModel moduleLibrariesModel) {
    myParentComponent = parentComponent;
    myModule = module;
    myModuleLibrariesModel = moduleLibrariesModel;
    myLibraryTypes = new HashMap<LibraryRootsComponentDescriptor, LibraryType>();
    myDefaultDescriptor = new DefaultLibraryRootsComponentDescriptor();
    for (LibraryType<?> libraryType : libraryTypes) {
      LibraryRootsComponentDescriptor descriptor = null;
      if (libraryType != null) {
        descriptor = libraryType.createLibraryRootsComponentDescriptor();
      }
      if (descriptor == null) {
        descriptor = myDefaultDescriptor;
      }
      myLibraryTypes.put(descriptor, libraryType);
    }
  }

  public List<Library> getChosenElements() {
    if (myChosenRoots == null) {
      return Collections.emptyList();
    }
    final List<OrderRoot> roots = filterAlreadyAdded(myChosenRoots);
    if (roots.isEmpty()) {
      return Collections.emptyList();
    }
    final List<Library> addedLibraries = new ArrayList<Library>();
    boolean onlyClasses = true;
    for (OrderRoot root : roots) {
      onlyClasses &= root.getType() == OrderRootType.CLASSES;
    }
    if (onlyClasses) {
      for (OrderRoot root : roots) {
        addedLibraries.add(createLibraryFromRoots(Collections.singletonList(root)));
      }
    }
    else {
      addedLibraries.add(createLibraryFromRoots(roots));
    }
    return addedLibraries;
  }

  private Library createLibraryFromRoots(List<OrderRoot> roots) {
    final Library library = ((LibraryTableBase.ModifiableModelEx)myModuleLibrariesModel).createLibrary(null, myLibraryType);
    final Library.ModifiableModel libModel = library.getModifiableModel();
    for (OrderRoot root : roots) {
      if (root.isJarDirectory()) {
        libModel.addJarDirectory(root.getFile(), false, root.getType());
      }
      else {
        libModel.addRoot(root.getFile(), root.getType());
      }
    }
    libModel.commit();
    return library;
  }

  private List<OrderRoot> filterAlreadyAdded(final List<OrderRoot> roots) {
    if (roots == null || roots.isEmpty()) {
      return Collections.emptyList();
    }

    final List<OrderRoot> result = new ArrayList<OrderRoot>();
    final Library[] libraries = myModuleLibrariesModel.getLibraries();
    for (OrderRoot root : roots) {
      if (!isIncluded(root, libraries)) {
        result.add(root);
      }
    }
    return result;
  }

  private static boolean isIncluded(OrderRoot root, Library[] libraries) {
    for (Library library : libraries) {
      if (ArrayUtil.contains(root.getFile(), library.getFiles(root.getType()))) {
        return true;
      }
    }
    return false;
  }

  public void doChoose() {
    final FileChooserDescriptor chooserDescriptor;
    final List<Pair<LibraryRootsComponentDescriptor, FileChooserDescriptor>> descriptors = new ArrayList<Pair<LibraryRootsComponentDescriptor, FileChooserDescriptor>>();
    for (LibraryRootsComponentDescriptor componentDescriptor : myLibraryTypes.keySet()) {
      descriptors.add(Pair.create(componentDescriptor, componentDescriptor.createAttachFilesChooserDescriptor()));
    }
    if (descriptors.size() == 1) {
      chooserDescriptor = descriptors.get(0).getSecond();
    }
    else {
      chooserDescriptor = new FileChooserDescriptor(true, true, true, false, true, false) {
        @Override
        public boolean isFileSelectable(VirtualFile file) {
          for (Pair<LibraryRootsComponentDescriptor, FileChooserDescriptor> pair : descriptors) {
            if (pair.getSecond().isFileSelectable(file)) {
              return true;
            }
          }
          return false;
        }

        @Override
        public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
          for (Pair<LibraryRootsComponentDescriptor, FileChooserDescriptor> pair : descriptors) {
            if (pair.getSecond().isFileVisible(file, showHiddenFiles)) {
              return true;
            }
          }
          return false;
        }
      };
    }
    chooserDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT, myModule);

    final VirtualFile[] files = FileChooser.chooseFiles(myParentComponent, chooserDescriptor);
    if (files.length == 0) return;

    List<LibraryRootsComponentDescriptor> suitableDescriptors = new ArrayList<LibraryRootsComponentDescriptor>();
    for (Pair<LibraryRootsComponentDescriptor, FileChooserDescriptor> pair : descriptors) {
      if (acceptAll(pair.getSecond(), files)) {
        suitableDescriptors.add(pair.getFirst());
      }
    }

    final LibraryRootsComponentDescriptor rootsComponentDescriptor;
    if (suitableDescriptors.size() == 1) {
      rootsComponentDescriptor = suitableDescriptors.get(0);
      myLibraryType = myLibraryTypes.get(rootsComponentDescriptor);
    }
    else {
      rootsComponentDescriptor = myDefaultDescriptor;
    }
    myChosenRoots = RootDetectionUtil
        .detectRoots(Arrays.asList(files), myParentComponent, myModule.getProject(), rootsComponentDescriptor.getRootDetectors(), true);
  }

  private static boolean acceptAll(FileChooserDescriptor descriptor, VirtualFile[] files) {
    for (VirtualFile file : files) {
      if (!descriptor.isFileSelectable(file) || !descriptor.isFileVisible(file, true)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isOK() {
    return myChosenRoots != null && !myChosenRoots.isEmpty();
  }

  @Override
  public void dispose() {
  }
}
