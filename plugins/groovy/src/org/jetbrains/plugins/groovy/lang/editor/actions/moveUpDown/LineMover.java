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

package org.jetbrains.plugins.groovy.lang.editor.actions.moveUpDown;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author ilyas
 */
class LineMover extends Mover {
  public LineMover(final boolean isDown) {
    super(isDown);
  }

  protected boolean checkAvailable(Editor editor, PsiFile file) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    final int startLine;
    final int endLine;
    LineRange range;
    if (selectionModel.hasSelection()) {
      startLine = editor.offsetToLogicalPosition(selectionModel.getSelectionStart()).line;
      final LogicalPosition endPos = editor.offsetToLogicalPosition(selectionModel.getSelectionEnd());
      endLine = endPos.column == 0 ? endPos.line : endPos.line + 1;
      range = new LineRange(startLine, endLine);
    } else {
      startLine = editor.getCaretModel().getLogicalPosition().line;
      endLine = startLine + 1;
      range = new LineRange(startLine, endLine);
    }

    final int maxLine = editor.offsetToLogicalPosition(editor.getDocument().getTextLength()).line;
    if (range.startLine == 0 && !isDown) return false;
    if (range.endLine > maxLine && isDown) return false;

    toMove = range;
    updateComplementaryRange();
    return true;
  }

  protected final void updateComplementaryRange() {
    int nearLine = isDown ? toMove.endLine : toMove.startLine - 1;
    toMove2 = new LineRange(nearLine, nearLine + 1);
  }

  @Nullable
  protected static Pair<PsiElement, PsiElement> getElementRange(Editor editor, PsiFile file, final LineRange range) {
    final int startOffset = editor.logicalPositionToOffset(new LogicalPosition(range.startLine, 0));
    PsiElement startingElement = firstNonWhiteElement(startOffset, file, true, true);
    if (startingElement == null) return null;
    final int endOffset = editor.logicalPositionToOffset(new LogicalPosition(range.endLine, 0)) - 1;

    PsiElement endingElement = firstNonWhiteElement(endOffset, file, false, true);
    if (endingElement == null) return null;
    if (PsiTreeUtil.isAncestor(startingElement, endingElement, false) ||
        startingElement.getTextRange().getEndOffset() <= endingElement.getTextRange().getStartOffset()) {
      return Pair.create(startingElement, endingElement);
    }
    if (PsiTreeUtil.isAncestor(endingElement, startingElement, false)) {
      return Pair.create(startingElement, endingElement);
    }
    return null;
  }

  @Nullable
  static PsiElement firstNonWhiteElement(int offset, PsiFile file, final boolean lookRight, boolean withNewlines) {
    final PsiElement leafElement = file.findElementAt(offset);
    return leafElement == null ? null : firstNonWhiteElement(leafElement, lookRight, withNewlines);
  }

  @NotNull
  static PsiElement firstNonWhiteElement(@NotNull PsiElement element, final boolean lookRight, boolean withNewlines) {
    while (element instanceof PsiWhiteSpace || StringUtil.isEmptyOrSpaces(element.getText()) || PsiUtil.isNewLine(element) && withNewlines) {
      final PsiElement candidate = lookRight ? element.getNextSibling() : element.getPrevSibling();
      if (candidate == null) {
        break;
      }

      element = candidate;
    }
    return element;
  }

  @Nullable
  protected static Pair<PsiElement, PsiElement> getElementRange(final PsiElement parent,
                                                                PsiElement element1,
                                                                PsiElement element2) {
    if (PsiTreeUtil.isAncestor(element1, element2, false) || PsiTreeUtil.isAncestor(element2, element1, false)) {
      return Pair.create(parent, parent);
    }
    // find nearset children that are parents of elements
    while (element1 != null && element1.getParent() != parent) {
      element1 = element1.getParent();
    }
    while (element2 != null && element2.getParent() != parent) {
      element2 = element2.getParent();
    }

    if (element1 == null || element2 == null) return null;
    PsiElement commonParent = element1.getParent();
    if ((!(element1 instanceof GrTopStatement || element1 instanceof PsiComment) || !(element2 instanceof GrTopStatement || element2 instanceof PsiComment)) &&
        commonParent == element2.getParent() &&
        commonParent instanceof GrTopStatement) {
      element1 = element2 = element1.getParent();
    }

    if (element1 != element2) {
      assert element1.getTextRange().getEndOffset() <= element2.getTextRange().getStartOffset() : element1.getTextRange() + "-" + element2.getTextRange() + element1 + element2;
    }

    return Pair.create(element1, element2);
  }
}
