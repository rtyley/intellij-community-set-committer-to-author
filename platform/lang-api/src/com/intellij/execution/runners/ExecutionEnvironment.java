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

package com.intellij.execution.runners;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.atomic.AtomicLong;


public class ExecutionEnvironment extends UserDataHolderBase {
  private static final AtomicLong myIdHolder = new AtomicLong(1L);

  @Nullable private final Project myProject;

  @NotNull private RunProfile myRunProfile;
  @NotNull private ExecutionTarget myTarget;

  @Nullable private RunnerSettings myRunnerSettings;
  @Nullable private ConfigurationPerRunnerSettings myConfigurationSettings;
  @Nullable private final RunnerAndConfigurationSettings myRunnerAndConfigurationSettings;
  @Nullable private RunContentDescriptor myContentToReuse;
  private long myExecutionId = 0;

  @TestOnly
  public ExecutionEnvironment() {
    myProject = null;
    myContentToReuse = null;
    myRunnerAndConfigurationSettings = null;
  }

  public ExecutionEnvironment(@NotNull final ProgramRunner runner,
                              @NotNull final RunnerAndConfigurationSettings configuration,
                              @Nullable Project project) {
    this(configuration.getConfiguration(),
         DefaultExecutionTarget.INSTANCE,
         project,
         configuration.getRunnerSettings(runner),
         configuration.getConfigurationSettings(runner),
         null,
         null);
  }

  /**
   * @deprecated, use {@link com.intellij.execution.runners.ExecutionEnvironmentBuilder} instead
   */
  public ExecutionEnvironment(@NotNull final ProgramRunner runner,
                              @NotNull final ExecutionTarget target,
                              @NotNull final RunnerAndConfigurationSettings configuration,
                              @Nullable RunContentDescriptor contentToReuse,
                              Project project) {
    this(configuration.getConfiguration(),
         target,
         project,
         configuration.getRunnerSettings(runner),
         configuration.getConfigurationSettings(runner),
         contentToReuse,
         configuration);
  }

  /**
   * @deprecated, use {@link com.intellij.execution.runners.ExecutionEnvironmentBuilder} instead
   */
  @TestOnly
  public ExecutionEnvironment(@NotNull final ProgramRunner runner,
                              @NotNull final ExecutionTarget target,
                              @NotNull final RunnerAndConfigurationSettings configuration,
                              Project project) {
    this(configuration.getConfiguration(),
         target,
         project,
         configuration.getRunnerSettings(runner),
         configuration.getConfigurationSettings(runner),
         null,
         configuration);
  }

  /**
   * @deprecated, use {@link com.intellij.execution.runners.ExecutionEnvironmentBuilder} instead
   */
  @Deprecated
  public ExecutionEnvironment(@NotNull final RunProfile runProfile,
                              @Nullable final RunnerSettings runnerSettings,
                              @Nullable final ConfigurationPerRunnerSettings configurationSettings,
                              @NotNull final DataContext dataContext) {
    this(runProfile, DefaultExecutionTarget.INSTANCE, PlatformDataKeys.PROJECT.getData(dataContext), runnerSettings, configurationSettings, null, null);
  }

  /**
   * @deprecated, use {@link com.intellij.execution.runners.ExecutionEnvironmentBuilder} instead
   */
  public ExecutionEnvironment(@NotNull RunProfile runProfile,
                              @Nullable Project project,
                              @Nullable RunnerSettings runnerSettings,
                              @Nullable ConfigurationPerRunnerSettings configurationSettings,
                              @Nullable RunContentDescriptor contentToReuse) {
    this(runProfile, DefaultExecutionTarget.INSTANCE, project, runnerSettings, configurationSettings, contentToReuse, null);
  }

  /**
   * @deprecated, use {@link com.intellij.execution.runners.ExecutionEnvironmentBuilder} instead
   */
  public ExecutionEnvironment(@NotNull RunProfile runProfile,
                              @NotNull ExecutionTarget target,
                              @Nullable Project project,
                              @Nullable RunnerSettings runnerSettings,
                              @Nullable ConfigurationPerRunnerSettings configurationSettings,
                              @Nullable RunContentDescriptor contentToReuse) {
    this(runProfile, target, project, runnerSettings, configurationSettings, contentToReuse, null);
  }

