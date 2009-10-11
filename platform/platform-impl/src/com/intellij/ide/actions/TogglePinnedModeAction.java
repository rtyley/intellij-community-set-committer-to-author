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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;

public class TogglePinnedModeAction extends ToggleAction implements DumbAware {
  public boolean isSelected(AnActionEvent event){
    Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    if (project == null || project.isDisposed()) {
      return false;
    }
    ToolWindowManager mgr=ToolWindowManager.getInstance(project);
    String id=mgr.getActiveToolWindowId();
    if(id==null){
      return false;
    }
    return !mgr.getToolWindow(id).isAutoHide();
  }

  public void setSelected(AnActionEvent event,boolean flag) {
    Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      return;
    }
    ToolWindowManager mgr=ToolWindowManager.getInstance(project);
    String id=mgr.getActiveToolWindowId();
    if(id==null){
      return;
    }
    mgr.getToolWindow(id).setAutoHide(!flag);
  }

  public void update(AnActionEvent event){
    super.update(event);
    Presentation presentation = event.getPresentation();
    Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    ToolWindowManager mgr=ToolWindowManager.getInstance(project);
    String id=mgr.getActiveToolWindowId();
    if(id==null){
      presentation.setEnabled(false);
      return;
    }
    ToolWindow toolWindow=mgr.getToolWindow(id);
    presentation.setEnabled(toolWindow.isAvailable()&&ToolWindowType.SLIDING!=toolWindow.getType());
  }
}
