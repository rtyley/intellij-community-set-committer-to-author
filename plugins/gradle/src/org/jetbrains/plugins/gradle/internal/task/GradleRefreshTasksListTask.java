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
package org.jetbrains.plugins.gradle.internal.task;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskDescriptor;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.ExternalSystemFacadeManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings;
import com.intellij.openapi.externalSystem.build.ExternalSystemBuildManager;
import org.jetbrains.plugins.gradle.tasks.GradleTasksModel;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.Collection;

/**
 * @author Denis Zhdanov
 * @since 3/15/13 1:20 PM
 */
public class GradleRefreshTasksListTask extends AbstractGradleTask {

  @NotNull private final String myProjectPath;

  public GradleRefreshTasksListTask(@Nullable Project project, @NotNull String projectPath) {
    super(project, ExternalSystemTaskType.REFRESH_TASKS_LIST);
    myProjectPath = projectPath;
  }

  @Override
  protected void doExecute() throws Exception {
    final ExternalSystemFacadeManager manager = ServiceManager.getService(ExternalSystemFacadeManager.class);
    Project project = getIdeProject();
    ExternalSystemBuildManager buildManager = manager.getFacade(project).getBuildManager();
    setState(GradleTaskState.IN_PROGRESS);
    try {
      final Collection<ExternalSystemTaskDescriptor> descriptors = buildManager.listTasks(getId(), myProjectPath);
      if (descriptors == null || descriptors.isEmpty()) {
        return;
      }
      
      if (project == null) {
        return;
      }

      GradleLocalSettings settings = GradleLocalSettings.getInstance(project);
      settings.setAvailableTasks(descriptors);
      
      final GradleTasksModel tasksModel = GradleUtil.getToolWindowElement(GradleTasksModel.class, project, ExternalSystemDataKeys.ALL_TASKS_MODEL);
      if (tasksModel == null) {
        return;
      }
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          tasksModel.setTasks(descriptors); 
        }
      });
    }
    finally {
      setState(GradleTaskState.FINISHED);
    }
  }

  @Override
  @NotNull
  protected String wrapProgressText(@NotNull String text) {
    return ExternalSystemBundle.message("gradle.tasks.progress.update.text", text);
  }
}
