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
package com.intellij.application.options;

import com.intellij.openapi.editor.TextChange;
import com.intellij.openapi.editor.impl.softwrap.TextChangeImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Allows to calculate difference between two set of changes applied to the same document.
 * <p/>
 * <b>Note:</b> current class doesn't provide precise diff for changes sets at the moment, i.e. there is a number of use-cases where
 * it's processing should be revised. However, it works fine at its main usage scenario (at the moment) -
 * <code>'code style preview'</code> panel. Feel free to revise it as necessary and add corresponding unit tests to
 * <code>'ChangesDiffCalculatorTest'</code> class.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since 10/14/10 2:44 PM
 */
public class ChangesDiffCalculator {

  /**
   * Allows to calculate diff between the given document changes assuming the following:
   * <pre>
   * <ul>
   *   <li>
   *      <code>'before changes'</code> contains information about changes after modifying document at past at particular manner;
   *   </li>
   *   <li>
   *      <code>'after changes'</code> contains information about changes after modifying active document at particular manner;
   *   </li>
   *   <li>
   *      every {@link TextChange document change} defines target document offset and initial document text located there;
   *   </li>
   * </ul>
   * </pre>
   *
   * @param beforeChanges   contains information about changes after modifying document at past at particular manner.
   *                        Is assumed to be sorted by change start offset in ascending order
   * @param beforeText      document text after applying given <code>'before changes'</code>
   * @param currentChanges  contains information about changes after modifying active document at particular manner.
   *                        Is assumed to be sorted by change start offset in ascending order
   * @param currentText     document text after applying given <code>'current changes'</code>
   * @return                information about diff ranges between the given changes against the <b>current</b> document
   */
  @SuppressWarnings({"MethodMayBeStatic"})
  public Collection<TextRange> calculateDiff(@NotNull List<? extends TextChange> beforeChanges, @NotNull CharSequence beforeText,
                                             @NotNull List<? extends TextChange> currentChanges, @NotNull CharSequence currentText)
  {
    List<TextRange> result = new ArrayList<TextRange>();
    Context context = new Context(beforeChanges, beforeText, currentChanges, currentText);

    while (context.beforeChange != null && context.currentChange != null) {
      if (tryMatchScatteredChanges(context)) {
        continue;
      }

      // 'Before' change starts before 'current' change
      if (context.beforeOriginalStart < context.currentOriginalStart) {
        handleBeforeChangeBeforeCurrentChange(context, result);
      }
      else if (context.beforeOriginalStart > context.currentOriginalStart) {
        handleBeforeChangeAfterCurrentChange(context, result);
      }
      else {
        handleChangesSharingStartOffset(context, result);
      }
    }

    registerTailChanges(context, result);

    return result;
  }

  /**
   * Manages situation when {@link Context#beforeChange 'before' change} targets original document offset that is located
   * strictly before original document offset targeted by {@link Context#currentChange 'current' change}.
   *
   * @param context   processing context
   * @param storage   collection to store processing diff results
   */
  private static void handleBeforeChangeBeforeCurrentChange(Context context, List<TextRange> storage) {
    if (context.beforeOriginalEnd < context.currentOriginalStart) {
      storage.add(new TextRange(context.beforeCurrentStart, context.beforeCurrentStart + context.beforeChange.getText().length()));
    }
    else {
      storage.add(new TextRange(context.beforeCurrentStart, context.currentChange.getEnd()));
      context.beforeShiftToCurrent -= getDiff(context.currentChange);
      context.proceedToNextChange(Context.ChangeType.CURRENT);
    }

    context.beforeShiftToCurrent += getDiff(context.beforeChange);
    context.proceedToNextChange(Context.ChangeType.BEFORE);
  }

  /**
   * Manages situation when {@link Context#beforeChange 'before' change} targets original document offset that is located
   * strictly after original document offset targeted by {@link Context#currentChange 'current' change}.
   *
   * @param context   processing context
   * @param storage   collection to store processing diff results
   */
  private static void handleBeforeChangeAfterCurrentChange(Context context, List<TextRange> storage) {
    storage.add(new TextRange(context.currentChange.getStart(), context.currentChange.getEnd()));
    context.beforeShiftToCurrent -= getDiff(context.currentChange);
    context.proceedToNextChange(Context.ChangeType.CURRENT);
  }

