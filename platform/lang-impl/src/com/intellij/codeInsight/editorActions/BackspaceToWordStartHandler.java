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
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

/**
 * @author yole
 */
public class BackspaceToWordStartHandler extends BackspaceHandler {
  public BackspaceToWordStartHandler(EditorActionHandler originalHandler) {
    super(originalHandler);
  }

  @Override
  public void executeWriteAction(Editor editor, DataContext dataContext) {
    if (!handleBackspace(editor, dataContext, true)) {
      myOriginalHandler.execute(editor, dataContext);
    }
  }
}
