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
package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.execution.AntBuildMessageView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.util.IconLoader;

public final class PauseOutputAction extends ToggleAction {
  private final AntBuildMessageView myAntBuildMessageView;

  public PauseOutputAction(AntBuildMessageView antBuildMessageView) {
    super(AntBundle.message("ant.view.pause.output.action.name"),null, IconLoader.getIcon("/actions/pause.png"));
    myAntBuildMessageView = antBuildMessageView;
  }

  public boolean isSelected(AnActionEvent event) {
    return myAntBuildMessageView.isOutputPaused();
  }

  public void setSelected(AnActionEvent event,boolean flag) {
    myAntBuildMessageView.setOutputPaused(flag);
  }

  public void update(AnActionEvent event){
    super.update(event);
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(!myAntBuildMessageView.isStopped() || isSelected(event));
  }
}