  /**
   * @deprecated, use {@link com.intellij.execution.runners.ExecutionEnvironmentBuilder} instead
   */
  public ExecutionEnvironment(@NotNull RunProfile runProfile,
                              @Nullable Project project,
                              @Nullable RunnerSettings runnerSettings,
                              @Nullable ConfigurationPerRunnerSettings configurationSettings,
                              @Nullable RunContentDescriptor contentToReuse,
                              @Nullable RunnerAndConfigurationSettings settings) {
    this(runProfile, DefaultExecutionTarget.INSTANCE, project, runnerSettings, configurationSettings, contentToReuse, settings);
  }

  public ExecutionEnvironment(@NotNull RunProfile runProfile,
                              @NotNull ExecutionTarget target,
                              @Nullable Project project,
                              @Nullable RunnerSettings runnerSettings,
                              @Nullable ConfigurationPerRunnerSettings configurationSettings,
                              @Nullable RunContentDescriptor contentToReuse,
                              @Nullable RunnerAndConfigurationSettings settings) {
    myTarget = target;
    myRunProfile = runProfile;
    myRunnerSettings = runnerSettings;
    myConfigurationSettings = configurationSettings;
    myProject = project;
    myContentToReuse = contentToReuse;
    myRunnerAndConfigurationSettings = settings;
    if (myContentToReuse != null) {
      Disposer.register(myContentToReuse, new Disposable() {
        @Override
        public void dispose() {
          myContentToReuse = null;
        }
      });
    }
  }

  /**
   * @deprecated, use {@link com.intellij.execution.runners.ExecutionEnvironmentBuilder} instead
   */
  @Deprecated
  public ExecutionEnvironment(@NotNull final ProgramRunner runner,
                              @NotNull final RunnerAndConfigurationSettings configuration,
                              @NotNull final DataContext context) {
    this(configuration.getConfiguration(),
         DefaultExecutionTarget.INSTANCE,
         PlatformDataKeys.PROJECT.getData(context),
         configuration.getRunnerSettings(runner),
         configuration.getConfigurationSettings(runner),
         null,
         configuration);
  }

  /**
   * @deprecated, use {@link com.intellij.execution.runners.ExecutionEnvironmentBuilder} instead
   */
  public ExecutionEnvironment(@NotNull final RunProfile profile,
                              @NotNull final DataContext dataContext) {
    this(profile, PlatformDataKeys.PROJECT.getData(dataContext), null, null, null);
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }


  @NotNull
  public ExecutionTarget getExecutionTarget() {
    return myTarget;
  }

  @NotNull
  public RunProfile getRunProfile() {
    return myRunProfile;
  }

  @Nullable
  public RunnerAndConfigurationSettings getRunnerAndConfigurationSettings() {
    return myRunnerAndConfigurationSettings;
  }

  /**
   * @deprecated use {@link #getProject()} and {@link #getContentToReuse()}
   */
  @Deprecated
  public DataContext getDataContext() {
    return new DataContext() {
      public Object getData(@NonNls String dataId) {
        return PlatformDataKeys.PROJECT.is(dataId) ? myProject : null;
      }
    };
  }

  @Nullable
  public RunContentDescriptor getContentToReuse() {
    return myContentToReuse;
  }

  @Nullable
  public String getRunnerId() {
    return myConfigurationSettings == null ? null : myConfigurationSettings.getRunnerId();
  }

  @Nullable
  public RunnerSettings getRunnerSettings() {
    return myRunnerSettings;
  }

  @Nullable
  public ConfigurationPerRunnerSettings getConfigurationSettings() {
    return myConfigurationSettings;
  }

  @Nullable
  public RunProfileState getState(final Executor executor) throws ExecutionException {
    return myRunProfile.getState(executor, this);
  }

  public long assignNewExecutionId() {
    myExecutionId = myIdHolder.incrementAndGet();
    return myExecutionId;
  }

  public void setExecutionId(long executionId) {
    myExecutionId = executionId;
  }

  public long getExecutionId() {
    return myExecutionId;
  }

  @Override
  public String toString() {
    if (myRunnerAndConfigurationSettings != null) {
      return myRunnerAndConfigurationSettings.getName();
    } else if (myRunProfile != null) {
        return myRunProfile.getName();
    } else if (myContentToReuse != null) {
      return myContentToReuse.getDisplayName();
    }
    return super.toString();
  }
}
