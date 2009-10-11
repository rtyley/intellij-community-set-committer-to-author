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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;

/**
 * @author yole
 */
public abstract class SplitterActionBase extends AnAction implements DumbAware {
  public void update(final AnActionEvent event) {
    final Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    final Presentation presentation = event.getPresentation();
    boolean enabled;
    if (project == null) {
      enabled = false;
    }
    else {
      final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
      enabled = fileEditorManager.isInSplitter();
    }
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      presentation.setVisible(enabled);
    }
    else {
      presentation.setEnabled(enabled);
    }
  }
}
