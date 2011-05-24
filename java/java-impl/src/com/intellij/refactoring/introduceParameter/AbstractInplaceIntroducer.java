/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
 * User: anna
 * Date: 3/15/11
 */
public abstract class AbstractInplaceIntroducer<V extends PsiNamedElement, E extends PsiElement> extends AbstractInplaceVariableIntroducer<E> {
  protected final V myLocalVariable;

  private final String myExprText;
  private final String myLocalName;

  protected String myConstantName;

  public AbstractInplaceIntroducer(Project project,
                                   Editor editor,
                                   E expr,
                                   V localVariable,
                                   E[] occurrences,
                                   String title) {
    super(null, editor, project, title, occurrences, expr);
    myLocalVariable = localVariable;
    myExprText = expr != null ? expr.getText() : null;
    myLocalName = localVariable != null ? localVariable.getName() : null;
  }

  protected abstract String getCommandName();

  protected abstract V createFieldToStartTemplateOn(boolean replaceAll, String[] names);
  protected abstract String[] suggestNames(boolean replaceAll, V variable);

  protected abstract void performIntroduce();
  protected void performPostIntroduceTasks() {}

  public abstract E restoreExpression(PsiFile containingFile, V variable, RangeMarker marker, String exprText);

  protected abstract boolean isReplaceAllOccurrences();
  protected abstract JComponent getComponent();


  protected void restoreAnchors() {}

  protected abstract void saveSettings(V variable);
  protected abstract V getVariable();

  public boolean startInplaceIntroduceTemplate() {
    final boolean replaceAllOccurrences = isReplaceAllOccurrences();
    final Ref<Boolean> result = new Ref<Boolean>();
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        final String[] names = suggestNames(replaceAllOccurrences, myLocalVariable);
        final V variable = createFieldToStartTemplateOn(replaceAllOccurrences, names);
        boolean started = false;
        if (variable != null) {
          myEditor.getCaretModel().moveToOffset(variable.getTextOffset());
          myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);

          final LinkedHashSet<String> nameSuggestions = new LinkedHashSet<String>();
          nameSuggestions.add(variable.getName());
          nameSuggestions.addAll(Arrays.asList(names));
          initOccurrencesMarkers();
          setElementToRename(variable);
          started = AbstractInplaceIntroducer.super.performInplaceRename(false, nameSuggestions);
        }
        result.set(started);
        if (!started && variable != null) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              variable.delete();
            }
          });
        }
      }

    }, getCommandName(), getCommandName());
    return result.get();
  }

  protected String getInputName() {
    return myConstantName;
  }

  @Override
  protected boolean performAutomaticRename() {
    return false;
  }


  @Override
  public void finish() {
    super.finish();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    final V variable = getVariable();
    if (variable == null) {
      return;
    }
    restoreState(variable);
  }

  @Override
  protected void addReferenceAtCaret(Collection<PsiReference> refs) {
    final V variable = getVariable();
    if (variable != null) {
      for (PsiReference reference : ReferencesSearch.search(variable)) {
        refs.remove(reference);
      }
    }
  }

  @Override
  protected boolean appendAdditionalElement(List<Pair<PsiElement, TextRange>> stringUsages) {
    return true;
  }

  @Override
  protected void collectAdditionalElementsToRename(boolean processTextOccurrences, List<Pair<PsiElement, TextRange>> stringUsages) {
    if (isReplaceAllOccurrences()) {
      for (E expression : getOccurrences()) {
        stringUsages.add(Pair.<PsiElement, TextRange>create(expression, new TextRange(0, expression.getTextLength())));
      }
    }
    else if (getExpr() != null) {
      stringUsages.add(Pair.<PsiElement, TextRange>create(getExpr(), new TextRange(0, getExpr().getTextLength())));
    }
  }

  @Override
  protected void collectAdditionalRangesToHighlight(Map<TextRange, TextAttributes> rangesToHighlight,
                                                    Collection<Pair<PsiElement, TextRange>> stringUsages,
                                                    EditorColorsManager colorsManager) {
  }

  @Override
  protected void addHighlights(@NotNull Map<TextRange, TextAttributes> ranges,
                               @NotNull Editor editor,
                               @NotNull Collection<RangeHighlighter> highlighters,
                               @NotNull HighlightManager highlightManager) {
    final TextAttributes attributes =
      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    final int variableNameLength = getVariable().getName().length();
    if (isReplaceAllOccurrences()) {
      for (RangeMarker marker : getOccurrenceMarkers()) {
        final int startOffset = marker.getStartOffset();
        highlightManager.addOccurrenceHighlight(editor, startOffset, startOffset + variableNameLength, attributes, 0, highlighters, null);
      }
    }
    else if (getExpr() != null) {
      final int startOffset = getExprMarker().getStartOffset();
      highlightManager.addOccurrenceHighlight(editor, startOffset, startOffset + variableNameLength, attributes, 0, highlighters, null);
    }
    super.addHighlights(ranges, editor, highlighters, highlightManager);
  }

  protected void restoreState(final V psiField) {
    myConstantName = psiField.getName();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final PsiFile containingFile = psiField.getContainingFile();
        final RangeMarker exprMarker = getExprMarker();
        if (exprMarker != null) {
          myExpr = restoreExpression(containingFile, psiField, exprMarker, myExprText);
          if (myExpr != null && myExpr.isPhysical()) {
            myExprMarker = myEditor.getDocument().createRangeMarker(myExpr.getTextRange());
          }
        }
        final List<RangeMarker> occurrenceMarkers = getOccurrenceMarkers();
        for (int i = 0, occurrenceMarkersSize = occurrenceMarkers.size(); i < occurrenceMarkersSize; i++) {
          RangeMarker marker = occurrenceMarkers.get(i);
          if (getExprMarker() != null && marker.getStartOffset() == getExprMarker().getStartOffset()) {
            myOccurrences[i] = myExpr;
            continue;
          }
          final E psiExpression =
             restoreExpression(containingFile, psiField, marker, myLocalVariable != null ? myLocalName : myExprText);
          if (psiExpression != null) {
            myOccurrences[i] = psiExpression;
          }
        }

        restoreAnchors();
        myOccurrenceMarkers = null;
        if (psiField.isValid()) {
          psiField.delete();
        }
      }
    });
  }

  @Override
  protected void moveOffsetAfter(boolean success) {
    if (success) {
      if (myLocalVariable == null && myExpr == null ||
          getInputName() == null ||
          myLocalVariable != null && !myLocalVariable.isValid() ||
          myExpr != null && !myExpr.isValid()) {
        super.moveOffsetAfter(false);
        return;
      }
      performIntroduce();
      saveSettings(getVariable());
    }
    if (myLocalVariable != null && myLocalVariable.isValid()) {
      myEditor.getCaretModel().moveToOffset(myLocalVariable.getTextOffset());
      myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
    else if (getExprMarker() != null) {
      myEditor.getCaretModel().moveToOffset(getExprMarker().getStartOffset());
      myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
    super.moveOffsetAfter(success);
    if (success) {
      performPostIntroduceTasks();
    }
  }

  protected void restartTemplate() {
    Runnable restartTemplateRunnable = new Runnable() {
      public void run() {
        final TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
        if (templateState != null) {
          myEditor.putUserData(INTRODUCE_RESTART, true);
          try {
            templateState.gotoEnd(true);
            startInplaceIntroduceTemplate();
          }
          finally {
            myEditor.putUserData(INTRODUCE_RESTART, false);
          }
        }
      }
    };
    CommandProcessor.getInstance().executeCommand(myProject, restartTemplateRunnable, getCommandName(), getCommandName());
  }
}
