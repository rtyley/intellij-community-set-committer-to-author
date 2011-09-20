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

package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ExcludedFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class ProjectExcludedFileIndex extends ExcludedFileIndex {
  private final ProjectRootManager myRootManager;
  private final DirectoryIndex myDirectoryIndex;

  public ProjectExcludedFileIndex(final Project project, final ProjectRootManager rootManager, final DirectoryIndex directoryIndex) {
    super(project);
    myRootManager = rootManager;
    myDirectoryIndex = directoryIndex;
  }

  public boolean isInContent(final VirtualFile file) {
    return myRootManager.getFileIndex().isInContent(file);
  }

  @Override
  public boolean isInSource(VirtualFile file) {
    return myRootManager.getFileIndex().isInSource(file);
  }

  public boolean isExcludedFile(final VirtualFile file) {
    return myRootManager.getFileIndex().isIgnored(file);
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
