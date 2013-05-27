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
package com.intellij.openapi.externalSystem.service.internal;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.serialization.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskState;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.ExternalSystemFacadeManager;
import com.intellij.openapi.externalSystem.service.RemoteExternalSystemFacade;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemTaskManager;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 3/15/13 10:02 PM
 */
public class ExternalSystemExecuteTaskTask extends AbstractExternalSystemTask {

  @NotNull private static final Function<ExternalTaskPojo, String> MAPPER = new Function<ExternalTaskPojo, String>() {
    @Override
    public String fun(ExternalTaskPojo task) {
      return task.getName();
    }
  };

  @NotNull private final List<ExternalTaskPojo> myTasksToExecute;
  @Nullable private final String myVmOptions;

  public ExternalSystemExecuteTaskTask(@NotNull ProjectSystemId externalSystemId,
                                       @NotNull Project project,
                                       @NotNull List<ExternalTaskPojo> tasksToExecute,
                                       @Nullable String vmOptions) throws IllegalArgumentException
  {
    super(externalSystemId, ExternalSystemTaskType.EXECUTE_TASK, project, getLinkedExternalProjectPath(tasksToExecute));
    myTasksToExecute = tasksToExecute;
    myVmOptions = vmOptions;
  }

  @NotNull
  private static String getLinkedExternalProjectPath(@NotNull Collection<ExternalTaskPojo> tasks) throws IllegalArgumentException {
    if (tasks.isEmpty()) {
      throw new IllegalArgumentException("Can't execute external tasks. Reason: given tasks list is empty");
    }
    String result = null;
    for (ExternalTaskPojo task : tasks) {
      String path = task.getLinkedExternalProjectPath();
      if (result == null) {
        result = path;
      }
      else if (!result.equals(path)) {
        throw new IllegalArgumentException(String.format(
          "Can't execute given external system tasks. Reason: expected that all of them belong to the same external project " +
          "but they are not (at least two different projects detected - '%s' and '%s'). Tasks: %s",
          result,
          task.getLinkedExternalProjectPath(),
          tasks));
      }
    }
    assert result != null;
    return result;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void doExecute() throws Exception {
    final ExternalSystemFacadeManager manager = ServiceManager.getService(ExternalSystemFacadeManager.class);
    ExternalSystemSettingsManager settingsManager = ServiceManager.getService(ExternalSystemSettingsManager.class);
    ExternalSystemExecutionSettings settings = settingsManager.getExecutionSettings(getIdeProject(),
                                                                                    getExternalProjectPath(),
                                                                                    getExternalSystemId());
    RemoteExternalSystemFacade facade = manager.getFacade(getIdeProject(), getExternalProjectPath(), getExternalSystemId());
    RemoteExternalSystemTaskManager taskManager = facade.getTaskManager();
    List<String> taskNames = ContainerUtilRt.map2List(myTasksToExecute, MAPPER);

    setState(ExternalSystemTaskState.IN_PROGRESS);
    try {
      taskManager.executeTasks(getId(), taskNames, getExternalProjectPath(), settings, myVmOptions);
    }
    finally {
      setState(ExternalSystemTaskState.FINISHED);
    }
  }
}
