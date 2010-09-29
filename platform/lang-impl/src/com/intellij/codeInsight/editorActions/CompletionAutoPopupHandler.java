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

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * @author peter
 */
public class CompletionAutoPopupHandler extends TypedHandlerDelegate {
  private Editor myEditor;
  private int myLastOffset;

  @Override
  public Result checkAutoPopup(char charTyped, final Project project, final Editor editor, final PsiFile file) {
    if (!CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP) return Result.CONTINUE;

    if (!Character.isLetter(charTyped)) {
      myEditor = null;
      return Result.CONTINUE;
    }

    if (myEditor != null) {
      if (editor != myEditor || editor.getCaretModel().getOffset() != myLastOffset + 1) {
        myEditor = null;
        return Result.CONTINUE;
      }
    }

    myEditor = editor;
    myLastOffset = editor.getCaretModel().getOffset();

    editor.addEditorMouseListener(new EditorMouseAdapter() {
      @Override
      public void mouseClicked(EditorMouseEvent e) {
        editor.removeEditorMouseListener(this);
        myEditor = null;
      }
    });

    AutoPopupController.getInstance(project).invokeAutoPopupRunnable(new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed() || !file.isValid()) return;
        if (editor.isDisposed() || FileEditorManager.getInstance(project).getSelectedTextEditor() != editor) return;

        new CodeCompletionHandlerBase(CompletionType.BASIC, false, false).invoke(project, editor);
      }
    }, CodeInsightSettings.getInstance().AUTO_LOOKUP_DELAY);
    return Result.STOP;
  }
}
