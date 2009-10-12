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

package com.intellij.codeInsight.template.impl.editorActions;

import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;

public class TabHandler extends EditorWriteActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public TabHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public void executeWriteAction(Editor editor, DataContext dataContext) {
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    if (project == null) {
      myOriginalHandler.execute(editor, dataContext);
      return;
    }

    TemplateManagerImpl templateManager = (TemplateManagerImpl) TemplateManagerImpl.getInstance(project);

    if (!templateManager.startTemplate(editor, TemplateSettings.TAB_CHAR)) {
      myOriginalHandler.execute(editor, dataContext);
    }
  }

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    return myOriginalHandler.isEnabled(editor, dataContext);
  }
}
