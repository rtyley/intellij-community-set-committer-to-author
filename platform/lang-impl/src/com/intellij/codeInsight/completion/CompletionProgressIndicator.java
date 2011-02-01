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

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ReferenceRange;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author peter
 */
public class CompletionProgressIndicator extends ProgressIndicatorBase implements CompletionProcess{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CompletionProgressIndicator");
  private final Editor myEditor;
  private final CompletionParameters myParameters;
  private final CodeCompletionHandlerBase myHandler;
  private final LookupImpl myLookup;
  private final MergingUpdateQueue myQueue;
  private final Update myUpdate = new Update("update") {
    public void run() {
      updateLookup();
      myQueue.setMergingTimeSpan(100);
    }
  };
  private final Semaphore myFreezeSemaphore;
  private final OffsetMap myOffsetMap;
  private final CopyOnWriteArrayList<Pair<Integer, ElementPattern<String>>> myRestartingPrefixConditions = ContainerUtil.createEmptyCOWList();
  private final LookupAdapter myLookupListener = new LookupAdapter() {
    public void itemSelected(LookupEvent event) {
      ensureDuringCompletionPassed();

      finishCompletionProcess();

      LookupElement item = event.getItem();
      if (item == null) return;

      setMergeCommand();

      myOffsetMap.addOffset(CompletionInitializationContext.START_OFFSET, myEditor.getCaretModel().getOffset() - item.getLookupString().length());
      CodeCompletionHandlerBase.selectLookupItem(item, event.getCompletionChar(), CompletionProgressIndicator.this, myLookup.getItems());
    }


    public void lookupCanceled(final LookupEvent event) {
      finishCompletionProcess();
    }
  };
  private final Semaphore myDuringCompletionSemaphore = new Semaphore();

  private volatile int myCount;

  public CompletionProgressIndicator(final Editor editor, CompletionParameters parameters, CodeCompletionHandlerBase handler, Semaphore freezeSemaphore,
                                     final OffsetMap offsetMap, LookupImpl lookup) {
    myEditor = editor;
    myParameters = parameters;
    myHandler = handler;
    myFreezeSemaphore = freezeSemaphore;
    myOffsetMap = offsetMap;
    myLookup = lookup;

    myLookup.setArranger(new CompletionLookupArranger(parameters));

    myLookup.addLookupListener(myLookupListener);
    myLookup.setCalculating(true);

    myQueue = new MergingUpdateQueue("completion lookup progress", 200, true, myEditor.getContentComponent());

    ApplicationManager.getApplication().assertIsDispatchThread();
    registerItself();

    if (!ApplicationManager.getApplication().isUnitTestMode() && !lookup.isShown()) {
      scheduleAdvertising();
    }

    trackModifiers();

    myDuringCompletionSemaphore.down();
  }

  public OffsetMap getOffsetMap() {
    return myOffsetMap;
  }

