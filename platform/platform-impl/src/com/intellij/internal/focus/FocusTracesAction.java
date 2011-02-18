/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.internal.focus;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.impl.FocusManagerImpl;
import com.intellij.openapi.wm.impl.FocusRequestInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class FocusTracesAction extends AnAction implements DumbAware {
  private static boolean myActive = false;

  public static boolean isActive() {
    return myActive;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final IdeFocusManager manager = IdeFocusManager.getGlobalInstance();
    if (! (manager instanceof FocusManagerImpl)) return;
    final FocusManagerImpl focusManager = (FocusManagerImpl)manager;

    myActive = !myActive;

    if (!myActive) {
      final List<FocusRequestInfo> requests = focusManager.getRequests();
      new FocusTracesDialog(project, new ArrayList<FocusRequestInfo>(requests)).show();
      requests.clear();
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    if (myActive) {
      presentation.setText("Stop Focus Tracing");
    } else {
      presentation.setText("Start Focus Tracing");
    }
    presentation.setEnabled(e.getData(PlatformDataKeys.PROJECT) != null);
  }
}
