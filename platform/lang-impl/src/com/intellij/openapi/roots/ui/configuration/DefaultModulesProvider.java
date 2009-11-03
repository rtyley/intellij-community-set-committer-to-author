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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.facet.FacetModel;
import com.intellij.facet.FacetManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class DefaultModulesProvider implements ModulesProvider {
  private final Project myProject;

  public DefaultModulesProvider(final Project project) {
    myProject = project;
  }

  @NotNull
  public Module[] getModules() {
    return ModuleManager.getInstance(myProject).getModules();
  }

  public Module getModule(String name) {
    return ModuleManager.getInstance(myProject).findModuleByName(name);
  }

  public ModuleRootModel getRootModel(@NotNull Module module) {
    return ModuleRootManager.getInstance(module);
  }

  public FacetModel getFacetModel(@NotNull Module module) {
    return FacetManager.getInstance(module);
  }
}
