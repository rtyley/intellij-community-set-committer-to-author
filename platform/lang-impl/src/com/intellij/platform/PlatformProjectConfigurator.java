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

package com.intellij.platform;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ide.highlighter.ModuleFileType;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PlatformProjectConfigurator implements DirectoryProjectConfigurator {
  public void configureProject(final Project project, @NotNull final VirtualFile baseDir) {
    final ModuleManager moduleManager = ModuleManager.getInstance(project);
    final Module[] modules = moduleManager.getModules();
    if (modules.length == 0) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          String moduleName = baseDir.getName().replace(":", "");     // correct module name when opening root of drive as project (RUBY-5181)
          String imlName = baseDir.getPath() + "/.idea/" + moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION;
          final Module module = moduleManager.newModule(imlName, ModuleTypeManager.getInstance().getDefaultModuleType());
          ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
          rootModel.addContentEntry(baseDir);
          rootModel.inheritSdk();
          rootModel.commit();
        }
      });
    }
  }
}
