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
package com.intellij.openapi.project;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.util.Condition;
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
   * @deprecated Because of multiple possible project formats, it is not allowed to call {@link #getProjectFile} anymore. Other methods should
   * be used for project introspections, such as {@link #getPresentableUrl()},  {@link #getBaseDir()}, etc.
   */
  @Nullable
  VirtualFile getProjectFile();

  /**
   * @deprecated Because of multiple possible project formats, it is not allowed to call {@link #getWorkspaceFile} anymore. Other methods should
   * be used for project introspections, such as {@link #getPresentableUrl()},  {@link #getBaseDir()}, etc.
   */
  @Nullable
  VirtualFile getWorkspaceFile();

  /**
   * @deprecated Since multiple possible project formats it is not allowed to ask for getProjectFile. Other methods should
   * be used for project introspections, such as {@link #getPresentableUrl()},  {@link #getBaseDir()}, etc.
   */
  @NotNull
  String getProjectFilePath();


  @Nullable
  VirtualFile getBaseDir();

  @NotNull
  @NonNls
  String getName();

  @Nullable
  @NonNls
  String getPresentableUrl();

  @NotNull
  @NonNls
  String getLocationHash();


  @NotNull
  @NonNls
  String getLocation();

  void save();

  Condition getDisposed();

  boolean isOpen();

  boolean isInitialized();

  boolean isDefault();
}
