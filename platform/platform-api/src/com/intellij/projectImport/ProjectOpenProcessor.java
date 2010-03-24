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
 * @author max
 */
package com.intellij.projectImport;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ProjectOpenProcessor {
  public static final ExtensionPointName<ProjectOpenProcessor> EXTENSION_POINT_NAME = new ExtensionPointName<ProjectOpenProcessor>("com.intellij.projectOpenProcessor");

  public abstract String getName();

  @Nullable
  public abstract Icon getIcon();

  public abstract boolean canOpenProject(VirtualFile file);

  @Nullable
  public abstract Project doOpenProject(@NotNull VirtualFile virtualFile, Project projectToClose, boolean forceOpenInNewFrame);

  @Nullable
  public static ProjectOpenProcessor getImportProvider(VirtualFile file) {
    for (ProjectOpenProcessor provider : Extensions.getExtensions(EXTENSION_POINT_NAME)) {
      if (provider.canOpenProject(file)) {
        return provider;
      }
    }
    return null;
  }
}