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
package org.jetbrains.idea.maven.tasks.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.keymap.impl.ui.EditKeymapsDialog;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.util.List;

public class AssignShortcutAction extends MavenAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    return super.isAvailable(e) && !isIgnoredProject(e) && getGoalActionId(e) != null;
  }

  private boolean isIgnoredProject(AnActionEvent e) {
    MavenProject project = MavenActionUtil.getMavenProject(e);
    if (project == null) return false;
    return MavenActionUtil.getProjectsManager(e).isIgnored(project);
  }

  public void actionPerformed(AnActionEvent e) {
    String actionId = getGoalActionId(e);
    if (actionId != null) {
      new EditKeymapsDialog(MavenActionUtil.getProject(e), actionId).show();
    }
  }

  @Nullable
  private String getGoalActionId(AnActionEvent e) {
    MavenProject project = MavenActionUtil.getMavenProject(e);
    if (project == null) return null;

    List<String> goals = e.getData(MavenDataKeys.MAVEN_GOALS);
    String goal = (goals == null || goals.size() != 1) ? null : goals.get(0);

    return getShortcutsManager(e).getActionId(project.getPath(), goal);
  }

  protected MavenShortcutsManager getShortcutsManager(AnActionEvent e) {
    return MavenShortcutsManager.getInstance(MavenActionUtil.getProject(e));
  }
}