  /**
   * Manages situation when {@link Context#beforeChange 'before' change} and {@link Context#currentChange 'current'} changes target
   * the same original document offset.
   *
   * @param context   processing context
   * @param storage   collection to store processing diff results
   */
  private static void handleChangesSharingStartOffset(Context context, List<TextRange> storage) {
    int lengthDiff = getLength(context.currentChange) - getLength(context.beforeChange);
    int originTextDiff = context.beforeChange.getText().length() - context.currentChange.getText().length();

    //if (lengthDiff != 0 && tryMatchScatteredChanges(context)) {
    //  return;
    //}

    int oldSize = storage.size();

    if (lengthDiff > 0) {
      storage.add(new TextRange(
        context.beforeChange.getEnd() + context.beforeShiftToCurrent, context.currentChange.getEnd() + originTextDiff
      ));
    }
    else if (lengthDiff < 0) {
      storage.add(new TextRange(context.currentChange.getEnd(), context.currentChange.getEnd()));
    }
    else if (context.beforeChange.getText().length() != context.currentChange.getText().length()) {
      if (StringUtil.equals(context.textForChangeRange(Context.ChangeType.BEFORE), context.textForChangeRange(Context.ChangeType.CURRENT)))
      {
        if (originTextDiff < 0) {
          storage.add(new TextRange(context.currentChange.getEnd(), context.currentChange.getEnd()));
        }
        else {
          storage.add(new TextRange(context.currentChange.getEnd(), context.currentChange.getEnd() + originTextDiff));
        }
      }
    }

    if (storage.size() != oldSize) {
      context.beforeShiftToCurrent += getDiff(context.beforeChange) - getDiff(context.currentChange);
    }

    context.proceedToNextChange(Context.ChangeType.BEFORE);
    context.proceedToNextChange(Context.ChangeType.CURRENT);
  }

  private static boolean tryMatchScatteredChanges(Context context) {
    TextChange currentChange = context.currentChange;
    TextChange beforeChange = context.beforeChange;
    CharSequence beforeTail = context.beforeText.subSequence(beforeChange.getStart(), context.beforeText.length());
    CharSequence currentTail = context.currentText.subSequence(currentChange.getStart(), context.currentText.length());
    int matchedSymbolsNumber = StringUtil.commonPrefixLength(beforeTail, currentTail);
    if (matchedSymbolsNumber <= 0) {
      return false;
    }

    int beforeEnd = beforeChange.getStart() + matchedSymbolsNumber;
    int currentEnd = currentChange.getStart() + matchedSymbolsNumber;
    Context contextCopy = new Context();
    context.copyTo(contextCopy);

    int offset = -1;
    StringBuilder beforeOriginalText = new StringBuilder(beforeChange.getText());
    int matchedChangesNumber = 0;
    int beforeUnchangedTailSymbolsNumber = 0;
    while (context.beforeChange != null && context.beforeChange.getStart() < beforeEnd) {
      if (offset >= 0) {
        CharSequence text = context.beforeText.subSequence(offset, context.beforeChange.getStart());
        beforeOriginalText.append(text);
        beforeOriginalText.append(context.beforeChange.getText());
      }
      offset = context.beforeChange.getEnd();
      if (context.beforeChange.getEnd() <= beforeEnd) {
        beforeUnchangedTailSymbolsNumber = beforeEnd - context.beforeChange.getEnd();
        context.proceedToNextChange(Context.ChangeType.BEFORE);
        matchedChangesNumber++;
      }
      else {
        context.beforeChange = new TextChangeImpl(context.beforeChange.getText(), beforeEnd, context.beforeChange.getEnd());
        beforeUnchangedTailSymbolsNumber = 0;
        break;
      }
    }
    if (offset >= 0 && offset < beforeEnd) {
      beforeOriginalText.append(context.beforeText.subSequence(offset, beforeEnd));
    }

    matchedChangesNumber = matchedChangesNumber < 2 ? 0 : matchedChangesNumber;
    offset = -1;
    StringBuilder currentOriginalText = new StringBuilder(currentChange.getText());
    while (context.currentChange != null && context.currentChange.getStart() < currentEnd) {
      if (offset >= 0) {
        CharSequence text = context.currentText.subSequence(offset, context.currentChange.getStart());
        currentOriginalText.append(text);
        if (context.currentChange.getEnd() < currentEnd) {
          currentOriginalText.append(context.currentChange.getText());
        }
      }
      offset = context.currentChange.getEnd();
      if (context.currentChange.getEnd() <= currentEnd) {
        context.proceedToNextChange(Context.ChangeType.CURRENT);
        matchedChangesNumber++;
      }
      else {
        context.currentShiftToOriginal += context.currentChange.getStart() - currentEnd + beforeUnchangedTailSymbolsNumber;
        context.currentChange = new TextChangeImpl(
          context.currentChange.getText(), currentEnd - beforeUnchangedTailSymbolsNumber, context.currentChange.getEnd()
        );
        break;
      }
    }
    if (offset >= 0 && offset < currentEnd) {
      currentOriginalText.append(context.currentText.subSequence(offset, currentEnd));
    }

    context.beforeShiftToCurrent += contextCopy.currentShiftToOriginal - context.currentShiftToOriginal
                                    - (contextCopy.beforeShiftToOriginal - context.beforeShiftToOriginal);

    if (matchedChangesNumber < 2 || !StringUtil.equals(beforeOriginalText, currentOriginalText)) {
      contextCopy.copyTo(context);
      return false;
    }

    context.update();
    return true;
  }

