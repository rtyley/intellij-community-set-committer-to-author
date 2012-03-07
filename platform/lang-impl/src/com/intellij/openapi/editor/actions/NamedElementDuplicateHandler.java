/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class NamedElementDuplicateHandler extends EditorWriteActionHandler {
  private final EditorActionHandler myOriginal;

  public NamedElementDuplicateHandler(EditorActionHandler original) {
    myOriginal = original;
  }

  @Override
  public void executeWriteAction(Editor editor, DataContext dataContext) {
    Project project = editor.getProject();
    if (project != null && !editor.getSelectionModel().hasSelection()) {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file != null) {
        VisualPosition caret = editor.getCaretModel().getVisualPosition();
        Pair<LogicalPosition, LogicalPosition> lines = EditorUtil.calcCaretLinesRange(editor, caret, caret);
        TextRange toDuplicate = new TextRange(editor.logicalPositionToOffset(lines.first), editor.logicalPositionToOffset(lines.second));

        PsiNamedElement named = findNamedElement(editor, file, toDuplicate);
        if (named != null) {
          editor.getCaretModel().moveToOffset(named.getTextOffset());
        }
      }
    }

    myOriginal.execute(editor, dataContext);
  }

  @Nullable
  private static PsiNamedElement findNamedElement(Editor editor, PsiFile file, TextRange toDuplicate) {
    int nonWs = CharArrayUtil.shiftForward(editor.getDocument().getCharsSequence(), toDuplicate.getStartOffset(), "\n\t ");
    PsiElement psi = file.findElementAt(nonWs);
    PsiNamedElement named = null;
    while (psi != null) {
      TextRange range = psi.getTextRange();
      if (range == null || psi instanceof PsiFile || !toDuplicate.contains(psi.getTextRange())) {
        break;
      }
      if (psi instanceof PsiNamedElement) {
        named = (PsiNamedElement)psi;
      }
      psi = psi.getParent();
    }
    return named;
  }
}
