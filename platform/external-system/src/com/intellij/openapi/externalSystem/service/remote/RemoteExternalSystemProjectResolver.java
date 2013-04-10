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
package com.intellij.openapi.externalSystem.service.remote;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.project.ExternalProject;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.RemoteExternalSystemService;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.rmi.RemoteException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 4/9/13 6:56 PM
 */
public interface RemoteExternalSystemProjectResolver<S extends ExternalSystemExecutionSettings> extends RemoteExternalSystemService<S> {

  /** <a href="http://en.wikipedia.org/wiki/Null_Object_pattern">Null object</a> for {@link RemoteExternalSystemProjectResolverImpl}. */
  RemoteExternalSystemProjectResolver<ExternalSystemExecutionSettings> NULL_OBJECT
    = new RemoteExternalSystemProjectResolver<ExternalSystemExecutionSettings>() {
    @Nullable
    @Override
    public ExternalProject resolveProjectInfo(@NotNull ExternalSystemTaskId id,
                                              @NotNull String projectPath,
                                              boolean downloadLibraries,
                                              @Nullable ExternalSystemExecutionSettings settings)
      throws ExternalSystemException, IllegalArgumentException, IllegalStateException
    {
      return null;
    }

    @Override
    public void setSettings(@NotNull ExternalSystemExecutionSettings settings) throws RemoteException {
    }

    @Override
    public void setNotificationListener(@NotNull ExternalSystemTaskNotificationListener notificationListener) throws RemoteException {
    }

    @Override
    public boolean isTaskInProgress(@NotNull ExternalSystemTaskId id) throws RemoteException {
      return false;
    }

    @NotNull
    @Override
    public Map<ExternalSystemTaskType, Set<ExternalSystemTaskId>> getTasksInProgress() throws RemoteException {
      return Collections.emptyMap();
    }
  };


  @Nullable
  ExternalProject resolveProjectInfo(@NotNull ExternalSystemTaskId id,
                                     @NotNull String projectPath,
                                     boolean downloadLibraries,
                                     @Nullable S settings)
    throws RemoteException, ExternalSystemException, IllegalArgumentException, IllegalStateException;
}
