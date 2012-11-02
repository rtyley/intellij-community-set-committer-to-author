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
package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.newProjectWizard.AddModuleWizardPro;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 5, 2004
 */
public class NewModuleAction extends AnAction implements DumbAware {
  public NewModuleAction() {
    super(ProjectBundle.message("module.new.action"), ProjectBundle.message("module.new.action.description"), null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) {
      return;
    }
    Object dataFromContext = prepareDataFromContext(e);

    String defaultPath = null;
    final VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    if (virtualFile != null && virtualFile.isDirectory()) {
      defaultPath = virtualFile.getPath();
    }
    final AddModuleWizard wizard = Registry.is("new.project.wizard")
                                   ? new AddModuleWizardPro(project, new DefaultModulesProvider(project), defaultPath)
                                   : new AddModuleWizard(project, new DefaultModulesProvider(project), defaultPath);

    wizard.show();

    if (wizard.isOK()) {
      createModuleFromWizard(project, dataFromContext, wizard);
    }
  }

  @Nullable
  public Module createModuleFromWizard(Project project, @Nullable Object dataFromContext, AddModuleWizard wizard) {
    final ProjectBuilder builder = wizard.getProjectBuilder();
    if (builder instanceof ModuleBuilder) {
      final ModuleBuilder moduleBuilder = (ModuleBuilder)builder;
      if (moduleBuilder.getName() == null) {
        moduleBuilder.setName(wizard.getProjectName());
      }
      if (moduleBuilder.getModuleFilePath() == null) {
        moduleBuilder.setModuleFilePath(wizard.getModuleFilePath());
      }
    }
    if (!builder.validate(project, project)) {
      return null;
    }
    Module module;
    if (builder instanceof ModuleBuilder) {
      module = ((ModuleBuilder) builder).commitModule(project, null);
      if (module != null) {
        processCreatedModule(module, dataFromContext);
      }
      return module;
    }
    else {
      List<Module> modules = builder.commit(project, null, new DefaultModulesProvider(project));
      if (builder.isOpenProjectSettingsAfter()) {
        ModulesConfigurator.showDialog(project, null, null);
      }
      module = modules == null || modules.isEmpty() ? null : modules.get(0);
    }
    project.save();
    return module;
  }

  @Nullable
  protected Object prepareDataFromContext(final AnActionEvent e) {
    return null;
  }

  protected void processCreatedModule(final Module module, @Nullable final Object dataFromContext) {
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(getEventProject(e) != null);
  }
}
