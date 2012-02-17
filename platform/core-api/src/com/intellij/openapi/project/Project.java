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

  @NotNull
  @NonNls
  String getName();

  /**
   * <p>Returns a project base directory - a parent directory of a <code>.ipr</code> file or <code>.idea</code> directory.<br/>
   * Returns <code>null</code> for default project.</p>
   *
   * <p>Please note that returned file is always de-referenced, so you have to use use {@linkplain #getBasePath()}
   * if it's desired to keep symlinks in original path.</p>
   *
   * @return project base directory, or null for default project
   * todo: check usages
   */
  @Nullable
  VirtualFile getBaseDir();

  /**
   * Returns a system-dependent path to a project base directory (see {@linkplain #getBaseDir()}).<br/>
   * Returns <code>null</code> for default project.
   *
   * @return a path to a project base directory, or empty string for default project
   */
  @Nullable
  @NonNls
  String getBasePath();

  /**
   * <p>Returns project descriptor file:
   * <ul>
   *   <li><code>path/to/project/project.ipr</code> - for file-based projects</li>
   *   <li><code>path/to/project/.idea/misc.xml</code> - for directory-based projects</li>
   * </ul>
   * Returns <code>null</code> for default project.</p>
   *
   * <p>Please note that returned file is always de-referenced, so you have to use use {@linkplain #getProjectFilePath()}
   * if it's desired to keep symlinks in original path.</p>
   *
   * @return project descriptor file, or null for default project
   */
  @Nullable
  VirtualFile getProjectFile();

  /**
   * Returns a system-dependent path to project descriptor file (see {@linkplain #getProjectFile()}).<br/>
   * Returns empty string (<code>""</code>) for default project.
   *
   * @return project descriptor file, or empty string for default project
   */
  @NotNull
  @NonNls
  String getProjectFilePath();

  /**
   * Returns presentable project path:
   * {@linkplain #getProjectFilePath()} for file-based projects, {@linkplain #getLocation()} for directory-based ones.
   *
   * @return presentable project path
   * todo: check usages
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

  /**
   * @deprecated please use {@linkplain #getPresentableUrl()} or {@linkplain #getBasePath()} (to remove in IDEA 13).
   * todo: remove usages
   */
  @Nullable
  @NonNls
  String getLocation();

  void save();

  boolean isOpen();

  boolean isInitialized();

  boolean isDefault();
}
