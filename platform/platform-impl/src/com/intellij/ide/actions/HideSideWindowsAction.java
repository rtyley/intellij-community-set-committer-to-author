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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;

public class HideSideWindowsAction extends AnAction implements DumbAware {

  public void actionPerformed(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      return;
    }

    ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(project);
    String id = toolWindowManager.getActiveToolWindowId();
    if (id == null) {
      id = toolWindowManager.getLastActiveToolWindowId();
    }
    toolWindowManager.hideToolWindow(id, true);
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(project);
    String id = toolWindowManager.getActiveToolWindowId();
    if (id != null) {
      presentation.setEnabled(true);
      return;
    }

    id = toolWindowManager.getLastActiveToolWindowId();
    if (id == null) {
      presentation.setEnabled(false);
      return;
    }

    ToolWindowEx toolWindow = (ToolWindowEx)toolWindowManager.getToolWindow(id);
    presentation.setEnabled(toolWindow.isVisible());
  }
}
