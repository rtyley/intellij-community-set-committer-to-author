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
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 6/7/13 11:18 AM
 */
public class ExternalSystemTaskDebugRunner extends GenericDebuggerRunner {

  private static final Logger LOG = Logger.getInstance("#" + ExternalSystemTaskDebugRunner.class.getName());

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return profile instanceof ExternalSystemRunConfiguration && ToolWindowId.DEBUG.equals(executorId);
  }

  @Nullable
  @Override
  protected RunContentDescriptor createContentDescriptor(Project project,
                                                         Executor executor,
                                                         RunProfileState state,
                                                         RunContentDescriptor contentToReuse,
                                                         ExecutionEnvironment env) throws ExecutionException
  {
    if (state instanceof ExternalSystemRunConfiguration.MyRunnableState) {
      int port = ((ExternalSystemRunConfiguration.MyRunnableState)state).getDebugPort();
      if (port > 0) {
        RemoteConnection connection = new RemoteConnection(true, "127.0.0.1", String.valueOf(port), false);
        return attachVirtualMachine(project, executor, state, contentToReuse, env, connection, true);
      }
      else {
        LOG.warn("Can't attach debugger to external system task execution. Reason: target debug port is unknown");
      }
    }
    else {
      LOG.warn(String.format(
        "Can't attach debugger to external system task execution. Reason: invalid run profile state is provided"
        + "- expected '%s' but got '%s'",
        ExternalSystemRunConfiguration.MyRunnableState.class.getName(), state.getClass().getName()
      ));
    }
    return null;
  }
}
