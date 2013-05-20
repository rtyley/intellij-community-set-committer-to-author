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
package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.execution.*;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;

/**
 * @author Sergey Evdokimov
 */
public class MavenExecuteGoalAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getRequiredData(PlatformDataKeys.PROJECT);

    ExecuteMavenGoalHistoryService historyService = ExecuteMavenGoalHistoryService.getInstance(project);

    MavenExecuteGoalDialog dialog = new MavenExecuteGoalDialog(project, historyService.getHistory());
    dialog.setWorkDirectory(historyService.getWorkDirectory());

    dialog.show();
    if (!dialog.isOK()) {
      return;
    }

    String goals = dialog.getGoals();
    goals = goals.trim();
    if (goals.startsWith("mvn ")) {
      goals = goals.substring("mvn ".length()).trim();
    }

    String workDirectory = dialog.getWorkDirectory();

    historyService.addCommand(goals, workDirectory);

    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);

    File mavenHome = MavenUtil.resolveMavenHomeDirectory(projectsManager.getGeneralSettings().getMavenHome());
    if (mavenHome == null) {
      // todo handle
      throw new RuntimeException();
    }

    MavenRunnerParameters parameters = new MavenRunnerParameters(true, workDirectory, Arrays.asList(ParametersList.parse(goals)), null);

    MavenGeneralSettings generalSettings = new MavenGeneralSettings();
    generalSettings.setMavenHome(mavenHome.getPath());

    MavenRunnerSettings runnerSettings = MavenRunner.getInstance(project).getSettings().clone();
    runnerSettings.setMavenProperties(new LinkedHashMap<String, String>());
    runnerSettings.setSkipTests(false);

    MavenRunConfigurationType.runConfiguration(project, parameters, generalSettings, runnerSettings, null);
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);

    boolean hasMaven = false;

    if (project != null) {
      hasMaven = MavenProjectsManager.getInstance(project).hasProjects();
    }

    e.getPresentation().setVisible(hasMaven);
  }
}