  public int getSelectionEndOffset() {
    return getOffsetMap().getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);
  }

  void duringCompletion(CompletionInitializationContext initContext) {
    try {
      ProgressManager.checkCanceled();

      if (!initContext.getOffsetMap().wasModified(CompletionInitializationContext.IDENTIFIER_END_OFFSET)) {
        try {
          final int selectionEndOffset = initContext.getSelectionEndOffset();
          final PsiReference reference = initContext.getFile().findReferenceAt(selectionEndOffset);
          if (reference != null) {
            initContext.setReplacementOffset(findReplacementOffset(selectionEndOffset, reference));
          }
        }
        catch (IndexNotReadyException ignored) {
        }
      }

      for (CompletionContributor contributor : CompletionContributor.forLanguage(initContext.getPositionLanguage())) {
        if (DumbService.getInstance(initContext.getProject()).isDumb() && !DumbService.isDumbAware(contributor)) {
          continue;
        }

        contributor.duringCompletion(initContext);
      }
    } catch (ProcessCanceledException ignored) {
    } finally {
      duringCompletionPassed();
    }
  }

  void duringCompletionPassed() {
    myDuringCompletionSemaphore.up();
  }

  void ensureDuringCompletionPassed() {
    myDuringCompletionSemaphore.waitFor();
  }

  public void setFocusLookupWhenDone(boolean focusLookup) {
    LOG.assertTrue(isAutopopupCompletion());
    if (focusLookup) {
      final CompletionPhase phase = CompletionServiceImpl.getCompletionPhase();
      if (!(phase instanceof CompletionPhase.Restarted)) {
        ((CompletionPhase.BgCalculation)phase).focusLookupWhenDone = true;
      }
    } else {
      myLookup.setAdvertisementText("Press " +
                                    CompletionContributor.getActionShortcut(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_REPLACE) +
                                    " to choose the first suggestion");
    }
  }

  private static int findReplacementOffset(int selectionEndOffset, PsiReference reference) {
    final List<TextRange> ranges = ReferenceRange.getAbsoluteRanges(reference);
    for (TextRange range : ranges) {
      if (range.contains(selectionEndOffset)) {
        return range.getEndOffset();
      }
    }

    return reference.getElement().getTextRange().getStartOffset() + reference.getRangeInElement().getEndOffset();
  }


  private void scheduleAdvertising() {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        if (isOutdated()) return; //tests?
        final List<CompletionContributor> list = ApplicationManager.getApplication().runReadAction(new Computable<List<CompletionContributor>>() {
          public List<CompletionContributor> compute() {
            if (isOutdated()) {
              return Collections.emptyList();
            }

            return CompletionContributor.forParameters(myParameters);
          }
        });
        for (final CompletionContributor contributor : list) {
          if (myLookup.getAdvertisementText() != null) return;
          if (!myLookup.isCalculating() && !myLookup.isVisible()) return;

          String s = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
            @Nullable
            public String compute() {
              if (isOutdated()) {
                return null;
              }

              return contributor.advertise(myParameters);
            }
          });
          if (myLookup.getAdvertisementText() != null) return;

          if (s != null) {
            myLookup.setAdvertisementText(s);
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                if (isAutopopupCompletion() && !myLookup.isShown()) {
                  return;
                }
                if (!CompletionServiceImpl.isPhase(CompletionPhase.BgCalculation.class, CompletionPhase.ItemsCalculated.class)) {
                  return;
                }
                if (CompletionServiceImpl.getCompletionPhase().indicator != CompletionProgressIndicator.this) {
                  return;
                }

                updateLookup();
              }
            }, myQueue.getModalityState());
            return;
          }
        }
      }
    });
  }

  private boolean isOutdated() {
    return CompletionServiceImpl.getCompletionPhase().indicator != this;
  }

  private void trackModifiers() {
    if (isAutopopupCompletion()) {
      return;
    }

    final JComponent contentComponent = myEditor.getContentComponent();
    contentComponent.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        processModifier(e);
      }

      public void keyReleased(KeyEvent e) {
        processModifier(e);
      }

      private void processModifier(KeyEvent e) {
        final int code = e.getKeyCode();
        if (code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_META || code == KeyEvent.VK_ALT || code == KeyEvent.VK_SHIFT) {
          contentComponent.removeKeyListener(this);
          final CompletionPhase phase = CompletionServiceImpl.getCompletionPhase();
          if (phase instanceof CompletionPhase.BgCalculation) {
            ((CompletionPhase.BgCalculation)phase).modifiersChanged = true;
          } else if (phase instanceof CompletionPhase.ZombiePhase) {
            CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
          }
        }
      }
    });
  }

  private void setMergeCommand() {
    CommandProcessor.getInstance().setCurrentCommandGroupId(getCompletionCommandName());
  }

  private String getCompletionCommandName() {
    return "Completion" + hashCode();
  }

  public void showLookup() {
    updateLookup();
  }

  public CompletionParameters getParameters() {
    return myParameters;
  }

  private void registerItself() {
    CompletionServiceImpl.getCompletionService().setCurrentCompletion(this);
  }

  public CodeCompletionHandlerBase getHandler() {
    return myHandler;
  }

  public LookupImpl getLookup() {
    return myLookup;
  }

  private void updateLookup() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (isOutdated()) return;

    if (!myLookup.isShown()) {
      if (hideAutopopupIfMeaningless()) {
        return;
      }

      if (StringUtil.isEmpty(myLookup.getAdvertisementText()) && !isAutopopupCompletion()) {
        final String text = DefaultCompletionContributor.getDefaultAdvertisementText(myParameters);
        if (text != null) {
          myLookup.setAdvertisementText(text);
        }
      }

      myLookup.show();
    }
    myLookup.refreshUi();
    hideAutopopupIfMeaningless();
    updateFocus();
  }

  final boolean isInsideIdentifier() {
    return getIdentifierEndOffset() != getSelectionEndOffset();
  }

  public int getIdentifierEndOffset() {
    return myOffsetMap.getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET);
  }


  public synchronized void addItem(final LookupElement item) {
    if (!isRunning()) return;
    ProgressManager.checkCanceled();

    final boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    if (!unitTestMode) {
      LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread());
    }

    myLookup.addItem(item);
    myCount++;
    if (unitTestMode) return;

    if (myCount == 1) {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          try {
            Thread.sleep(300);
          }
          catch (InterruptedException e) {
            LOG.error(e);
          }
          myFreezeSemaphore.up();
        }
      });
    }
    myQueue.queue(myUpdate);
  }

  public void closeAndFinish(boolean hideLookup) {
    final CompletionProgressIndicator current = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
    LOG.assertTrue(this == current, current);

    Lookup lookup = LookupManager.getActiveLookup(myEditor);
    LOG.assertTrue(lookup == myLookup, lookup);
    myLookup.removeLookupListener(myLookupListener);
    finishCompletionProcess();
    CompletionServiceImpl.assertPhase(CompletionPhase.NoCompletion.getClass());

    if (hideLookup) {
      LookupManager.getInstance(getProject()).hideActiveLookup();
    }
  }

  private void finishCompletionProcess() {
    cancel();

    ApplicationManager.getApplication().assertIsDispatchThread();
    Disposer.dispose(myQueue);

    CompletionProgressIndicator currentCompletion = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
    assert currentCompletion == this : currentCompletion + "!=" + this;
    CompletionServiceImpl.getCompletionService().setCurrentCompletion(null);

    CompletionServiceImpl.assertPhase(CompletionPhase.BgCalculation.class, CompletionPhase.ItemsCalculated.class, CompletionPhase.Synchronous.class, CompletionPhase.Restarted.class);
    CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
  }

  @TestOnly
  public static void cleanupForNextTest() {
    CompletionProgressIndicator currentCompletion = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
    if (currentCompletion != null) {
      currentCompletion.finishCompletionProcess();
    }
  }

  public void stop() {
    super.stop();

    myQueue.cancelAllUpdates();
    myFreezeSemaphore.up();

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final CompletionPhase phase = CompletionServiceImpl.getCompletionPhase();
        if (!(phase instanceof CompletionPhase.BgCalculation) || phase.indicator != CompletionProgressIndicator.this) return;

        myLookup.setCalculating(false);

        if (hideAutopopupIfMeaningless()) {
          return;
        }

        if (myCount == 0) {
          if (!isAutopopupCompletion()) {
            LookupManager.getInstance(getProject()).hideActiveLookup();

            final CompletionProgressIndicator current = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
            LOG.assertTrue(current == null, current + "!=" + CompletionProgressIndicator.this);

            handleEmptyLookup(!((CompletionPhase.BgCalculation)phase).modifiersChanged);
          }
        }
        else {
          CompletionServiceImpl.setCompletionPhase(new CompletionPhase.ItemsCalculated(CompletionProgressIndicator.this, ((CompletionPhase.BgCalculation)phase).focusLookupWhenDone));
          updateFocus();
          updateLookup();
        }
      }
    }, myQueue.getModalityState());
  }

  private boolean hideAutopopupIfMeaningless() {
    if (isAutopopupCompletion() && !myLookup.isSelectionTouched() && !myLookup.isCalculating()) {
      myLookup.refreshUi();
      for (LookupElement item : myLookup.getItems()) {
        if (!(item.getPrefixMatcher().getPrefix() + myLookup.getAdditionalPrefix()).equals(item.getLookupString())) {
          return false;
        }
      }

      myLookup.hideLookup(false);
      LOG.assertTrue(CompletionServiceImpl.getCompletionService().getCurrentCompletion() == null);
      CompletionServiceImpl.setCompletionPhase(new CompletionPhase.EmptyAutoPopup(this));
      return true;
    }
    return false;
  }

  private void updateFocus() {
    if (myLookup.isSelectionTouched()) {
      return;
    }

    if (!isAutopopupCompletion()) {
      myLookup.setFocused(true);
      return;
    }

    for (LookupElement item : myLookup.getItems()) {
      if ((item.getPrefixMatcher().getPrefix() + myLookup.getAdditionalPrefix()).equals(item.getLookupString())) {
        myLookup.setFocused(false);
        return;
      }
    }

    final CompletionPhase phase = CompletionServiceImpl.getCompletionPhase();
    myLookup.setFocused(phase instanceof CompletionPhase.ItemsCalculated && ((CompletionPhase.ItemsCalculated)phase).focusLookup);
  }

  public boolean fillInCommonPrefix(final boolean explicit) {
    if (isInsideIdentifier()) {
      return false;
    }

    final Boolean aBoolean = new WriteCommandAction<Boolean>(getProject()) {
      protected void run(Result<Boolean> result) throws Throwable {
        if (!explicit) {
          setMergeCommand();
        }
        try {
          result.setResult(myLookup.fillInCommonPrefix(explicit));
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }.execute().getResultObject();
    return aBoolean.booleanValue();
  }

  public void restorePrefix(final CompletionPhase zombie) {
    new WriteCommandAction(getProject(), getCompletionCommandName()) {
      @Override
      protected void run(Result result) throws Throwable {
        setMergeCommand();

        if (zombie instanceof CompletionPhase.InsertedSingleItem) {
          ((CompletionPhase.InsertedSingleItem)zombie).restorePrefix.run();
        }
        getLookup().restorePrefix();
      }
    }.execute();

  }

  public Editor getEditor() {
    return myEditor;
  }

  public boolean isRepeatedInvocation(CompletionType completionType, Editor editor) {
    if (completionType != myParameters.getCompletionType() || editor != myEditor) {
      return false;
    }

    if (isAutopopupCompletion() && !myLookup.mayBeNoticed()) {
      return false;
    }

    return true;
  }

  @Override
  public boolean isAutopopupCompletion() {
    return myHandler.autopopup;
  }

  @NotNull
  public Project getProject() {
    return ObjectUtils.assertNotNull(myEditor.getProject());
  }

  public void addWatchedPrefix(int startOffset, ElementPattern<String> restartCondition) {
    if (isAutopopupCompletion()) {
      myRestartingPrefixConditions.add(Pair.create(startOffset, restartCondition));
    }
  }

  public void prefixUpdated() {
    final CharSequence text = myEditor.getDocument().getCharsSequence();
    final int caretOffset = myEditor.getCaretModel().getOffset();
    for (Pair<Integer, ElementPattern<String>> pair : myRestartingPrefixConditions) {
      final String newPrefix = text.subSequence(pair.first, caretOffset).toString();
      if (pair.second.accepts(newPrefix)) {
        scheduleRestart();
        myRestartingPrefixConditions.clear();
        return;
      }
    }

    hideAutopopupIfMeaningless();
    updateFocus();
  }

  public void scheduleRestart() {
    cancel();

    ApplicationManager.getApplication().assertIsDispatchThread();
    final CompletionPhase phase = new CompletionPhase.Restarted(this);
    CompletionServiceImpl.setCompletionPhase(phase);

    final Project project = getProject();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (phase != CompletionServiceImpl.getCompletionPhase()) {
          return;
        }

        closeAndFinish(false);

        final CodeCompletionHandlerBase newHandler = new CodeCompletionHandlerBase(myParameters.getCompletionType(), false, isAutopopupCompletion());
        final PsiFile psiFileInEditor = PsiUtilBase.getPsiFileInEditor(myEditor, project);
        try {
          newHandler.invokeCompletion(project, myEditor, psiFileInEditor, myParameters.getInvocationCount());
        }
        catch (IndexNotReadyException ignored) {
        }
      }
    });
  }

  @Override
  public String toString() {
    return "CompletionProgressIndicator[count=" + myCount + ",phase=" + CompletionServiceImpl.getCompletionPhase() + "]";
  }

  protected void handleEmptyLookup(final boolean awaitSecondInvocation) {
    assert !isAutopopupCompletion();

    if (ApplicationManager.getApplication().isUnitTestMode() || !myHandler.invokedExplicitly) {
      CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
      return;
    }

    for (final CompletionContributor contributor : CompletionContributor.forParameters(getParameters())) {
      final String text = contributor.handleEmptyLookup(getParameters(), getEditor());
      if (StringUtil.isNotEmpty(text)) {
        LightweightHint hint = showErrorHint(getProject(), getEditor(), text);
        CompletionServiceImpl.setCompletionPhase(
          awaitSecondInvocation ? new CompletionPhase.NoSuggestionsHint(hint, this) : CompletionPhase.NoCompletion);
        return;
      }
    }
    CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
  }

  private static LightweightHint showErrorHint(Project project, Editor editor, String text) {
    final LightweightHint[] result = {null};
    final EditorHintListener listener = new EditorHintListener() {
      public void hintShown(final Project project, final LightweightHint hint, final int flags) {
        result[0] = hint;
      }
    };
    final MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(EditorHintListener.TOPIC, listener);
    assert text != null;
    HintManager.getInstance().showErrorHint(editor, text);
    connection.disconnect();
    return result[0];
  }
}
