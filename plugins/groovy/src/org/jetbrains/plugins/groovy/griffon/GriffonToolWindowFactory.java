/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.griffon;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.mvc.projectView.*;

import javax.swing.*;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class GriffonToolWindowFactory extends MvcToolWindowDescriptor {
  public GriffonToolWindowFactory() {
    super(GriffonFramework.getInstance());
  }

  @Override
  public void fillModuleChildren(List<AbstractTreeNode> result, Module module, ViewSettings viewSettings, VirtualFile root) {
    final Project project = module.getProject();

    // process well-known artifact paths
    for (VirtualFile file : ModuleRootManager.getInstance(module).getSourceRoots()) {
      PsiDirectory sourceRoot = PsiManager.getInstance(project).findDirectory(file);
      if (sourceRoot != null) {
        if ("griffon-app".equals(file.getParent().getName())) {
          GriffonDirectoryMetadata metadata = DIRECTORY_METADATA.get(file.getName());
          if (metadata == null) continue;
          result.add(new TopLevelDirectoryNode(module, sourceRoot, viewSettings, metadata.description, metadata.icon, metadata.weight));
        }
      }
    }

    // add standard source folder
    final PsiDirectory srcMain = findDirectory(project, root, "src/main");
    if (srcMain != null) {
      result.add(new TopLevelDirectoryNode(module, srcMain, viewSettings, "Project Sources", GroovyIcons.GROOVY_ICON_16x16,
                                           AbstractMvcPsiNodeDescriptor.SRC_FOLDERS));
    }
    final PsiDirectory srcCli = findDirectory(project, root, "src/cli");
    if (srcCli != null) {
      result.add(new TopLevelDirectoryNode(module, srcCli, viewSettings, "Build Sources", GroovyIcons.GROOVY_ICON_16x16,
                                           AbstractMvcPsiNodeDescriptor.SRC_FOLDERS));
    }

    // add standard test sources
    final PsiDirectory testsUnit = findDirectory(project, root, "test/unit");
    if (testsUnit != null) {
      result.add(
        new TestsTopLevelDirectoryNode(module, testsUnit, viewSettings, "Unit Tests", PlatformIcons.TEST_SOURCE_FOLDER, PlatformIcons.TEST_SOURCE_FOLDER));
    }
    final PsiDirectory testsIntegration = findDirectory(project, root, "test/integration");
    if (testsIntegration != null) {
      result.add(new TestsTopLevelDirectoryNode(module, testsIntegration, viewSettings, "Integration Tests", PlatformIcons.TEST_SOURCE_FOLDER,
                                                PlatformIcons.TEST_SOURCE_FOLDER));
    }
    final PsiDirectory testsShared = findDirectory(project, root, "test/shared");
    if (testsShared != null) {
      result.add(new TestsTopLevelDirectoryNode(module, testsShared, viewSettings, "Shared Test Sources", PlatformIcons.TEST_SOURCE_FOLDER,
                                                PlatformIcons.TEST_SOURCE_FOLDER));
    }

    // add additional sources provided by plugins
    for (VirtualFile file : ModuleRootManager.getInstance(module).getContentRoots()) {
      List<GriffonSourceInspector.GriffonSource> sources = GriffonSourceInspector.processModuleMetadata(module);
      for (GriffonSourceInspector.GriffonSource source : sources) {
        final PsiDirectory dir = findDirectory(project, file, source.getPath());
        if (dir != null) {
          result.add(
            new TopLevelDirectoryNode(module, dir, viewSettings, source.getNavigation().getDescription(), source.getNavigation().getIcon(),
                                      source.getNavigation().getWeight()));
        }
      }
    }

    final VirtualFile applicationPropertiesFile = GriffonFramework.getInstance().getApplicationPropertiesFile(module);
    if (applicationPropertiesFile != null) {
      PsiFile appProperties = PsiManager.getInstance(module.getProject()).findFile(applicationPropertiesFile);
      if (appProperties != null) {
        result.add(new FileNode(module, appProperties, null, viewSettings));
      }
    }
  }

  @Override
  public Icon getModuleNodeIcon() {
    return GriffonFramework.GRIFFON_ICON;
  }

  private static final Map<String, GriffonDirectoryMetadata> DIRECTORY_METADATA = new LinkedHashMap<String, GriffonDirectoryMetadata>();

  static {
    DIRECTORY_METADATA.put("models", new GriffonDirectoryMetadata("Models", loadIcon("folder-models"), 20));
    DIRECTORY_METADATA.put("views", new GriffonDirectoryMetadata("Views", loadIcon("folder-views"), 30));
    DIRECTORY_METADATA.put("controllers", new GriffonDirectoryMetadata("Controllers", loadIcon("folder-controllers"), 40));
    DIRECTORY_METADATA.put("services", new GriffonDirectoryMetadata("Services", loadIcon("folder-services"), 50));
    DIRECTORY_METADATA.put("lifecycle", new GriffonDirectoryMetadata("Lifecycle", loadIcon("folder-lifecycle"), 60));
    DIRECTORY_METADATA.put("conf", new GriffonDirectoryMetadata("Configuration", loadIcon("folder-conf"), 65));
  }

  private static Icon loadIcon(String name) {
    return IconLoader.getIcon("/icons/griffon/" + name + ".png");
  }

  private static class GriffonDirectoryMetadata {
    public final String description;
    public final Icon icon;
    public final int weight;

    public GriffonDirectoryMetadata(String description, Icon icon, int weight) {
      this.description = description;
      this.icon = icon;
      this.weight = weight;
    }
  }
}
