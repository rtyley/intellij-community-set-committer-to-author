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
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.LibrariesElement;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PackageViewLibrariesNode extends ProjectViewNode<LibrariesElement>{
  public PackageViewLibrariesNode(final Project project, Module module, final ViewSettings viewSettings) {
    super(project, new LibrariesElement(module, project), viewSettings);
  }

  public boolean contains(@NotNull final VirtualFile file) {
    return someChildContainsFile(file);
  }

  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    final ArrayList<VirtualFile> roots = new ArrayList<VirtualFile>();
    if (getValue().getModule() == null) {
      final Module[] modules = ModuleManager.getInstance(getProject()).getModules();

      for (int i = 0; i < modules.length; i++) {
        Module module = modules[i];
        addModuleLibraryRoots(ModuleRootManager.getInstance(module), roots);
      }

    } else {
      addModuleLibraryRoots(ModuleRootManager.getInstance(getValue().getModule()), roots);
    }
    return PackageUtil.createPackageViewChildrenOnFiles(roots, getProject(), getSettings(), null, true);
  }

  private static void addModuleLibraryRoots(ModuleRootManager moduleRootManager, List<VirtualFile> roots) {
    final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
    for (int idx = 0; idx < orderEntries.length; idx++) {
      final OrderEntry orderEntry = orderEntries[idx];
      if (!(orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry)) {
        continue;
      }
      final VirtualFile[] files = orderEntry.getFiles(OrderRootType.CLASSES);
      for (int i = 0; i < files.length; i++) {
        final VirtualFile file = files[i];
        if (file.getFileSystem() instanceof JarFileSystem && file.getParent() != null) {
          // skip entries inside jars
          continue;
        }
        roots.add(file);
      }
    }
  }

  public void update(final PresentationData presentation) {
    presentation.setPresentableText(IdeBundle.message("node.projectview.libraries"));
    presentation.setIcons(Icons.LIBRARY_ICON);
  }

  public String getTestPresentation() {
    return "Libraries";
  }

  public boolean shouldUpdateData() {
    return true;
  }

  public int getWeight() {
    return 60;
  }
}
