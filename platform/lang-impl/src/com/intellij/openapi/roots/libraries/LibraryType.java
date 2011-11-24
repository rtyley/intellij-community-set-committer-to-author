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
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor;
import com.intellij.openapi.roots.libraries.ui.LibraryRootsComponentDescriptor;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Override this class to provide custom library type. The implementation should be registered in plugin.xml:
 * <p>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;library.type implementation="qualified-class-name"/&gt;<br>
 * &lt;/extensions&gt;
 *
 * @author nik
 */
public abstract class LibraryType<P extends LibraryProperties> extends LibraryPresentationProvider<P> {
  public static final ExtensionPointName<LibraryType<?>> EP_NAME = ExtensionPointName.create("com.intellij.library.type");
  
  public final static OrderRootType[] DEFAULT_EXTERNAL_ROOT_TYPES = {OrderRootType.CLASSES};

  protected LibraryType(@NotNull LibraryKind<P> libraryKind) {
    super(libraryKind);
  }

  /**
   * @return text to show in 'New Library' popup. Return {@code null} if the type should not be shown in the 'New Library' popup
   */
  @Nullable
  public abstract String getCreateActionName();

  /**
   * Called when a new library of this type is created in Project Structure dialog
   */
  @Nullable
  public abstract NewLibraryConfiguration createNewLibrary(@NotNull JComponent parentComponent, @Nullable VirtualFile contextDirectory,
                                                           @NotNull Project project);

  @NotNull
  public abstract P createDefaultProperties();

  /**
   * @return {@code true} if library of this type can be added as a dependency to {@code module}
   */
  public boolean isSuitableModule(@NotNull Module module, @NotNull FacetsProvider facetsProvider) {
    return true;
  }

  /**
   * Override this method to customize the library roots editor
   * @return {@link com.intellij.openapi.roots.libraries.ui.LibraryRootsComponentDescriptor} instance
   */
  @Nullable
  public LibraryRootsComponentDescriptor createLibraryRootsComponentDescriptor() {
    return null;
  }

  @Nullable
  public abstract LibraryPropertiesEditor createPropertiesEditor(@NotNull LibraryEditorComponent<P> editorComponent);

  @Override
  public P detect(@NotNull List<VirtualFile> classesRoots) {
    return null;
  }

  /**
   * @return Root types to collect library files which do not belong to the project and therefore
   *         indicate that the library is external.
   */
  public OrderRootType[] getExternalRootTypes() {
    return DEFAULT_EXTERNAL_ROOT_TYPES;
  }
  
  public OrderRootType[] getAdditionalRootTypes() {
    return new OrderRootType[0];
  }

  public boolean isFileBased() {
    return false;
  }

}
