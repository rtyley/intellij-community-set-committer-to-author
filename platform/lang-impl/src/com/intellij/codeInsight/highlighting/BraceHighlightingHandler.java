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
 * Date: Sep 27, 2002
 * Time: 3:10:17 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.Alarm;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class BraceHighlightingHandler {
  private static final Key<List<RangeHighlighter>> BRACE_HIGHLIGHTERS_IN_EDITOR_VIEW_KEY = Key.create("BraceHighlighter.BRACE_HIGHLIGHTERS_IN_EDITOR_VIEW_KEY");
  private static final Key<RangeHighlighter> LINE_MARKER_IN_EDITOR_KEY = Key.create("BraceHighlighter.LINE_MARKER_IN_EDITOR_KEY");

  private final Project myProject;
  private final Editor myEditor;
  private final Alarm myAlarm;

  private final DocumentEx myDocument;
  private final PsiFile myPsiFile;
  private final FileType myFileType;
  private final CodeInsightSettings myCodeInsightSettings;

  private BraceHighlightingHandler(@NotNull Project project, @NotNull Editor editor, @NotNull Alarm alarm, PsiFile psiFile) {
    myProject = project;

    myEditor = editor;
    myAlarm = alarm;
    myDocument = (DocumentEx)myEditor.getDocument();

    myPsiFile = psiFile;
    myCodeInsightSettings = CodeInsightSettings.getInstance();
    myFileType = myPsiFile == null ? null : myPsiFile.getFileType();
  }

  static void lookForInjectedAndMatchBracesInOtherThread(@NotNull final Editor editor, @NotNull final Alarm alarm, @NotNull final Processor<BraceHighlightingHandler> processor) {
    final Project project = editor.getProject();
    if (project == null) return;
    final int offset = editor.getCaretModel().getOffset();
    Job<Object> job = JobScheduler.getInstance().createJob("Brace highlighter", Job.DEFAULT_PRIORITY);
    job.addTask(new Runnable() {
      public void run() {
        if (isReallyDisposed(editor, project)) return;
        final PsiFile injected = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
          public PsiFile compute() {
            Document document = editor.getDocument();
            PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
            return null != psiFile ? getInjectedFileIfAny(editor, project, offset, psiFile, alarm) : null;
          }
        });
        ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable(){
          public void run() {
            if (!isReallyDisposed(editor, project)) {
              Editor newEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, injected);
              BraceHighlightingHandler handler = new BraceHighlightingHandler(project, newEditor, alarm, injected);
              processor.process(handler);
            }
          }
        }, ModalityState.stateForComponent(editor.getComponent()));
      }
    });
    job.schedule();
  }

  private static boolean isReallyDisposed(Editor editor, Project project) {
    Project editorProject = editor.getProject();
    return editorProject == null ||
           editorProject.isDisposed() || project.isDisposed() || !editor.getComponent().isShowing() || editor.isViewer();
  }

  private static PsiFile getInjectedFileIfAny(@NotNull final Editor editor, @NotNull final Project project, int offset, @NotNull PsiFile psiFile, @NotNull final Alarm alarm) {
    Document document = editor.getDocument();
    // when document is committed, try to highlight braces in injected lang - it's fast
    if (!PsiDocumentManager.getInstance(project).isUncommited(document)) {
      final PsiElement injectedElement = InjectedLanguageUtil.findInjectedElementNoCommit(psiFile, offset);
      if (injectedElement != null /*&& !(injectedElement instanceof PsiWhiteSpace)*/) {
        final PsiFile injected = injectedElement.getContainingFile();
        if (injected != null) {
          return injected;
        }
      }
    }
    else {
      PsiDocumentManager.getInstance(project).performForCommittedDocument(document, new Runnable() {
        public void run() {
          if (!project.isDisposed() && !editor.isDisposed()) {
            BraceHighlighter.updateBraces(editor, alarm);
          }
        }
      });
    }
    return psiFile;
  }

  public void updateBraces() {
    if (myPsiFile == null) return;

    clearBraceHighlighters();

    if (!myCodeInsightSettings.HIGHLIGHT_BRACES) return;

    if (myEditor.getSelectionModel().hasSelection()) return;

    int offset = myEditor.getCaretModel().getOffset();
    final CharSequence chars = myEditor.getDocument().getCharsSequence();

    if (myEditor.offsetToLogicalPosition(offset).column != myEditor.getCaretModel().getLogicalPosition().column) {
      // we are in virtual space
      final int caretLineNumber = myEditor.getCaretModel().getLogicalPosition().line;
      if (caretLineNumber >= myDocument.getLineCount()) return;
      offset = myDocument.getLineEndOffset(caretLineNumber) + myDocument.getLineSeparatorLength(caretLineNumber);
    }

    final int originalOffset = offset;

    HighlighterIterator iterator = getEditorHighlighter().createIterator(offset);

    if (iterator.atEnd()) {
      offset--;
    }
    else if (BraceMatchingUtil.isRBraceToken(iterator, chars, myFileType)) {
      offset--;
    }
    else if (!BraceMatchingUtil.isLBraceToken(iterator, chars, myFileType)) {
      offset--;

      if (offset >= 0) {
        final HighlighterIterator i = getEditorHighlighter().createIterator(offset);
        if (!BraceMatchingUtil.isRBraceToken(i, chars, myFileType)) offset++;
      }
    }

    if (offset < 0) {
      removeLineMarkers();
      return;
    }

    iterator = getEditorHighlighter().createIterator(offset);

    myAlarm.cancelAllRequests();

    if (BraceMatchingUtil.isLBraceToken(iterator, chars, myFileType) ||
        BraceMatchingUtil.isRBraceToken(iterator, chars, myFileType)) {
      doHighlight(offset, originalOffset);
    }

    //highlight scope
    if (!myCodeInsightSettings.HIGHLIGHT_SCOPE) {
      removeLineMarkers();
      return;
    }

    final int _offset = offset;
    myAlarm.addRequest(new Runnable() {
      public void run() {
        if (!myProject.isDisposed() && !myEditor.isDisposed()) {
          highlightScope(_offset);
        }
      }
    }, 300);
  }

  private EditorHighlighter getEditorHighlighter() {
    return ((EditorEx)myEditor).getHighlighter();
  }

  private void highlightScope(int offset) {
    if (myEditor.getFoldingModel().isOffsetCollapsed(offset)) return;
    if (myEditor.getDocument().getTextLength() <= offset) return;

    HighlighterIterator iterator = getEditorHighlighter().createIterator(offset);
    final CharSequence chars = myDocument.getCharsSequence();

    if (!BraceMatchingUtil.isStructuralBraceToken(myFileType, iterator, chars)) {
//      if (BraceMatchingUtil.isRBraceTokenToHighlight(myFileType, iterator) || BraceMatchingUtil.isLBraceTokenToHighlight(myFileType, iterator)) return;
    }
    else {
      if (BraceMatchingUtil.isRBraceToken(iterator, chars, myFileType) || BraceMatchingUtil.isLBraceToken(iterator, chars, myFileType)) return;
    }

    if (!BraceMatchingUtil.findStructuralLeftBrace(myFileType, iterator, chars)) {
      removeLineMarkers();
      return;
    }

    highlightLeftBrace(iterator, true);
  }

  private void doHighlight(int offset, int originalOffset) {
    if (myEditor.getFoldingModel().isOffsetCollapsed(offset)) return;

    HighlighterIterator iterator = getEditorHighlighter().createIterator(offset);
    final CharSequence chars = myDocument.getCharsSequence();

    if (BraceMatchingUtil.isLBraceToken(iterator, chars, myFileType)) {
      IElementType tokenType = iterator.getTokenType();

      iterator.advance();
      if (!iterator.atEnd() && BraceMatchingUtil.isRBraceToken(iterator, chars, myFileType)) {
        if (BraceMatchingUtil.isPairBraces(tokenType, iterator.getTokenType(), myFileType) && originalOffset == iterator.getStart()) return;
      }

      iterator.retreat();
      highlightLeftBrace(iterator, false);

      if (offset > 0) {
        iterator = getEditorHighlighter().createIterator(offset - 1);
        if (BraceMatchingUtil.isRBraceToken(iterator, chars, myFileType)) {
          highlightRightBrace(iterator);
        }
      }
    }
    else if (BraceMatchingUtil.isRBraceToken(iterator, chars, myFileType)) {
      highlightRightBrace(iterator);
    }
  }

  private void highlightRightBrace(HighlighterIterator iterator) {
    int brace1End = iterator.getEnd();

    boolean matched = BraceMatchingUtil.matchBrace(myDocument.getCharsSequence(), myFileType, iterator, false);

    int brace2Start = iterator.atEnd() ? -1 : iterator.getStart();

    highlightBraces(brace2Start, brace1End - 1, matched, false);
  }

  private void highlightLeftBrace(HighlighterIterator iterator, boolean scopeHighlighting) {
    int brace1Start = iterator.getStart();
    boolean matched = BraceMatchingUtil.matchBrace(myDocument.getCharsSequence(), myFileType, iterator, true);

    int brace2End = iterator.atEnd() ? -1 : iterator.getEnd() - 1;

    highlightBraces(brace1Start, brace2End, matched, scopeHighlighting);
  }

  private void highlightBraces(final int lBraceOffset, int rBraceOffset, boolean matched, boolean scopeHighlighting) {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    final TextAttributes attributes =
        matched ? scheme.getAttributes(CodeInsightColors.MATCHED_BRACE_ATTRIBUTES)
        : scheme.getAttributes(CodeInsightColors.UNMATCHED_BRACE_ATTRIBUTES);

    if (rBraceOffset >= 0 && !scopeHighlighting) {
      highlightBrace(rBraceOffset, matched);
    }

    if (lBraceOffset >= 0) {
      if (!scopeHighlighting) {
        highlightBrace(lBraceOffset, matched);
      }
    }

    if (!myEditor.equals(FileEditorManager.getInstance(myProject).getSelectedTextEditor())) {
      return;
    }

    if (lBraceOffset >= 0 && rBraceOffset >= 0) {
      final int startLine = myEditor.offsetToLogicalPosition(lBraceOffset).line;
      final int endLine = myEditor.offsetToLogicalPosition(rBraceOffset).line;
      if (endLine - startLine > 0) {
        final Runnable runnable = new Runnable() {
          public void run() {
            if (myProject.isDisposed() || myEditor.isDisposed()) return;
            Color color = attributes.getBackgroundColor();
            if (color == null) return;
            color = color.darker();
            lineMarkFragment(startLine, endLine, color);
          }
        };

        if (!scopeHighlighting) {
          myAlarm.addRequest(runnable, 300);
        }
        else {
          runnable.run();
        }
      }
      else {
        if (!myCodeInsightSettings.HIGHLIGHT_SCOPE) {
          removeLineMarkers();
        }
      }

      if (!scopeHighlighting) {
        showScopeHint(lBraceOffset, lBraceOffset + 1);
      }
    }
    else {
      if (!myCodeInsightSettings.HIGHLIGHT_SCOPE) {
        removeLineMarkers();
      }
    }
  }

  private void highlightBrace(int rBraceOffset, boolean matched) {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    final TextAttributes attributes =
        matched ? scheme.getAttributes(CodeInsightColors.MATCHED_BRACE_ATTRIBUTES)
        : scheme.getAttributes(CodeInsightColors.UNMATCHED_BRACE_ATTRIBUTES);


    RangeHighlighter rbraceHighlighter =
        myEditor.getMarkupModel().addRangeHighlighter(
            rBraceOffset, rBraceOffset + 1, HighlighterLayer.LAST + 1, attributes, HighlighterTargetArea.EXACT_RANGE);
    rbraceHighlighter.setGreedyToLeft(false);
    rbraceHighlighter.setGreedyToRight(false);
    registerHighlighter(rbraceHighlighter);
  }

  private void registerHighlighter(RangeHighlighter highlighter) {
    List<RangeHighlighter> highlighters = myEditor.getUserData(BRACE_HIGHLIGHTERS_IN_EDITOR_VIEW_KEY);
    if (highlighters == null) {
      highlighters = new ArrayList<RangeHighlighter>();
      myEditor.putUserData(BRACE_HIGHLIGHTERS_IN_EDITOR_VIEW_KEY, highlighters);
    }

    highlighters.add(highlighter);
  }

  private void showScopeHint(final int lbraceStart, final int lbraceEnd) {
    LogicalPosition bracePosition = myEditor.offsetToLogicalPosition(lbraceStart);
    Point braceLocation = myEditor.logicalPositionToXY(bracePosition);
    final int y = braceLocation.y;
    myAlarm.addRequest(
        new Runnable() {
          public void run() {
            if (!myEditor.getComponent().isShowing()) return;
            Rectangle viewRect = myEditor.getScrollingModel().getVisibleArea();
            if (y < viewRect.y) {
              int start = lbraceStart;
              if (!(myPsiFile instanceof PsiPlainTextFile)) {
                PsiDocumentManager.getInstance(myProject).commitAllDocuments();
                start = BraceMatchingUtil.getBraceMatcher(myFileType, PsiUtilBase.getLanguageAtOffset(myPsiFile, lbraceStart)).getCodeConstructStart(myPsiFile, lbraceStart);
              }
              TextRange range = new TextRange(start, lbraceEnd);
              int line1 = myDocument.getLineNumber(range.getStartOffset());
              int line2 = myDocument.getLineNumber(range.getEndOffset());
              line1 = Math.max(line1, line2 - 5);
              range = new TextRange(myDocument.getLineStartOffset(line1), range.getEndOffset());
              EditorFragmentComponent.showEditorFragmentHint(myEditor, range, true);
            }
          }
        },
        300, ModalityState.stateForComponent(myEditor.getComponent()));
  }

  public void clearBraceHighlighters() {
    List<RangeHighlighter> highlighters = myEditor.getUserData(BRACE_HIGHLIGHTERS_IN_EDITOR_VIEW_KEY);
    if (highlighters == null) return;
    myEditor.putUserData(BRACE_HIGHLIGHTERS_IN_EDITOR_VIEW_KEY, null);
    for (final Object highlighter : highlighters) {
      RangeHighlighter rangeHighlighter = (RangeHighlighter)highlighter;
      myEditor.getMarkupModel().removeHighlighter(rangeHighlighter);
    }
  }

  private void lineMarkFragment(int startLine, int endLine, Color color) {
    removeLineMarkers();

    if (startLine >= endLine || endLine >= myDocument.getLineCount()) return;

    int startOffset = myDocument.getLineStartOffset(startLine);
    int endOffset = myDocument.getLineStartOffset(endLine);

    RangeHighlighter highlighter = myEditor.getMarkupModel().addRangeHighlighter(startOffset, endOffset, 0, null, HighlighterTargetArea.LINES_IN_RANGE);
    highlighter.setLineMarkerRenderer(new MyLineMarkerRenderer(color));
    myEditor.putUserData(LINE_MARKER_IN_EDITOR_KEY, highlighter);
  }

  private void removeLineMarkers() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    RangeHighlighter marker = myEditor.getUserData(LINE_MARKER_IN_EDITOR_KEY);
    if (marker != null && ((MarkupModelEx)myEditor.getMarkupModel()).containsHighlighter(marker)) {
      myEditor.getMarkupModel().removeHighlighter(marker);
    }
    myEditor.putUserData(LINE_MARKER_IN_EDITOR_KEY, null);
  }

  private static class MyLineMarkerRenderer implements LineMarkerRenderer {
    private static final int DEEPNESS = 2;
    private static final int THICKNESS = 2;
    private final Color myColor;

    private MyLineMarkerRenderer(Color color) {
      myColor = color;
    }

    public void paint(Editor editor, Graphics g, Rectangle r) {
      int height = r.height + editor.getLineHeight();
      g.setColor(myColor);
      g.fillRect(r.x, r.y, THICKNESS, height);
      g.fillRect(r.x + THICKNESS, r.y, DEEPNESS, THICKNESS);
      g.fillRect(r.x + THICKNESS, r.y + height - THICKNESS, DEEPNESS, THICKNESS);
    }
  }
}
