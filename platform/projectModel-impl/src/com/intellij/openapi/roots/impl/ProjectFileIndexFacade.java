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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class ProjectFileIndexFacade extends FileIndexFacade {
  private final DirectoryIndex myDirectoryIndex;
  private final ProjectFileIndex myFileIndex;

  public ProjectFileIndexFacade(final Project project, final ProjectRootManager rootManager, final DirectoryIndex directoryIndex) {
    super(project);
    myDirectoryIndex = directoryIndex;
    myFileIndex = rootManager.getFileIndex();
  }

  public boolean isInContent(final VirtualFile file) {
    return myFileIndex.isInContent(file);
  }

  @Override
  public boolean isInSource(VirtualFile file) {
    return myFileIndex.isInSource(file);
  }

  @Override
  public boolean isInSourceContent(VirtualFile file) {
    return myFileIndex.isInSourceContent(file);
  }

  @Override
  public boolean isInLibraryClasses(VirtualFile file) {
    return myFileIndex.isInLibraryClasses(file);
  }

  @Override
  public boolean isInSdkClasses(VirtualFile file) {
    return ContainerUtil.findInstance(myFileIndex.getOrderEntriesForFile(file), JdkOrderEntry.class) != null;
  }

  @Override
  public boolean isInLibrarySource(VirtualFile file) {
    return myFileIndex.isInLibrarySource(file);
  }

  public boolean isExcludedFile(final VirtualFile file) {
    return myFileIndex.isIgnored(file);
  }

  @Nullable
  @Override
  public Module getModuleForFile(VirtualFile file) {
    return myFileIndex.getModuleForFile(file);
  }

  public boolean isValidAncestor(final VirtualFile baseDir, VirtualFile childDir) {
    if (!childDir.isDirectory()) {
      childDir = childDir.getParent();
    }
    while (true) {
      if (childDir == null) return false;
      if (childDir.equals(baseDir)) return true;
      if (myDirectoryIndex.getInfoForDirectory(childDir) == null) return false;
      childDir = childDir.getParent();
    }
  }
}
