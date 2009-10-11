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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 14, 2002
 * Time: 4:06:30 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;

public class CodeBlockUtil {
  private CodeBlockUtil() {
  }

  private static Language getBraceType(HighlighterIterator iterator) {
    final IElementType type = iterator.getTokenType();
    return type.getLanguage();
  }

  public static void moveCaretToCodeBlockEnd(Project project, Editor editor, boolean isWithSelection) {
    Document document = editor.getDocument();
    int selectionStart = editor.getSelectionModel().getLeadSelectionOffset();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) return;

    int offset = editor.getCaretModel().getOffset();
    final FileType fileType = file.getFileType();
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);

    int depth = 0;
    Language braceType;
    boolean isBeforeLBrace = false;
    if (isLStructuralBrace(fileType, iterator, document.getCharsSequence())) {
      isBeforeLBrace = true;
      depth = -1;
      braceType = getBraceType(iterator);
    } else {
      braceType = null;
    }

    boolean moved = false;
    while (true) {
      if (iterator.atEnd()) return;

      if (isRStructuralBrace(fileType, iterator,document.getCharsSequence()) &&
          ( braceType == getBraceType(iterator) ||
            braceType == null
          )
          ) {
        if (moved) {
          if (depth == 0) break;
          depth--;
        }

        if (braceType == null) {
          braceType = getBraceType(iterator);
        }
      }
      else if (isLStructuralBrace(fileType, iterator,document.getCharsSequence()) &&
               ( braceType == getBraceType(iterator) ||
                 braceType == null
               )
              ) {
        if (braceType == null) {
          braceType = getBraceType(iterator);
        }
        depth++;
      }

      moved = true;
      iterator.advance();
    }

    int end = isBeforeLBrace? iterator.getEnd() : iterator.getStart();
    editor.getCaretModel().moveToOffset(end);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

    if (isWithSelection) {
      editor.getSelectionModel().setSelection(selectionStart, editor.getCaretModel().getOffset());
    } else {
      editor.getSelectionModel().removeSelection();
    }
  }

  public static void moveCaretToCodeBlockStart(Project project, Editor editor, boolean isWithSelection) {
    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    int selectionStart = editor.getSelectionModel().getLeadSelectionOffset();
    if (file == null) return;

    int offset = editor.getCaretModel().getOffset() - 1;
    if (offset < 0) return;
    final FileType fileType = file.getFileType();
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);

    int depth = 0;
    Language braceType;
    boolean isAfterRBrace = false;
    if (isRStructuralBrace(fileType, iterator,document.getCharsSequence())) {
      isAfterRBrace = true;
      depth = -1;
      braceType = getBraceType(iterator);
    } else {
      braceType = null;
    }

    boolean moved = false;
    while (true) {
      if (iterator.atEnd()) return;

      if (isLStructuralBrace(fileType, iterator, document.getCharsSequence()) &&
          ( braceType == getBraceType(iterator) ||
            braceType == null
          )
          ) {
        if (braceType == null) {
          braceType = getBraceType(iterator);
        }

        if (moved) {
          if (depth == 0) break;
          depth--;
        }
      }
      else if (isRStructuralBrace(fileType, iterator,document.getCharsSequence()) &&
               ( braceType == getBraceType(iterator) ||
                 braceType == null
               )
              ) {
        if (braceType == null) {
          braceType = getBraceType(iterator);
        }
        depth++;
      }

      moved = true;
      iterator.retreat();
    }

    int start = isAfterRBrace ? iterator.getStart() : iterator.getEnd();
    editor.getCaretModel().moveToOffset(start);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

    if (isWithSelection) {
      editor.getSelectionModel().setSelection(selectionStart, editor.getCaretModel().getOffset());
    } else {
      editor.getSelectionModel().removeSelection();
    }
  }

  private static boolean isLStructuralBrace(final FileType fileType, HighlighterIterator iterator, CharSequence fileText) {
    return BraceMatchingUtil.isLBraceToken(iterator, fileText, fileType) && BraceMatchingUtil.isStructuralBraceToken(fileType, iterator,fileText);
  }

  private static boolean isRStructuralBrace(final FileType fileType, HighlighterIterator iterator, CharSequence fileText) {
    return BraceMatchingUtil.isRBraceToken(iterator, fileText, fileType) && BraceMatchingUtil.isStructuralBraceToken(fileType, iterator,fileText);
  }
}
