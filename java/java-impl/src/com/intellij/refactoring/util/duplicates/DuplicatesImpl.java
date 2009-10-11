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
package com.intellij.refactoring.util.duplicates;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 */
public class DuplicatesImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.duplicates.DuplicatesImpl");

  private DuplicatesImpl() {}

  public static void invoke(final Project project, Editor editor, final MatchProvider provider) {
    final List<Match> duplicates = provider.getDuplicates();
    int idx = 0;
    for (final Match match : duplicates) {
      if (!match.getMatchStart().isValid() || !match.getMatchEnd().isValid()) continue;
      if (replaceMatch(project, provider, match, editor, ++idx, duplicates.size())) return;
    }
  }

  public static void invoke(final Project project, final MatchProvider provider) {
    final List<Match> duplicates = provider.getDuplicates();
    int idx = 0;
    for (final Match match : duplicates) {
      final PsiFile file = match.getFile();
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile == null || !virtualFile.isValid()) return;
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;
      final Editor editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, virtualFile), false);
      LOG.assertTrue(editor != null);
      if (!match.getMatchStart().isValid() || !match.getMatchEnd().isValid()) continue;
      if (replaceMatch(project, provider, match, editor, ++idx, duplicates.size())) return;
    }
  }

  private static boolean replaceMatch(final Project project, final MatchProvider provider, final Match match, final Editor editor,
                                      final int idx, final int size) {
    final ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    highlightMatch(project, editor, match, highlighters);
    final TextRange textRange = match.getTextRange();
    final LogicalPosition logicalPosition = editor.offsetToLogicalPosition(textRange.getStartOffset());
    expandAllRegionsCoveringRange(project, editor, textRange);
    editor.getScrollingModel().scrollTo(logicalPosition, ScrollType.MAKE_VISIBLE);
    String prompt = provider.getConfirmDuplicatePrompt(match);
    final int matchAnswer = ApplicationManager.getApplication().isUnitTestMode()
                            ? 0
                            : Messages.showYesNoCancelDialog(project, prompt, RefactoringBundle.message("process.duplicates.title", idx, size),
                                                             Messages.getQuestionIcon());
    HighlightManager.getInstance(project).removeSegmentHighlighter(editor, highlighters.get(0));
    if (matchAnswer == 0) {
      final Runnable action = new Runnable() {
        public void run() {
          try {
            provider.processMatch(match);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      };

      //use outer command
      ApplicationManager.getApplication().runWriteAction(action);
    }
    else if (matchAnswer == 2) {
      return true;
    }
    return false;
  }

  private static void expandAllRegionsCoveringRange(final Project project, Editor editor, final TextRange textRange) {
    final FoldRegion[] foldRegions = CodeFoldingManager.getInstance(project).getFoldRegionsAtOffset(editor, textRange.getStartOffset());
    boolean anyCollapsed = false;
    for (final FoldRegion foldRegion : foldRegions) {
      if (!foldRegion.isExpanded()) {
        anyCollapsed = true;
        break;
      }
    }
    if (anyCollapsed) {
      editor.getFoldingModel().runBatchFoldingOperation(new Runnable() {
        public void run() {
          for (final FoldRegion foldRegion : foldRegions) {
            if (!foldRegion.isExpanded()) {
              foldRegion.setExpanded(true);
            }
          }
        }
      });
    }
  }

  public static void highlightMatch(final Project project, Editor editor, final Match match, final ArrayList<RangeHighlighter> highlighters) {
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    HighlightManager.getInstance(project).addRangeHighlight(editor, match.getTextRange().getStartOffset(), match.getTextRange().getEndOffset(),
                                                            attributes, true, highlighters);
  }

  public static void processDuplicates(final MatchProvider provider, final Project project, Editor editor) {
    boolean hasDuplicates = provider.hasDuplicates();
    if (hasDuplicates) {
      final int answer = Messages.showYesNoDialog(project,
        RefactoringBundle.message("0.has.detected.1.code.fragments.in.this.file.that.can.be.replaced.with.a.call.to.extracted.method",
        ApplicationNamesInfo.getInstance().getProductName(), provider.getDuplicates().size()),
        "Process Duplicates", Messages.getQuestionIcon());
      if (answer == 0) {
        invoke(project, editor, provider);
      }
    }
  }
}
