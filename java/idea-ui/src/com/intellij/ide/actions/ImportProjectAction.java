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
package com.intellij.ide.actions;

import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 11/6/12
 */
public class ImportProjectAction extends ImportModuleAction {

  @Override
  public List<Module> doImport(Project project, @NotNull VirtualFile file) {
    return super.doImport(null, file);
  }

  @Override
  public List<Module> createFromWizard(Project project, AddModuleWizard wizard) {
    Project newProject = NewProjectUtil.createFromWizard(wizard, project);
    return  newProject == null ? Collections.<Module>emptyList() : Arrays.asList(ModuleManager.getInstance(newProject).getModules());
  }

  @Override
  public void update(AnActionEvent e) {

  }
}
