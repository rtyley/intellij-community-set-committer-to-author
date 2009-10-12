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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.util.projectWizard.JdkChooserPanel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class IdeaProjectSettingsService extends ProjectSettingsService {
  private final Project myProject;

  public IdeaProjectSettingsService(final Project project) {
    myProject = project;
  }

  public void openModuleSettings(final Module module) {
    ModulesConfigurator.showDialog(myProject, module.getName(), null, false);
  }

  public void openModuleLibrarySettings(final Module module) {
    ModulesConfigurator.showDialog(myProject, module.getName(), ClasspathEditor.NAME, false);
  }

  public void openContentEntriesSettings(final Module module) {
    ModulesConfigurator.showDialog(myProject, module.getName(), ContentEntriesEditor.NAME, false);
  }

  public void openProjectLibrarySettings(final NamedLibraryElement element) {
    final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(myProject);
    ShowSettingsUtil.getInstance().editConfigurable(myProject, config, new Runnable() {
      public void run() {
        final OrderEntry orderEntry = element.getOrderEntry();
        if (orderEntry instanceof JdkOrderEntry) {
          config.select(((JdkOrderEntry)orderEntry).getJdk(), true);
        } else {
          config.select((LibraryOrderEntry)orderEntry, true);
        }
      }
    });
  }

  public boolean processModulesMoved(final Module[] modules, @Nullable final ModuleGroup targetGroup) {
    final ModuleStructureConfigurable rootConfigurable = ModuleStructureConfigurable.getInstance(myProject);
    if (rootConfigurable.updateProjectTree(modules, targetGroup)) { //inside project root editor
      if (targetGroup != null) {
        rootConfigurable.selectNodeInTree(targetGroup.toString());
      }
      else {
        rootConfigurable.selectNodeInTree(modules[0].getName());
      }
      return true;
    }
    return false;
  }

  @Override
  public void showModuleConfigurationDialog(String moduleToSelect, String tabNameToSelect, boolean showModuleWizard) {
    ModulesConfigurator.showDialog(myProject, moduleToSelect, tabNameToSelect, showModuleWizard);
  }

  @Override
  public Sdk chooseAndSetSdk() {
    return JdkChooserPanel.chooseAndSetJDK(myProject);
  }
}
