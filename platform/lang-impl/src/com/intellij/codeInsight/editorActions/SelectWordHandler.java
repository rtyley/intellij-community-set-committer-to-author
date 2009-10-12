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

package com.intellij.codeInsight.editorActions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.ASTNode;
import com.intellij.lang.CompositeLanguage;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SelectWordHandler extends EditorActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.SelectWordHandler");

  private final EditorActionHandler myOriginalHandler;

  public SelectWordHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public void execute(Editor editor, DataContext dataContext) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: execute(editor='" + editor + "')");
    }
    if (editor instanceof EditorWindow && editor.getSelectionModel().hasSelection()
        && InjectedLanguageUtil.isSelectionIsAboutToOverflowInjectedFragment((EditorWindow)editor)) {
      // selection about to spread beyond injected fragment
      editor = ((EditorWindow)editor).getDelegate();
    }
    Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
    if (project == null) {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, dataContext);
      }
      return;
    }

    Document document = editor.getDocument();
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);

    if (file == null) {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, dataContext);
      }
      return;
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    doAction(editor, file);
  }

  private static void doAction(Editor editor, PsiFile file) {
    CharSequence text = editor.getDocument().getCharsSequence();

    if (file instanceof PsiCompiledElement) {
      file = (PsiFile)((PsiCompiledElement)file).getMirror();
    }

    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.select.word");

    int caretOffset = editor.getCaretModel().getOffset();

    if (caretOffset > 0 && caretOffset < editor.getDocument().getTextLength() &&
        !Character.isJavaIdentifierPart(text.charAt(caretOffset)) && Character.isJavaIdentifierPart(text.charAt(caretOffset - 1))) {
      caretOffset--;
    }

    PsiElement element = findElementAt(file, caretOffset);

    if (element instanceof PsiWhiteSpace && caretOffset > 0) {
      PsiElement anotherElement = findElementAt(file, caretOffset - 1);

      if (!(anotherElement instanceof PsiWhiteSpace)) {
        element = anotherElement;
      }
    }

    while (element instanceof PsiWhiteSpace || element != null && StringUtil.isEmpty(element.getText().trim())) {
      while (element.getNextSibling() == null) {
        if (element instanceof PsiFile) return;
        final PsiElement parent = element.getParent();
        final PsiElement[] children = parent.getChildren();

        if (children.length > 0 && children[children.length - 1] == element) {
          element = parent;
        }
        else {
          element = parent;
          break;
        }
      }

      element = element.getNextSibling();
      if (element == null) return;
      caretOffset = element.getTextRange().getStartOffset();
    }

    TextRange selectionRange = new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());

    TextRange newRange = null;

    int textLength = editor.getDocument().getTextLength();

    while (element != null && !(element instanceof PsiFile)) {
      newRange = advance(selectionRange, element, text, caretOffset, editor);

      if (newRange != null && !newRange.isEmpty()) {
        break;
      }

      element = getUpperElement(element);
    }

    if (newRange == null) {
      newRange = new TextRange(0, textLength);
    }

    if (!editor.getSelectionModel().hasSelection() || newRange.contains(selectionRange)) {
      selectionRange = newRange;
    }

    editor.getSelectionModel().setSelection(selectionRange.getStartOffset(), selectionRange.getEndOffset());
  }

  @Nullable
  private static PsiElement findElementAt(final PsiFile file, final int caretOffset) {
    PsiElement elementAt = file.findElementAt(caretOffset);
    if (elementAt != null && isLanguageExtension(file, elementAt)) {
      return file.getViewProvider().findElementAt(caretOffset, file.getLanguage());
    }
    return elementAt;
  }

  private static boolean isLanguageExtension(@NotNull final PsiFile file, @NotNull final PsiElement elementAt) {
    final Language elementLanguage = elementAt.getLanguage();
    if (file.getLanguage() instanceof CompositeLanguage) {
      CompositeLanguage compositeLanguage = (CompositeLanguage) file.getLanguage();
      final Language[] extensions = compositeLanguage.getLanguageExtensionsForFile(file);
      for(Language extension: extensions) {
        if (extension == elementLanguage) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  private static PsiElement getUpperElement(final PsiElement e) {
    final PsiElement parent = e.getParent();

    if (!(e instanceof PsiErrorElement)) {
      final PsiFile file = e.getContainingFile();
      final FileViewProvider viewProvider = file.getViewProvider();
      if (viewProvider.getBaseLanguage() != e.getLanguage()) {
        final PsiFile baseRoot = viewProvider.getPsi(viewProvider.getBaseLanguage());
        if (baseRoot != file && parent.getTextLength() == baseRoot.getTextLength()) {
          final ASTNode node = baseRoot.getNode();
          if (node == null) return parent;
          final ASTNode leafElementAt = node.findLeafElementAt(e.getTextRange().getStartOffset());
          return leafElementAt != null ? leafElementAt.getPsi() : parent;
        }
      }
    }

    return parent;
  }

  @Nullable
  private static TextRange advance(TextRange selectionRange, PsiElement element, CharSequence text, int cursorOffset, Editor editor) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: advance(selectionRange='" + selectionRange + "', element='" + element + "')");
    }
    TextRange minimumRange = null;

    for (ExtendWordSelectionHandler selectioner : SelectWordUtil.getExtendWordSelectionHandlers()) {
      if (!selectioner.canSelect(element)) {
        continue;
      }
      List<TextRange> ranges = selectioner.select(element, text, cursorOffset, editor);

      if (ranges == null) {
        continue;
      }

      for (TextRange range : ranges) {
        if (range == null) {
          continue;
        }

        if (range.contains(selectionRange) && !range.equals(selectionRange)) {
          if (minimumRange == null || minimumRange.contains(range)) {
            minimumRange = range;
          }
        }
      }
    }

    //TODO remove assert
    if (minimumRange != null) {
      LOG.assertTrue(minimumRange.getEndOffset() <= text.length());
    }

    return minimumRange;
  }
}