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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ExcludeFolder;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class JavaContentEntryEditor extends ContentEntryEditor {
  private final CompilerModuleExtension myCompilerExtension;

  public JavaContentEntryEditor(final String contentEntryUrl) {
    super(contentEntryUrl, true, true);
    myCompilerExtension = getModel().getModuleExtension(CompilerModuleExtension.class);
  }

  protected ContentRootPanel createContentRootPane() {
    return new JavaContentRootPanel(this) {
      @Nullable
      @Override
      protected ContentEntry getContentEntry() {
        return JavaContentEntryEditor.this.getContentEntry();
      }
    };
  }

  @Override
  protected ExcludeFolder doAddExcludeFolder(@NotNull final String path) {
    final boolean isCompilerOutput = isCompilerOutput(path);
    final boolean isExplodedDirectory = isExplodedDirectory(path);
    if (isCompilerOutput || isExplodedDirectory) {
      if (isCompilerOutput) {
        myCompilerExtension.setExcludeOutput(true);
      }
      if (isExplodedDirectory) {
        getModel().setExcludeExplodedDirectory(true);
      }
      return null;
    }
    return super.doAddExcludeFolder(path);
  }

  @Override
  protected void doRemoveExcludeFolder(@NotNull final ExcludeFolder excludeFolder) {
    final String path = VfsUtil.urlToPath(excludeFolder.getUrl());
    if (isCompilerOutput(path)) {
      myCompilerExtension.setExcludeOutput(false);
    }
    if (isExplodedDirectory(path)) {
      getModel().setExcludeExplodedDirectory(false);
    }
    super.doRemoveExcludeFolder(excludeFolder);
  }

  private boolean isCompilerOutput(final String path) {
    final String compilerOutputPath = VfsUtil.urlToPath(myCompilerExtension.getCompilerOutputUrl());
    if (compilerOutputPath.equals(path)) {
      return true;
    }

    final String compilerOutputPathForTests = VfsUtil.urlToPath(myCompilerExtension.getCompilerOutputUrlForTests());
    if (compilerOutputPathForTests.equals(path)) {
      return true;
    }

    if (myCompilerExtension.isCompilerOutputPathInherited()) {
      final ProjectStructureConfigurable instance = ProjectStructureConfigurable.getInstance(getModel().getModule().getProject());
      final String compilerOutput = VfsUtil.urlToPath(instance.getProjectConfig().getCompilerOutputUrl());
      if (compilerOutput.equals(path)) {
        return true;
      }
    }

    return false;
  }

  private boolean isExplodedDirectory(final String path) {
    final String explodedUrl = getModel().getExplodedDirectoryUrl();
    return explodedUrl != null && VfsUtil.urlToPath(explodedUrl).equals(path);
  }
}