  /**
   * There is a possible case that we process all {@link Context#beforeChanges 'before'} or {@link Context#currentChanges 'current'}
   * changes but not all changes of another type are processed yet. Hence, we need to store them too. This method handles that.
   *
   * @param context   processing context
   * @param storage   collection to store processing diff results
   */
  private static void registerTailChanges(Context context, List<TextRange> storage) {
    if (context.beforeChange != null) {
      for (; context.beforeIndex < context.beforeChanges.size(); context.beforeIndex++) {
        TextChange change = context.beforeChanges.get(context.beforeIndex);
        int offset = change.getStart() + context.beforeShiftToCurrent;
        storage.add(new TextRange(offset, offset + change.getText().length()));
      }
    }

    if (context.currentChange != null) {
      for (; context.currentIndex < context.currentChanges.size(); context.currentIndex++) {
        TextChange change = context.currentChanges.get(context.currentIndex);
        storage.add(new TextRange(change.getStart(), change.getEnd()));
      }
    }
  }

  private static int getDiff(TextChange change) {
    return change.getText().length() - getLength(change);
  }

  private static int getLength(TextChange change) {
    return change.getEnd() - change.getStart();
  }

  /**
   * Utility class to hold stack-local processing state
   */
  private static class Context {

    enum ChangeType { BEFORE, CURRENT }

    private List<TextChange> beforeChanges = new ArrayList<TextChange>();
    private List<TextChange> currentChanges = new ArrayList<TextChange>();
    private CharSequence beforeText;
    private CharSequence currentText;

    /** Shift to apply to 'before' change start offset in order to get offset within original document. */
    int beforeShiftToOriginal;
    /** Shift to apply to 'before' change start offset in order to get offset within current document. */
    int beforeShiftToCurrent = 0;
    /** Shift to apply to 'current' change start offset in order to get offset within original document. */
    int currentShiftToOriginal = 0;

    int beforeIndex = 0;
    TextChange beforeChange;
    int currentIndex = 0;
    TextChange currentChange;

    int beforeOriginalStart;
    int beforeCurrentStart;
    int beforeOriginalEnd;
    int currentOriginalStart;
    int currentOriginalEnd;

    Context() {
    }

    Context(List<? extends TextChange> beforeChanges, CharSequence beforeText, List<? extends TextChange> currentChanges,
            CharSequence currentText)
    {
      this.beforeChanges.addAll(beforeChanges);
      this.beforeText = beforeText;
      this.currentChanges.addAll(currentChanges);
      this.currentText = currentText;
      beforeChange = beforeChanges.isEmpty() ? null : beforeChanges.get(0);
      currentChange = currentChanges.isEmpty() ? null : currentChanges.get(0);
      update();
    }

    /**
     * Updates current context state.
     * <p/>
     * Is assumed to be called on {@link #beforeChange} or {@link #currentChange} change.
     */
    void update() {
      if (beforeChange != null) {
        beforeOriginalStart = beforeChange.getStart() + beforeShiftToOriginal;
        //TODO den check
        //if (getDiff(beforeChange) > 0) {
        //  beforeOriginalStart += getDiff(beforeChange);
        //}
        beforeCurrentStart = beforeChange.getStart() + beforeShiftToCurrent;
        beforeOriginalEnd = beforeOriginalStart + beforeChange.getText().length();
      }

      if (currentChange != null) {
        currentOriginalStart = currentChange.getStart() + currentShiftToOriginal;
        //TODO den check
        //if (getDiff(currentChange) > 0) {
        //  currentOriginalStart += getDiff(currentChange);
        //}
        currentOriginalEnd = currentOriginalStart + currentChange.getText().length();
      }
    }

    void proceedToNextChange(ChangeType changeType) {
      if (changeType == ChangeType.BEFORE) {
        beforeShiftToOriginal += getDiff(beforeChange);
        beforeChange = ++beforeIndex < beforeChanges.size() ? beforeChanges.get(beforeIndex) : null;
      }
      else {
        currentShiftToOriginal += getDiff(currentChange);
        currentChange = ++currentIndex < currentChanges.size() ? currentChanges.get(currentIndex) : null;
      }
      update();
    }

    CharSequence textForChangeRange(ChangeType changeType) {
      CharSequence text = changeType == ChangeType.BEFORE ? beforeText : currentText;
      TextChange change = changeType == ChangeType.BEFORE ? beforeChange : currentChange;
      return text.subSequence(change.getStart(), change.getEnd());
    }

    void copyTo(Context context) {
      context.beforeChanges.clear();
      context.beforeChanges.addAll(beforeChanges);
      context.currentChanges.clear();
      context.currentChanges.addAll(currentChanges);
      context.beforeText = beforeText;
      context.currentText = currentText;
      context.beforeShiftToOriginal = beforeShiftToOriginal;
      context.beforeShiftToCurrent = beforeShiftToCurrent;
      context.currentShiftToOriginal = currentShiftToOriginal;
      context.beforeIndex = beforeIndex;
      context.beforeChange = beforeChange;
      context.currentIndex = currentIndex;
      context.currentChange = currentChange;
      context.beforeOriginalStart = beforeOriginalStart;
      context.beforeCurrentStart = beforeCurrentStart;
      context.beforeOriginalEnd = beforeOriginalEnd;
      context.currentOriginalStart = currentOriginalStart;
      context.currentOriginalEnd = currentOriginalEnd;
    }
  }
}
