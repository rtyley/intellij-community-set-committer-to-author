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
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;

/**
 * @author nik
 */
public abstract class LibraryScopeBase extends GlobalSearchScope {
  private final LinkedHashSet<VirtualFile> myEntries;
  private final ProjectFileIndex myIndex;

  public LibraryScopeBase(Project project, VirtualFile[] classes, VirtualFile[] sources) {
    super(project);
    myIndex = ProjectRootManager.getInstance(project).getFileIndex();
    myEntries = new LinkedHashSet<VirtualFile>(classes.length + sources.length);
    Collections.addAll(myEntries, classes);
    Collections.addAll(myEntries, sources);
  }

  public boolean contains(VirtualFile file) {
    return myEntries.contains(getFileRoot(file));
  }

  @Nullable
  private VirtualFile getFileRoot(VirtualFile file) {
    if (myIndex.isInLibraryClasses(file)) {
      return myIndex.getClassRootForFile(file);
    }
    if (myIndex.isInContent(file)) {
      return myIndex.getSourceRootForFile(file);
    }
    return null;
  }

  public int compare(VirtualFile file1, VirtualFile file2) {
    final VirtualFile r1 = getFileRoot(file1);
    final VirtualFile r2 = getFileRoot(file2);
    for (VirtualFile root : myEntries) {
      if (Comparing.equal(r1, root)) return 1;
      if (Comparing.equal(r2, root)) return -1;
    }
    return 0;
  }

  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return false;
  }

  public boolean isSearchInLibraries() {
    return true;
  }
}
