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
package com.intellij.openapi.project;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Project interface class.
 */
public interface Project extends ComponentManager, AreaInstance {
  @NonNls String DIRECTORY_STORE_FOLDER = ".idea";

  /**
   * Returns a name ot the project. For a directory-based project it's an arbitrary string specified by user at project creation
   * or later in a project settings. For a file-based project it's a name of a project file without extension.
   *
   * @return project name
   */
  @NotNull
  @NonNls
  String getName();

  /**
   * Returns a project base directory - a parent directory of a <code>.ipr</code> file or <code>.idea</code> directory.<br/>
   * Returns <code>null</code> for default project.
   *
   * @return project base directory, or null for default project
   */
  @Nullable
  VirtualFile getBaseDir();

  /**
   * Returns project descriptor file:
   * <ul>
   *   <li><code>path/to/project/project.ipr</code> - for file-based projects</li>
   *   <li><code>path/to/project/.idea/misc.xml</code> - for directory-based projects</li>
   * </ul>
   * Returns <code>null</code> for default project.
   *
   * @return project descriptor file, or null for default project
   */
  @Nullable
  VirtualFile getProjectFile();

  /**
   * Returns presentable project path:
   * {@linkplain #getProjectFile()} for file-based projects, {@linkplain #getBaseDir()} for directory-based ones.<br/>
   * Returns <code>null</code> for default project.
   * <b>Note:</b> the word "presentable" here implies file system presentation, not a UI one.
   *
   * @return presentable project path
   */
  @Nullable
  @NonNls
  String getPresentableUrl();

  /**
   * <p>Returns a workspace file:
   * <ul>
   *   <li><code>path/to/project/project.iws</code> - for file-based projects</li>
   *   <li><code>path/to/project/.idea/workspace.xml</code> - for directory-based ones</li>
   * </ul>
   * Returns <code>null</code> for default project.
   *
   * @return workspace file, or null for default project
   */
  @Nullable
  VirtualFile getWorkspaceFile();

  @NotNull
  @NonNls
  String getLocationHash();

  /** @deprecated please use {@linkplain #getProjectFile()} (to remove in IDEA 13). */
  @NotNull
  @NonNls
  String getProjectFilePath();

  /** @deprecated please use {@linkplain #getPresentableUrl()} or {@linkplain #getBaseDir()} (to remove in IDEA 13). */
  @Nullable
  @NonNls
  String getLocation();

  void save();

  boolean isOpen();

  boolean isInitialized();

  boolean isDefault();
}
