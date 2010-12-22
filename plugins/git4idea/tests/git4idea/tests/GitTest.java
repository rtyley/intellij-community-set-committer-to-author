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
package git4idea.tests;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.testFramework.AbstractVcsTestCase;
import com.intellij.ui.GuiUtils;
import git4idea.GitVcs;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * The common ancestor for git test cases which need git executable.
 * These tests can be executed only if git is installed in the system and IDEA_TEST_GIT_EXECUTABLE_PATH targets to the folder which
 * contains git executable.
 * @author Kirill Likhodedov
 */
public abstract class GitTest extends AbstractVcsTestCase {

  public static final String GIT_EXECUTABLE_PATH = "IDEA_TEST_GIT_EXECUTABLE_PATH";

  private static final String GIT_EXECUTABLE = (SystemInfo.isWindows ? "git.exe" : "git");
  protected GitTestRepository myMainRepo;
  private File myProjectDir;

  @BeforeMethod
  protected void setUp() throws Exception {
    // setting git executable
    String exec = System.getenv(GIT_EXECUTABLE_PATH);
    if (exec != null) {
      myClientBinaryPath = new File(exec);
    }
    if (exec == null || !myClientBinaryPath.exists()) {
      final File pluginRoot = new File(PluginPathManager.getPluginHomePath("git4idea"));
      myClientBinaryPath = new File(pluginRoot, "tests/git4idea/tests/data/bin");
    }

    myMainRepo = initRepositories();

    myProjectDir = new File(myMainRepo.getDirFixture().getTempDirPath());
    if (EventQueue.isDispatchThread()) {
      initProject(myProjectDir);
    } else {
      SwingUtilities.invokeAndWait(new Runnable() {
        @Override
        public void run() {
          try {
            initProject(myProjectDir);
            activateVCS(GitVcs.NAME);
          }
          catch (Exception e) {
            e.printStackTrace();
          }
        }
      });
    }

    myTraceClient = true;
    doActionSilently(VcsConfiguration.StandardConfirmation.ADD);
    doActionSilently(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  /**
   * Different implementations for {@link GitSingleUserTest} and {@link GitCollaborativeTest}:
   * create a single or several repositories, which will be used in tests.
   * @return main repository which IDEA project will be bound to.
   */
  protected abstract GitTestRepository initRepositories() throws Exception;

  protected abstract void tearDownRepositories() throws Exception;

  @AfterMethod
  protected void tearDown() throws Exception {
    GuiUtils.runOrInvokeAndWait(new Runnable() {
      @Override
      public void run() {
        try {
          tearDownProject();
          tearDownRepositories();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
  }

  /**
   * Executes the given native Git command with parameters in the given working directory.
   * @param workingDir  working directory where the command will be executed. May be null.
   * @param commandLine command and parameters (e.g. 'status, -m').
   */
  protected ProcessOutput executeCommand(@Nullable File workingDir, String... commandLine) throws IOException {
    return runClient(GIT_EXECUTABLE, null, workingDir, commandLine);
  }

  protected void doActionSilently(final VcsConfiguration.StandardConfirmation op) {
    setStandardConfirmation(GitVcs.NAME, op, VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY);
  }

  protected void doNothingSilently(final VcsConfiguration.StandardConfirmation op) {
    setStandardConfirmation(GitVcs.NAME, op, VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY);
  }

  protected void showConfirmation(final VcsConfiguration.StandardConfirmation op) {
    setStandardConfirmation(GitVcs.NAME, op, VcsShowConfirmationOption.Value.SHOW_CONFIRMATION);
  }

}
