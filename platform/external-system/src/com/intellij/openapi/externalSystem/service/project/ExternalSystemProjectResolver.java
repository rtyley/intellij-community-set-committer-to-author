/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.project.ExternalProject;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemExecutionSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines common interface for resolving external system project..
 * 
 * @author Denis Zhdanov
 * @since 4/9/13 3:53 PM
 */
public interface ExternalSystemProjectResolver<S extends ExternalSystemExecutionSettings> {

  /**
   * Builds object-level representation of the external system config file contained at the given path.
   *
   * @param id                id of the current 'resolve project info' task
   * @param projectPath       absolute path to the target external system config file
   * @param downloadLibraries a hint that specifies if third-party libraries that are not available locally should be resolved (downloaded)
   * @param settings          settings to use for the project resolving;
   *                          <code>null</code> as indication that no specific settings are required
   * @return object-level representation of the target external system project;
   *                          <code>null</code> if it's not possible to resolve the project due to the objective reasons
   * @throws ExternalSystemException   in case when unexpected exception occurs during project info construction
   * @throws IllegalArgumentException  if given path is invalid
   * @throws IllegalStateException     if it's not possible to resolve target project info
   */
  @Nullable
  ExternalProject resolveProjectInfo(@NotNull ExternalSystemTaskId id,
                                     @NotNull String projectPath,
                                     boolean downloadLibraries,
                                     @Nullable S settings)
    throws ExternalSystemException, IllegalArgumentException, IllegalStateException;
}
