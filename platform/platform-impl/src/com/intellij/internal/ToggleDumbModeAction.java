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
package com.intellij.internal;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;

/**
 * @author peter
 */
public class ToggleDumbModeAction extends AnAction implements DumbAware {
  private volatile boolean myDumb = false;

  public void actionPerformed(final AnActionEvent e) {
    if (myDumb) {
      myDumb = false;
    } else {
      myDumb = true;
      final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
      if (project == null) return;

      DumbServiceImpl.getInstance(project).queueIndexUpdate(new Consumer<ProgressIndicator>() {
        public void consume(ProgressIndicator progressIndicator) {
          while (myDumb) {
            try {
              Thread.sleep(100);
            }
            catch (InterruptedException e1) {
            }
          }
        }
      });
    }
  }

  @Override
  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    presentation.setEnabled(project != null && myDumb == DumbServiceImpl.getInstance(project).isDumb());
    if (myDumb) {
      presentation.setText("Exit dumb mode");
    } else {
      presentation.setText("Enter dumb mode");
    }
  }

}