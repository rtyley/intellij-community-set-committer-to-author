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

package com.intellij.execution.actions;

import com.intellij.execution.*;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RunContextAction extends BaseRunConfigurationAction {
  private final Executor myExecutor;

  public RunContextAction(@NotNull final Executor executor) {
    super(ExecutionBundle.message("perform.action.with.context.configuration.action.name", executor.getStartActionText()), null,
          executor.getIcon());
    myExecutor = executor;
  }

  protected void perform(final ConfigurationContext context) {
    RunnerAndConfigurationSettingsImpl configuration = context.findExisting();
    final RunManagerEx runManager = context.getRunManager();
    if (configuration == null) {
      configuration = context.getConfiguration();
      if (configuration == null) return;
      runManager.setTemporaryConfiguration(configuration);
    }
    runManager.setActiveConfiguration(configuration);

    final ProgramRunner runner = getRunner(configuration.getConfiguration());
    if (runner != null) {
      try {
        runner.execute(myExecutor, new ExecutionEnvironment(runner, configuration, context.getDataContext()));
      }
      catch (ExecutionException e) {
        Messages.showErrorDialog(context.getProject(), e.getMessage(), ExecutionBundle.message("error.common.title"));
      }
    }
  }

  @Override
  protected boolean isEnabledFor(RunConfiguration configuration) {
    return getRunner(configuration) != null;
  }

  @Nullable
  private ProgramRunner getRunner(final RunConfiguration configuration) {
    return RunnerRegistry.getInstance().getRunner(myExecutor.getId(), configuration);
  }

  protected void updatePresentation(final Presentation presentation, final String actionText, final ConfigurationContext context) {
    presentation.setText(myExecutor.getStartActionText() + actionText, true);

    RunnerAndConfigurationSettingsImpl configuration = context.findExisting();
    if (configuration == null) {
      configuration = context.getConfiguration();
    }

    final boolean b = configuration != null && getRunner(configuration.getConfiguration()) != null;
    presentation.setEnabled(b);
    presentation.setVisible(b);
  }
}
