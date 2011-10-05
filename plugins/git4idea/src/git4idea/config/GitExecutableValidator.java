/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.config;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecutableValidator;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.CharsetToolkit;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

/**
 * Project service that is used to check whether currently set git executable is valid (just calls 'git version' and parses the output),
 * and to display notification to the user proposing to fix the project set up.
 * @author Kirill Likhodedov
 */
public class GitExecutableValidator extends ExecutableValidator {

  private GitVcs myVcs;

  public GitExecutableValidator(@NotNull Project project, @NotNull GitVcs vcs) {
    super(project, GitVcs.NOTIFICATION_GROUP_ID.getDisplayId(),
          GitBundle.message("git.executable.notification.title"), GitBundle.message("git.executable.notification.description"));
    myVcs = vcs;
  }

  @Override
  protected String getCurrentExecutable() {
    return myVcs.getAppSettings().getPathToGit();
  }

  @NotNull
  @Override
  protected Configurable getConfigurable() {
    return myVcs.getConfigurable();
  }

  @Override
  public boolean isExecutableValid(@NotNull String executable) {
    try {
      GeneralCommandLine commandLine = new GeneralCommandLine();
      commandLine.setExePath(executable);
      commandLine.addParameter("--version");
      CapturingProcessHandler handler = new CapturingProcessHandler(commandLine.createProcess(), CharsetToolkit.getDefaultSystemCharset());
      ProcessOutput result = handler.runProcess(30 * 1000);
      return !result.isTimeout() && (result.getExitCode() == 0) && result.getStderr().isEmpty();
    } catch (Throwable e) {
      return false;
    }
  }

  /**
   * Checks if git executable is valid. If not (which is a common case for low-level vcs exceptions), shows the
   * notification. Otherwise throws the exception.
   * This is to be used in catch-clauses
   * @param e exception which was thrown.
   * @throws VcsException if git executable is valid.
   */
   public void showNotificationOrThrow(VcsException e) throws VcsException {
    if (checkExecutableAndNotifyIfNeeded()) {
      throw e;
    }
  }
}
