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

/*
 * User: anna
 * Date: 16-Jan-2008
 */
package com.intellij.packageDependencies.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyUISettings;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

public class ProjectPatternProvider extends PatternDialectProvider {

  @NonNls public static final String FILE = "file";

  private static final Icon COMPACT_EMPTY_MIDDLE_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/compactEmptyPackages.png");
  private static final Icon ICON = IconLoader.getIcon("/general/projectTab.png");
  private static final Logger LOG = Logger.getInstance("#" + ProjectPatternProvider.class.getName());


  public TreeModel createTreeModel(final Project project, final Marker marker) {
    return FileTreeModelBuilder.createTreeModel(project, false, marker);
  }

  public TreeModel createTreeModel(final Project project, final Set<PsiFile> deps, final Marker marker,
                                   final DependenciesPanel.DependencyPanelSettings settings) {
    return FileTreeModelBuilder.createTreeModel(project, false, deps, marker, settings);
  }

  public String getDisplayName() {
    return IdeBundle.message("title.project");
  }

  @NotNull
  public String getShortName() {
    return FILE;
  }

  public AnAction[] createActions(Project project, final Runnable update) {
    if (ProjectViewDirectoryHelper.getInstance(project).supportsHideEmptyMiddlePackages()) {
      return new AnAction[]{new CompactEmptyMiddlePackagesAction(update)};
    }
    return AnAction.EMPTY_ARRAY;
  }

  @Nullable
  public PackageSet createPackageSet(final PackageDependenciesNode node, final boolean recursively) {
    if (node instanceof ModuleGroupNode) {
      if (!recursively) return null;
      @NonNls final String modulePattern = "group:" + ((ModuleGroupNode)node).getModuleGroup().toString();
      return new FilePatternPackageSet(modulePattern, "*//*");
    }
    else if (node instanceof ModuleNode) {
      if (!recursively) return null;
      final String modulePattern = ((ModuleNode)node).getModuleName();
      return new FilePatternPackageSet(modulePattern, "*/");
    }

    else if (node instanceof DirectoryNode) {
      String pattern = ((DirectoryNode)node).getFQName();
      if (pattern != null) {
        if (pattern.length() > 0) {
          pattern += recursively ? "//*" : "/*";
        }
        else {
          pattern += recursively ? "*/" : "*";
        }
      }
      return new FilePatternPackageSet(getModulePattern(node), pattern);
    }
    else if (node instanceof FileNode) {
      if (recursively) return null;
      FileNode fNode = (FileNode)node;
      final PsiFile file = (PsiFile)fNode.getPsiElement();
      final VirtualFile virtualFile = file.getVirtualFile();
      LOG.assertTrue(virtualFile != null);
      final VirtualFile contentRoot = ProjectRootManager.getInstance(file.getProject()).getFileIndex().getContentRootForFile(virtualFile);
      if (contentRoot == null) return null;
      final String fqName = VfsUtil.getRelativePath(virtualFile, contentRoot, '/');
      if (fqName != null) return new FilePatternPackageSet(getModulePattern(node), fqName);
    }
    return null;
  }

  public Icon getIcon() {
    return ICON;
  }

  private static final class CompactEmptyMiddlePackagesAction extends ToggleAction {
    private final Runnable myUpdate;

    CompactEmptyMiddlePackagesAction(Runnable update) {
      super(IdeBundle.message("action.compact.empty.middle.packages"),
            IdeBundle.message("action.compact.empty.middle.packages"), COMPACT_EMPTY_MIDDLE_PACKAGES_ICON);
      myUpdate = update;
    }

    public boolean isSelected(AnActionEvent event) {
      return DependencyUISettings.getInstance().UI_COMPACT_EMPTY_MIDDLE_PACKAGES;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      DependencyUISettings.getInstance().UI_COMPACT_EMPTY_MIDDLE_PACKAGES = flag;
      myUpdate.run();
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(DependencyUISettings.getInstance().SCOPE_TYPE == FILE);
    }
  }
}