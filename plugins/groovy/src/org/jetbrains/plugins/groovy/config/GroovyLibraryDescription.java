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
package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.libraries.LibraryPresentationProvider;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager;
import com.intellij.openapi.roots.ui.configuration.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.util.GroovyUtils;

import javax.swing.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
* @author nik
*/
public class GroovyLibraryDescription extends CustomLibraryDescription {
  private final Condition<List<VirtualFile>> myCondition;
  private String myEnvVariable;

  public GroovyLibraryDescription() {
    this("GROOVY_HOME", getAllGroovyKinds());
  }

  private static Set<? extends LibraryKind<?>> getAllGroovyKinds() {
    final HashSet<LibraryKind<?>> kinds = new HashSet<LibraryKind<?>>();
    for (LibraryPresentationProvider provider : LibraryPresentationProvider.EP_NAME.getExtensions()) {
      if (provider instanceof GroovyLibraryPresentationProviderBase) {
        kinds.add(provider.getKind());
      }
    }
    return kinds;
  }

  public GroovyLibraryDescription(@NotNull String envVariable, @NotNull LibraryKind<?> libraryKind) {
    this(envVariable, Collections.singleton(libraryKind));
  }

  public GroovyLibraryDescription(@NotNull String envVariable, @NotNull final Set<? extends LibraryKind<?>> libraryKinds) {
    myEnvVariable = envVariable;
    myCondition = new Condition<List<VirtualFile>>() {
      @Override
      public boolean value(List<VirtualFile> virtualFiles) {
        return LibraryPresentationManager.getInstance().isLibraryOfKind(virtualFiles, libraryKinds);
      }
    };
  }

  @Nullable
  public static GroovyLibraryPresentationProviderBase findManager(@NotNull VirtualFile dir) {
    if (GroovyUtils.getFilesInDirectoryByPattern(dir.getPath() + "/lib", "groovy.*\\.jar").length == 0) {
      return null;
    }

    final String name = dir.getName();

    final List<GroovyLibraryPresentationProviderBase> providers = ContainerUtil.findAll(LibraryPresentationProvider.EP_NAME.getExtensions(), GroovyLibraryPresentationProviderBase.class);
    for (final GroovyLibraryPresentationProviderBase provider : providers) {
      if (provider.managesName(name) && provider.isSDKHome(dir)) {
        return provider;
      }
    }

    for (final GroovyLibraryPresentationProviderBase manager : providers) {
      if (manager.isSDKHome(dir)) {
        return manager;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public Condition<List<VirtualFile>> getSuitableLibraryCondition() {
    return myCondition;
  }

  @Override
  public NewLibraryConfiguration createNewLibrary(@NotNull JComponent parentComponent, VirtualFile contextDirectory) {
    final String envHome = System.getenv(myEnvVariable);
    VirtualFile initial = null;
    if (envHome != null && envHome.length() > 0) {
      initial = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(envHome));
    }

    final VirtualFile[] files = FileChooser.chooseFiles(parentComponent, FileChooserDescriptorFactory.createSingleFolderDescriptor(), initial);
    if (files.length != 1) return null;

    final VirtualFile dir = files[0];
    final GroovyLibraryPresentationProviderBase provider = findManager(dir);
    if (provider == null) {
      //todo[nik,peter] show error message in file chooser
      return null;
    }

    final String path = dir.getPath();
    final String sdkVersion = provider.getSDKVersion(path);
    if (AbstractConfigUtils.UNDEFINED_VERSION.equals(sdkVersion)) {
      return null;
    }

    return new NewLibraryConfiguration(provider.getLibraryPrefix() + "-" + sdkVersion) {
      @Override
      public void addRoots(@NotNull LibraryEditor editor) {
        provider.fillLibrary(path, editor);
      }
    };
  }
}
