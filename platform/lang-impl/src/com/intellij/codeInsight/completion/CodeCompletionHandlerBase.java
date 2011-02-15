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

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.reference.SoftReference;
import com.intellij.util.Consumer;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class CodeCompletionHandlerBase implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CodeCompletionHandlerBase");
  private final CompletionType myCompletionType;
  final boolean invokedExplicitly;
  final boolean autopopup;

  public CodeCompletionHandlerBase(final CompletionType completionType) {
    this(completionType, true, false);
  }

  public CodeCompletionHandlerBase(CompletionType completionType, boolean invokedExplicitly, boolean autopopup) {
    myCompletionType = completionType;
    this.invokedExplicitly = invokedExplicitly;
    this.autopopup = autopopup;
  }

  public final void invoke(final Project project, final Editor editor) {
    invoke(project, editor, PsiUtilBase.getPsiFileInEditor(editor, project));
  }

  public final void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull PsiFile psiFile) {
    if (editor.isViewer()) {
      editor.getDocument().fireReadOnlyModificationAttempt();
      return;
    }

    try {
      invokeCompletion(project, editor, psiFile, autopopup ? 0 : 1);
    }
    catch (IndexNotReadyException e) {
      DumbService.getInstance(project).showDumbModeNotification("Code completion is not available here while indices are being built");
    }
  }

  public void invokeCompletion(final Project project, final Editor editor, final PsiFile psiFile, int time) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      assert !ApplicationManager.getApplication().isWriteAccessAllowed() : "Completion should not be invoked inside write action";
    }

    final Document document = editor.getDocument();
    if (!FileDocumentManager.getInstance().requestWriting(document, project)) {
      return;
    }

    psiFile.putUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING, Boolean.TRUE);

    CompletionPhase phase = CompletionServiceImpl.getCompletionPhase();
    CompletionProgressIndicator indicator = phase.newCompletionStarted();

    CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);

    if (indicator != null) {
      boolean repeated = indicator.isRepeatedInvocation(myCompletionType, editor);
      if (repeated && !indicator.isRunning() && (!isAutocompleteCommonPrefixOnInvocation() || indicator.fillInCommonPrefix(true))) {
        return;
      }

      if (repeated) {
        time = Math.max(indicator.getParameters().getInvocationCount() + 1, 2);
        indicator.restorePrefix(phase);
      }
    }

    if (time > 1) {
      if (myCompletionType == CompletionType.CLASS_NAME) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.SECOND_CLASS_NAME_COMPLETION);
      }
      else if (myCompletionType == CompletionType.BASIC) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.SECOND_BASIC_COMPLETION);
      }
    }

    final CompletionInitializationContext[] initializationContext = {null};


    Runnable initCmd = new Runnable() {
      @Override
      public void run() {

        Runnable runnable = new Runnable() {
          public void run() {
            final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);

            EditorUtil.fillVirtualSpaceUntilCaret(editor);
            documentManager.commitAllDocuments();

            if (editor.getDocument().getTextLength() != psiFile.getTextLength()) {
              if (ApplicationManagerEx.getApplicationEx().isInternal()) {
                throw new AssertionError("unsuccessful commit: docText=" + editor.getDocument().getText() + "; fileText=" + psiFile.getText() + "; injected=" + (editor instanceof EditorWindow));
              }

              throw new AssertionError("unsuccessful commit: injected=" + (editor instanceof EditorWindow));
            }

            final Ref<CompletionContributor> current = Ref.create(null);
            initializationContext[0] = new CompletionInitializationContext(editor, psiFile, myCompletionType) {
              CompletionContributor dummyIdentifierChanger;
              @Override
              public void setFileCopyPatcher(@NotNull FileCopyPatcher fileCopyPatcher) {
                super.setFileCopyPatcher(fileCopyPatcher);

                if (dummyIdentifierChanger != null) {
                  LOG.error("Changing the dummy identifier twice, already changed by " + dummyIdentifierChanger);
                }
                dummyIdentifierChanger = current.get();
              }
            };
            for (final CompletionContributor contributor : CompletionContributor.forLanguage(initializationContext[0].getPositionLanguage())) {
              if (DumbService.getInstance(project).isDumb() && !DumbService.isDumbAware(contributor)) {
                continue;
              }

              current.set(contributor);
              contributor.beforeCompletion(initializationContext[0]);
              assert !documentManager.isUncommited(document) : "Contributor " + contributor + " left the document uncommitted";
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(runnable);
      }
    };
    if (autopopup) {
      CommandProcessor.getInstance().runUndoTransparentAction(initCmd);

      int offset = editor.getCaretModel().getOffset();

      assert offset > 0;
      PsiElement elementAt = InjectedLanguageUtil.findInjectedElementNoCommit(psiFile, offset - 1);
      if (elementAt == null) {
        elementAt = psiFile.findElementAt(offset - 1);
      }

      Language language = elementAt != null ? PsiUtilBase.findLanguageFromElement(elementAt):psiFile.getLanguage();

      for (CompletionConfidence confidence : CompletionConfidenceEP.forLanguage(language)) {
        final ThreeState result = confidence.shouldSkipAutopopup(elementAt, psiFile, offset); // TODO: Peter Lazy API
        if (result == ThreeState.YES) return;
        if (result == ThreeState.NO) break;
      }
    } else {
      CommandProcessor.getInstance().executeCommand(project, initCmd, null, null);
    }

    doComplete(time, initializationContext[0]);
  }

  private boolean shouldFocusLookup(CompletionParameters parameters) {
    if (!autopopup) {
      return true;
    }

    switch (CodeInsightSettings.getInstance().AUTOPOPUP_FOCUS_POLICY) {
      case CodeInsightSettings.ALWAYS: return true;
      case CodeInsightSettings.NEVER: return false;
    }

    final Language language = PsiUtilBase.getLanguageAtOffset(parameters.getPosition().getContainingFile(), parameters.getOffset());
    for (CompletionConfidence confidence : CompletionConfidenceEP.forLanguage(language)) {
      final ThreeState result = confidence.shouldFocusLookup(parameters);
      if (result != ThreeState.UNSURE) {
        return result == ThreeState.YES;
      }
    }
    return false;
  }

  @NotNull
  private LookupImpl obtainLookup(Editor editor) {
    LookupImpl existing = (LookupImpl)LookupManager.getActiveLookup(editor);
    if (existing != null && existing.isCompletion()) {
      existing.markReused();
      if (!autopopup) {
        existing.setFocused(true);
        existing.setHintMode(false);
      }
      return existing;
    }

    LookupImpl lookup = (LookupImpl)LookupManager.getInstance(editor.getProject()).createLookup(editor, LookupElement.EMPTY_ARRAY, "", LookupArranger.DEFAULT);
    if (editor.isOneLineMode()) {
      lookup.setCancelOnClickOutside(true);
      lookup.setCancelOnOtherWindowOpen(true);
      lookup.setResizable(false);
      lookup.setForceLightweightPopup(false);
    }
    lookup.setFocused(!autopopup);
    return lookup;
  }

  private void doComplete(final int invocationCount, CompletionInitializationContext initContext) {
    final Editor editor = initContext.getEditor();

    final CompletionParameters parameters = createCompletionParameters(invocationCount, initContext);

    final LookupImpl lookup = obtainLookup(editor);

    final Semaphore freezeSemaphore = new Semaphore();
    freezeSemaphore.down();
    final CompletionProgressIndicator indicator = new CompletionProgressIndicator(editor, parameters, this, freezeSemaphore,
                                                                                  initContext.getOffsetMap(), lookup);

    boolean sync =
      (invokedExplicitly || ApplicationManager.getApplication().isUnitTestMode()) && !CompletionAutoPopupHandler.ourTestingAutopopup;

    CompletionServiceImpl.setCompletionPhase(sync ? new CompletionPhase.Synchronous(indicator) : new CompletionPhase.BgCalculation(indicator));

    final AtomicReference<LookupElement[]> data = startCompletionThread(parameters, indicator, initContext);

    if (!sync) {
      return;
    }

    if (freezeSemaphore.waitFor(2000)) {
      final LookupElement[] allItems = data.get();
      if (allItems != null) { // the completion is really finished, now we may auto-insert or show lookup
        completionFinished(initContext.getStartOffset(), initContext.getSelectionEndOffset(), indicator, allItems);
        return;
      }
    }

    CompletionServiceImpl.setCompletionPhase(new CompletionPhase.BgCalculation(indicator));
    indicator.showLookup();
  }

  private AtomicReference<LookupElement[]> startCompletionThread(final CompletionParameters parameters,
                                                                        final CompletionProgressIndicator indicator,
                                                                        final CompletionInitializationContext initContext) {

    final ApplicationAdapter listener = new ApplicationAdapter() {
      @Override
      public void beforeWriteActionStart(Object action) {
        indicator.scheduleRestart();
      }
    };
    ApplicationManager.getApplication().addApplicationListener(listener);

    final Semaphore startSemaphore = new Semaphore();
    startSemaphore.down();
    startSemaphore.down();

    spawnProcess(ProgressWrapper.wrap(indicator), new Runnable() {
      public void run() {
        try {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              startSemaphore.up();
              if (autopopup) {
                indicator.setFocusLookupWhenDone(shouldFocusLookup(parameters));
              }
              indicator.duringCompletion(initContext);
            }
          });
        }
        finally {
          indicator.duringCompletionPassed();
        }
      }
    });

    final AtomicReference<LookupElement[]> data = new AtomicReference<LookupElement[]>(null);
    spawnProcess(indicator, new Runnable() {
      public void run() {
        try {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              try {
                startSemaphore.up();
                ProgressManager.checkCanceled();

                final LookupElement[] result = CompletionService.getCompletionService().performCompletion(parameters, new Consumer<LookupElement>() {
                    public void consume(final LookupElement lookupElement) {
                      indicator.addItem(lookupElement);
                    }
                  });

                indicator.ensureDuringCompletionPassed();

                data.set(result);
              }
              finally {
                ApplicationManager.getApplication().removeApplicationListener(listener);
              }
            }
          });
        }
        catch (ProcessCanceledException ignored) {
        }
      }
    });

    startSemaphore.waitFor();
    return data;
  }



  private static void spawnProcess(final ProgressIndicator indicator, final Runnable process) {
    final Runnable computeRunnable = new Runnable() {
      public void run() {
        ProgressManager.getInstance().runProcess(process, indicator);
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode() && !CompletionAutoPopupHandler.ourTestingAutopopup) {
      computeRunnable.run();
    } else {
      ApplicationManager.getApplication().executeOnPooledThread(computeRunnable);
    }
  }

  private CompletionParameters createCompletionParameters(int invocationCount, final CompletionInitializationContext initContext) {
    final Ref<CompletionContext> ref = Ref.create(null);
    CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            ref.set(insertDummyIdentifier(initContext));
          }
        });
      }
    });
    final CompletionContext newContext = ref.get();

    final int offset = newContext.getStartOffset();
    final PsiFile fileCopy = newContext.file;
    final PsiElement insertedElement = newContext.file.findElementAt(newContext.getStartOffset());
    if (insertedElement == null) {
      throw new AssertionError("offset " + newContext.getStartOffset() + " at:\n text=\"" + fileCopy.getText() + "\"\n instance=" + fileCopy);
    }
    insertedElement.putUserData(CompletionContext.COMPLETION_CONTEXT_KEY, newContext);

    LOG.assertTrue(fileCopy.findElementAt(offset) == insertedElement, "wrong offset");

    final TextRange range = insertedElement.getTextRange();
    if (!range.substring(fileCopy.getText()).equals(insertedElement.getText())) {
      LOG.error("wrong text: copy='" + fileCopy.getText() + "'; element='" + insertedElement.getText() + "'");
    }

    return new CompletionParameters(insertedElement, fileCopy.getOriginalFile(), myCompletionType, offset, invocationCount);
  }

  private AutoCompletionDecision shouldAutoComplete(
    final CompletionProgressIndicator indicator,
    final LookupElement[] items) {
    if (!invokedExplicitly) {
      return AutoCompletionDecision.SHOW_LOOKUP;
    }
    final CompletionParameters parameters = indicator.getParameters();
    final LookupElement item = items[0];
    if (items.length == 1) {
      final AutoCompletionPolicy policy = getAutocompletionPolicy(item);
      if (policy == AutoCompletionPolicy.NEVER_AUTOCOMPLETE) return AutoCompletionDecision.SHOW_LOOKUP;
      if (policy == AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE) return AutoCompletionDecision.insertItem(item);
    }
    if (!isAutocompleteOnInvocation(parameters.getCompletionType())) {
      return AutoCompletionDecision.SHOW_LOOKUP;
    }
    if (isInsideIdentifier(indicator.getOffsetMap())) {
      return AutoCompletionDecision.SHOW_LOOKUP;
    }
    if (items.length == 1 && getAutocompletionPolicy(item) == AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE) {
      return AutoCompletionDecision.insertItem(item);
    }

    for (final CompletionContributor contributor : CompletionContributor.forParameters(parameters)) {
      final AutoCompletionDecision decision = contributor.handleAutoCompletionPossibility(new AutoCompletionContext(parameters, items, indicator.getOffsetMap()));
      if (decision != null) {
        return decision;
      }
    }

    return AutoCompletionDecision.SHOW_LOOKUP;
  }

  @Nullable
  private static AutoCompletionPolicy getAutocompletionPolicy(LookupElement element) {
    final AutoCompletionPolicy policy = AutoCompletionPolicy.getPolicy(element);
    if (policy != null) {
      return policy;
    }

    final LookupItem item = element.as(LookupItem.CLASS_CONDITION_KEY);
    if (item != null) {
      return item.getAutoCompletionPolicy();
    }

    return null;
  }

  private static boolean isInsideIdentifier(final OffsetMap offsetMap) {
    return offsetMap.getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET) != offsetMap.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);
  }


  protected void completionFinished(final int offset1,
                                    final int offset2,
                                    final CompletionProgressIndicator indicator,
                                    final LookupElement[] items) {
    if (items.length == 0) {
      LookupManager.getInstance(indicator.getProject()).hideActiveLookup();
      indicator.handleEmptyLookup(true);
      return;
    }

    final AutoCompletionDecision decision = shouldAutoComplete(indicator, items);
    if (decision == AutoCompletionDecision.SHOW_LOOKUP) {
      CompletionServiceImpl.setCompletionPhase(new CompletionPhase.ItemsCalculated(indicator, true));
      indicator.getLookup().setCalculating(false);
      indicator.showLookup();
      if (isAutocompleteCommonPrefixOnInvocation() && items.length > 1) {
        indicator.fillInCommonPrefix(false);
      }
    }
    else if (decision instanceof AutoCompletionDecision.InsertItem) {
      final LookupElement item = ((AutoCompletionDecision.InsertItem)decision).getElement();
      indicator.closeAndFinish(true);
      final Runnable restorePrefix = rememberDocumentState(indicator.getEditor());
      indicator.getOffsetMap()
        .addOffset(CompletionInitializationContext.START_OFFSET, (offset1 - item.getPrefixMatcher().getPrefix().length()));
      handleSingleItem(offset2, indicator, items, item.getLookupString(), item);

      // the insert handler may have started a live template with completion
      if (CompletionService.getCompletionService().getCurrentCompletion() == null &&
          !ApplicationManager.getApplication().isUnitTestMode()) {
        CompletionServiceImpl.setCompletionPhase(new CompletionPhase.InsertedSingleItem(indicator, restorePrefix));
      }
    }
  }

  protected static void handleSingleItem(final int offset2, final CompletionProgressIndicator context, final LookupElement[] items, final String _uniqueText, final LookupElement item) {
    new WriteCommandAction(context.getProject()) {
      protected void run(Result result) throws Throwable {
        String uniqueText = _uniqueText;

        if (item.getObject() instanceof DeferredUserLookupValue && item.as(LookupItem.CLASS_CONDITION_KEY) != null) {
          if (!((DeferredUserLookupValue)item.getObject()).handleUserSelection(item.as(LookupItem.CLASS_CONDITION_KEY), context.getProject())) {
            return;
          }

          uniqueText = item.getLookupString(); // text may be not ready yet
        }

        if (!StringUtil.startsWithIgnoreCase(uniqueText, item.getPrefixMatcher().getPrefix())) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_CAMEL_HUMPS);
        }

        insertLookupString(offset2, uniqueText, context.getEditor(), context.getOffsetMap());
        context.getEditor().getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

        lookupItemSelected(context, item, Lookup.AUTO_INSERT_SELECT_CHAR, Arrays.asList(items));
      }
    }.execute();
  }

  private static void insertLookupString(final int currentOffset, final String newText, final Editor editor, final OffsetMap offsetMap) {
    editor.getDocument().replaceString(offsetMap.getOffset(CompletionInitializationContext.START_OFFSET), currentOffset, newText);
    editor.getCaretModel().moveToOffset(offsetMap.getOffset(CompletionInitializationContext.START_OFFSET) + newText.length());
    editor.getSelectionModel().removeSelection();
  }

  protected static void selectLookupItem(final LookupElement item, final char completionChar, final CompletionProgressIndicator context, final List<LookupElement> items) {
    final int caretOffset = context.getEditor().getCaretModel().getOffset();

    context.getOffsetMap().addOffset(CompletionInitializationContext.SELECTION_END_OFFSET, caretOffset);
    final int idEnd = context.getIdentifierEndOffset();
    final int identifierEndOffset =
      CompletionUtil.isOverwrite(item, completionChar) && context.getSelectionEndOffset() == idEnd ?
      caretOffset :
      Math.max(caretOffset, idEnd);
    context.getOffsetMap().addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, identifierEndOffset);
    lookupItemSelected(context, item, completionChar, items);
  }

  private CompletionContext insertDummyIdentifier(CompletionInitializationContext initContext) {
    final PsiFile originalFile = initContext.getFile();
    PsiFile fileCopy = createFileCopy(originalFile);
    PsiFile hostFile = InjectedLanguageUtil.getTopLevelFile(fileCopy);
    final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(hostFile.getProject());
    int hostStartOffset = injectedLanguageManager.injectedToHost(fileCopy, initContext.getStartOffset());
    final Editor hostEditor = InjectedLanguageUtil.getTopLevelEditor(initContext.getEditor());

    final OffsetMap hostMap = new OffsetMap(hostEditor.getDocument());
    final OffsetMap original = initContext.getOffsetMap();
    for (final OffsetKey key : original.keySet()) {
      hostMap.addOffset(key, injectedLanguageManager.injectedToHost(fileCopy, original.getOffset(key)));
    }

    Document document = fileCopy.getViewProvider().getDocument();
    assert document != null : "no document";
    initContext.getFileCopyPatcher().patchFileCopy(fileCopy, document, initContext.getOffsetMap());
    final Document hostDocument = hostFile.getViewProvider().getDocument();
    assert hostDocument != null : "no host document";
    PsiDocumentManager.getInstance(hostFile.getProject()).commitDocument(hostDocument);
    assert hostFile.isValid() : "file became invalid";
    assert hostMap.getOffset(CompletionInitializationContext.START_OFFSET) < hostFile.getTextLength() : "startOffset outside the host file";

    CompletionContext context;
    PsiFile injected = InjectedLanguageUtil.findInjectedPsiNoCommit(hostFile, hostStartOffset);
    if (injected != null) {
      assert hostStartOffset >= injectedLanguageManager.injectedToHost(injected, 0) : "startOffset before injected";
      assert hostStartOffset <= injectedLanguageManager.injectedToHost(injected, injected.getTextLength()) : "startOffset after injected";

      EditorWindow injectedEditor = (EditorWindow)InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(hostEditor, hostFile, hostStartOffset);
      assert injected == injectedEditor.getInjectedFile();
      final OffsetMap map = new OffsetMap(injectedEditor.getDocument());
      for (final OffsetKey key : hostMap.keySet()) {
        map.addOffset(key, injectedEditor.logicalPositionToOffset(injectedEditor.hostToInjected(hostEditor.offsetToLogicalPosition(hostMap.getOffset(key)))));
      }
      context = new CompletionContext(initContext.getProject(), injectedEditor, injected, map);
      assert hostStartOffset == injectedLanguageManager.injectedToHost(injected, context.getStartOffset()) : "inconsistent injected offset translation";
    } else {
      context = new CompletionContext(initContext.getProject(), hostEditor, hostFile, hostMap);
    }

    assert context.getStartOffset() < context.file.getTextLength() : "start outside the file";
    assert context.getStartOffset() >= 0 : "start < 0";

    return context;
  }

  private boolean isAutocompleteCommonPrefixOnInvocation() {
    return invokedExplicitly && CodeInsightSettings.getInstance().AUTOCOMPLETE_COMMON_PREFIX;
  }

  private static void lookupItemSelected(final CompletionProgressIndicator indicator, @NotNull final LookupElement item, final char completionChar,
                                         final List<LookupElement> items) {
    if (indicator.getHandler().autopopup) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_BASIC);
    }

    final Editor editor = indicator.getEditor();
    final PsiFile file = indicator.getParameters().getOriginalFile();
    final InsertionContext context = new InsertionContext(indicator.getOffsetMap(), completionChar, items.toArray(new LookupElement[items.size()]), file, editor);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final int idEndOffset = indicator.getIdentifierEndOffset();
        if (idEndOffset != indicator.getSelectionEndOffset() && CompletionUtil.isOverwrite(item, completionChar)) {
          editor.getDocument().deleteString(indicator.getSelectionEndOffset(), idEndOffset);
        }

        assert context.getStartOffset() >= 0 : "stale startOffset";
        assert context.getTailOffset() >= 0 : "stale tailOffset";

        PsiDocumentManager.getInstance(indicator.getProject()).commitAllDocuments();
        item.handleInsert(context);
        PostprocessReformattingAspect.getInstance(indicator.getProject()).doPostponedFormatting();


        final int tailOffset = context.getTailOffset();
        if (tailOffset >= 0) {
          if (context.shouldAddCompletionChar() &&
              completionChar != Lookup.AUTO_INSERT_SELECT_CHAR && completionChar != Lookup.REPLACE_SELECT_CHAR &&
              completionChar != Lookup.NORMAL_SELECT_CHAR && completionChar != Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
            TailType.insertChar(editor, tailOffset, completionChar);
          }
        }
        else {
          LOG.error("tailOffset<0 after inserting " + item + " of " + item.getClass());
        }
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    });
    final Runnable runnable = context.getLaterRunnable();
    if (runnable != null) {
      final Runnable runnable1 = new Runnable() {
        public void run() {
          final Project project = context.getProject();
          if (project.isDisposed()) return;
          runnable.run();
        }
      };
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        ApplicationManager.getApplication().invokeLater(runnable1);
      } else {
        runnable1.run();
      }
    }
  }

  public boolean startInWriteAction() {
    return false;
  }

  public static final Key<SoftReference<Pair<PsiFile, Document>>> FILE_COPY_KEY = Key.create("CompletionFileCopy");

  protected PsiFile createFileCopy(PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (file.isPhysical() && virtualFile != null && virtualFile.getFileSystem() == LocalFileSystem.getInstance()
        // must not cache injected file copy, since it does not reflect changes in host document
        && !InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) {
      final SoftReference<Pair<PsiFile, Document>> reference = file.getUserData(FILE_COPY_KEY);
      if (reference != null) {
        final Pair<PsiFile, Document> pair = reference.get();
        if (pair != null && pair.first.isValid() && pair.first.getClass().equals(file.getClass())) {
          final PsiFile copy = pair.first;
          final Document document = pair.second;
          assert document != null;
          final String oldDocumentText = document.getText();
          final String oldCopyText = copy.getText();
          final String newText = file.getText();
          document.setText(newText);
          try {
            PsiDocumentManager.getInstance(copy.getProject()).commitDocument(document);
            return copy;
          }
          catch (Throwable e) {
            document.setText("");
            if (((ApplicationEx)ApplicationManager.getApplication()).isInternal()) {
              final StringBuilder sb = new StringBuilder();
              boolean oldsAreSame = Comparing.equal(oldCopyText, oldDocumentText);
              if (oldsAreSame) {
                sb.append("oldCopyText == oldDocumentText");
              }
              else {
                sb.append("oldCopyText != oldDocumentText");
                sb.append("\n--- oldCopyText ------------------------------------------------\n").append(oldCopyText);
                sb.append("\n--- oldDocumentText ------------------------------------------------\n").append(oldDocumentText);
              }
              if (Comparing.equal(oldCopyText, newText)) {
                sb.insert(0, "newText == oldCopyText; ");
              }
              else if (!oldsAreSame && Comparing.equal(oldDocumentText, newText)) {
                sb.insert(0, "newText == oldDocumentText; ");
              }
              else {
                sb.insert(0, "newText != oldCopyText, oldDocumentText; ");
                if (oldsAreSame) {
                  sb.append("\n--- oldCopyText ------------------------------------------------\n").append(oldCopyText);
                }
                sb.append("\n--- newText ------------------------------------------------\n").append(newText);
              }
              LOG.error(sb.toString(), e);
            }
          }
        }
      }
    }

    final PsiFile copy = (PsiFile)file.copy();
    final Document document = copy.getViewProvider().getDocument();
    assert document != null;
    file.putUserData(FILE_COPY_KEY, new SoftReference<Pair<PsiFile,Document>>(Pair.create(copy, document)));
    return copy;
  }

  private static boolean isAutocompleteOnInvocation(final CompletionType type) {
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    switch (type) {
      case CLASS_NAME: return settings.AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION;
      case SMART: return settings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION;
      case BASIC:
      default: return settings.AUTOCOMPLETE_ON_CODE_COMPLETION;
    }
  }

  private static Runnable rememberDocumentState(final Editor editor) {
    final String documentText = editor.getDocument().getText();
    final int caret = editor.getCaretModel().getOffset();
    final int selStart = editor.getSelectionModel().getSelectionStart();
    final int selEnd = editor.getSelectionModel().getSelectionEnd();

    return new Runnable() {
      @Override
      public void run() {
        DocumentEx document = (DocumentEx) editor.getDocument();

        // restore the text in two steps, because otherwise the dumb caret model will scroll the editor
        document.replaceString(0, editor.getCaretModel().getOffset(), documentText.substring(0, caret));
        document.replaceString(caret, document.getTextLength(), documentText.substring(caret));
        editor.getSelectionModel().setSelection(selStart, selEnd);
      }
    };
  }
}
