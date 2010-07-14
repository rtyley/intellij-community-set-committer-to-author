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

package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
public class LibraryRuntimeClasspathScope extends GlobalSearchScope {
  private final ProjectFileIndex myIndex;
  private final LinkedHashSet<VirtualFile> myEntries = new LinkedHashSet<VirtualFile>();
  private final List<Module> myModules;

  public LibraryRuntimeClasspathScope(final Project project, final List<Module> modules) {
    super(project);
    myModules = modules;
    myIndex = ProjectRootManager.getInstance(project).getFileIndex();
    for (Module module : modules) {
      buildEntries(module);
    }
  }

  public int hashCode() {
    return myModules.hashCode();
  }

  public boolean equals(Object object) {
    if (object == this) return true;
    if (object == null || object.getClass() != LibraryRuntimeClasspathScope.class) return false;

    final LibraryRuntimeClasspathScope that = (LibraryRuntimeClasspathScope)object;
    return that.myModules.equals(myModules);
  }

  private void buildEntries(@NotNull final Module module) {
    final Set<Sdk> myJDKProcessed = new THashSet<Sdk>();

    ModuleRootManager.getInstance(module).orderEntries().recursively().process(new RootPolicy<LinkedHashSet<VirtualFile>>() {
      public LinkedHashSet<VirtualFile> visitLibraryOrderEntry(final LibraryOrderEntry libraryOrderEntry,
                                                               final LinkedHashSet<VirtualFile> value) {
        ContainerUtil.addAll(value, libraryOrderEntry.getRootFiles(OrderRootType.CLASSES));
        return value;
      }

      public LinkedHashSet<VirtualFile> visitModuleSourceOrderEntry(final ModuleSourceOrderEntry moduleSourceOrderEntry,
                                                                    final LinkedHashSet<VirtualFile> value) {
        ContainerUtil.addAll(value, moduleSourceOrderEntry.getFiles(OrderRootType.SOURCES));
        return value;
      }

      public LinkedHashSet<VirtualFile> visitJdkOrderEntry(final JdkOrderEntry jdkOrderEntry, final LinkedHashSet<VirtualFile> value) {
        if (!myJDKProcessed.add(jdkOrderEntry.getJdk())) return value;
        ContainerUtil.addAll(value, jdkOrderEntry.getRootFiles(OrderRootType.CLASSES));
        return value;
      }
    }, myEntries);
  }

  public boolean contains(VirtualFile file) {
    return myEntries.contains(getFileRoot(file));
  }

  @Nullable
  private VirtualFile getFileRoot(VirtualFile file) {
    if (myIndex.isLibraryClassFile(file)) {
      return myIndex.getClassRootForFile(file);
    }
    if (myIndex.isInContent(file)) {
      return myIndex.getSourceRootForFile(file);
    }
    if (myIndex.isInLibraryClasses(file)) {
      return myIndex.getClassRootForFile(file);
    }
    return null;
  }

  public int compare(VirtualFile file1, VirtualFile file2) {
    final VirtualFile r1 = getFileRoot(file1);
    final VirtualFile r2 = getFileRoot(file2);
    for (VirtualFile root : myEntries) {
      if (r1 == root) return 1;
      if (r2 == root) return -1;
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
