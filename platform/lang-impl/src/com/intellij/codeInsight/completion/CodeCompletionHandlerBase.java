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
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.extapi.psi.MetadataPsiElementBase;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.reference.SoftReference;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class CodeCompletionHandlerBase implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CodeCompletionHandlerBase");
  private final CompletionType myCompletionType;
  final boolean invokedExplicitly;
  final boolean focusLookup;

  public CodeCompletionHandlerBase(final CompletionType completionType) {
    this(completionType, true, true);
  }

  public CodeCompletionHandlerBase(CompletionType completionType, boolean invokedExplicitly, boolean focusLookup) {
    myCompletionType = completionType;
    this.invokedExplicitly = invokedExplicitly;
    this.focusLookup = focusLookup;
  }

  public final void invoke(final Project project, final Editor editor) {
    invoke(project, editor, PsiUtilBase.getPsiFileInEditor(editor, project));
  }

  public final void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull PsiFile psiFile) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      assert !ApplicationManager.getApplication().isWriteAccessAllowed() : "Please don't invoke completion inside write action";
    }

    try {
      invokeCompletion(project, editor, psiFile, focusLookup ? 1 : 0);
    }
    catch (IndexNotReadyException e) {
      DumbService.getInstance(project).showDumbModeNotification("Code completion is not available here while indices are being built");
    }
  }

  public void invokeCompletion(final Project project, final Editor editor, final PsiFile psiFile, int time) {
    final Document document = editor.getDocument();
    if (editor.isViewer()) {
      document.fireReadOnlyModificationAttempt();
      return;
    }
    if (!FileDocumentManager.getInstance().requestWriting(document, project)) {
      return;
    }

    psiFile.putUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING, Boolean.TRUE);

    final CompletionProgressIndicator indicator = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
    if (indicator != null) {
      if (indicator.isRepeatedInvocation(myCompletionType, editor)) {
        if (!indicator.isRunning() && (!isAutocompleteCommonPrefixOnInvocation() || indicator.fillInCommonPrefix(true))) {
          return;
        }
        else {
          time = indicator.getParameters().getInvocationCount() + 1;
          new WriteCommandAction(project) {
            protected void run(Result result) throws Throwable {
              indicator.restorePrefix();
            }
          }.execute();
        }
      }
      indicator.closeAndFinish();
    }

    if (time != 1) {
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
            initializationContext[0] = new CompletionInitializationContext(editor, psiFile, myCompletionType);
            for (final CompletionContributor contributor : CompletionContributor.forLanguage(PsiUtilBase.getLanguageInEditor(editor, project))) {
              if (DumbService.getInstance(project).isDumb() && !DumbService.isDumbAware(contributor)) {
                continue;
              }

              contributor.beforeCompletion(initializationContext[0]);
              assert !documentManager.isUncommited(document) : "Contributor " + contributor + " left the document uncommitted";
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(runnable);
      }
    };
    if (!focusLookup) {
      CommandProcessor.getInstance().runUndoTransparentAction(initCmd);
    } else {
      CommandProcessor.getInstance().executeCommand(project, initCmd, null, null);
    }

    final int offset1 = initializationContext[0].getStartOffset();
    final int offset2 = initializationContext[0].getSelectionEndOffset();
    final CompletionContext context = new CompletionContext(project, editor, psiFile, initializationContext[0].getOffsetMap());

    doComplete(offset1, offset2, context, initializationContext[0].getFileCopyPatcher(), editor, time);
  }

  protected void doComplete(final int offset1,
                            final int offset2,
                            final CompletionContext context,
                            final FileCopyPatcher patcher, final Editor editor, final int invocationCount) {
    final CompletionParameters parameters = createCompletionParameters(context, patcher, invocationCount);

    final Semaphore freezeSemaphore = new Semaphore();
    freezeSemaphore.down();
    final CompletionProgressIndicator indicator = new CompletionProgressIndicator(editor, parameters, this, freezeSemaphore, context.getOffsetMap());

    final AtomicReference<LookupElement[]> data = startCompletionThread(parameters, indicator);

    if (!invokedExplicitly) {
      indicator.notifyBackgrounded();
      return;
    }

    if (freezeSemaphore.waitFor(2000)) {
      final LookupElement[] allItems = data.get();
      if (allItems != null) { // the completion is really finished, now we may auto-insert or show lookup
        completionFinished(offset1, offset2, indicator, allItems);
        return;
      }
    }

    indicator.notifyBackgrounded();
    indicator.showLookup();
  }

  private AtomicReference<LookupElement[]> startCompletionThread(final CompletionParameters parameters, final CompletionProgressIndicator indicator) {
    final AtomicReference<LookupElement[]> data = new AtomicReference<LookupElement[]>(null);
    final Semaphore startSemaphore = new Semaphore();
    startSemaphore.down();
    final Runnable computeRunnable = new Runnable() {
      public void run() {
        ProgressManager.getInstance().runProcess(new Runnable() {
          public void run() {
            try {
              startSemaphore.up();
              data.set(CompletionService.getCompletionService().performCompletion(parameters, new Consumer<LookupElement>() {
                public void consume(final LookupElement lookupElement) {
                  indicator.addItem(lookupElement);
                }
              }));
            }
            catch (PsiInvalidElementAccessException e) {
              throw new RuntimeException("PIEAE: canceled=" + indicator.isCanceled() + "; trace=" + indicator.cancelTrace, e);
            }
            catch (ProcessCanceledException ignored) {
            }
          }
        }, indicator);
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      computeRunnable.run();
    } else {
      ApplicationManager.getApplication().executeOnPooledThread(computeRunnable);
    }

    startSemaphore.waitFor();
    return data;
  }

  private CompletionParameters createCompletionParameters(final CompletionContext context, final FileCopyPatcher patcher, int invocationCount) {
    final Ref<Pair<CompletionContext, PsiElement>> ref = Ref.create(null);
    CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            ref.set(insertDummyIdentifier(context, patcher, context.file, context.editor));
          }
        });
      }
    });

    final PsiElement insertedElement = ref.get().getSecond();
    final CompletionContext newContext = ref.get().getFirst();
    insertedElement.putUserData(CompletionContext.COMPLETION_CONTEXT_KEY, newContext);

    return new CompletionParameters(insertedElement, newContext.file, myCompletionType, newContext.getStartOffset(), invocationCount);
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

    final LookupItem item = element.as(LookupItem.class);
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
      handleEmptyLookup(indicator.getProject(), indicator.getEditor(), indicator.getParameters(), indicator);
      return;
    }

    final AutoCompletionDecision decision = shouldAutoComplete(indicator, items);
    if (decision == AutoCompletionDecision.SHOW_LOOKUP) {
      indicator.showLookup();
      if (isAutocompleteCommonPrefixOnInvocation() && items.length > 1) {
        indicator.fillInCommonPrefix(false);
      }
    } else if (decision instanceof AutoCompletionDecision.InsertItem) {
      final LookupElement item = ((AutoCompletionDecision.InsertItem)decision).getElement();
      indicator.closeAndFinish();
      indicator.rememberDocumentState();
      indicator.getOffsetMap().addOffset(CompletionInitializationContext.START_OFFSET, (offset1 - item.getPrefixMatcher().getPrefix().length()));
      handleSingleItem(offset2, indicator, items, item.getLookupString(), item);

      // the insert handler may have started a live template with completion
      if (CompletionService.getCompletionService() == null) {
        indicator.liveAfterDeath(null);
      }
    }
  }

  protected static void handleSingleItem(final int offset2, final CompletionProgressIndicator context, final LookupElement[] items, final String _uniqueText, final LookupElement item) {
    new WriteCommandAction(context.getProject()) {
      protected void run(Result result) throws Throwable {
        String uniqueText = _uniqueText;

        if (item.getObject() instanceof DeferredUserLookupValue && item.as(LookupItem.class) != null) {
          if (!((DeferredUserLookupValue)item.getObject()).handleUserSelection(item.as(LookupItem.class), context.getProject())) {
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

  private Pair<CompletionContext, PsiElement> insertDummyIdentifier(final CompletionContext context,
                                                                    final FileCopyPatcher patcher,
                                                                    final PsiFile file, final Editor originalEditor) {
    PsiFile oldFileCopy = createFileCopy(file);
    PsiFile hostFile = InjectedLanguageUtil.getTopLevelFile(oldFileCopy);
    boolean wasInjected = hostFile != oldFileCopy;
    Project project = hostFile.getProject();
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(project);
    // is null in tests
    int hostStartOffset = injectedLanguageManager == null
                          ? context.getStartOffset()
                          : injectedLanguageManager.injectedToHost(oldFileCopy, context.getStartOffset());

    Document document = oldFileCopy.getViewProvider().getDocument();
    assert document != null;
    patcher.patchFileCopy(oldFileCopy, document, context.getOffsetMap());
    PsiDocumentManager.getInstance(project).commitDocument(document);
    PsiFile fileCopy = InjectedLanguageUtil.findInjectedPsiNoCommit(hostFile, hostStartOffset);
    if (fileCopy == null) {
      PsiElement elementAfterCommit = findElementAt(hostFile, hostStartOffset);
      if (wasInjected) {
        LOG.error("No injected fragment found at offset " + hostStartOffset + " in the patched file copy, found: " + elementAfterCommit);
      }
      fileCopy = elementAfterCommit == null ? oldFileCopy : elementAfterCommit.getContainingFile();
    }

    if (oldFileCopy != fileCopy && !wasInjected) {
      // newly inserted identifier can well end up in the injected language region
      Editor editor = EditorFactory.getInstance().createEditor(document, project);
      Editor newEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, hostFile, context.getStartOffset());
      if (newEditor instanceof EditorWindow) {
        EditorWindow injectedEditor = (EditorWindow)newEditor;
        PsiFile injectedFile = injectedEditor.getInjectedFile();
        final OffsetMap map = new OffsetMap(newEditor.getDocument());
        final OffsetMap oldMap = context.getOffsetMap();
        for (final OffsetKey key : oldMap.keySet()) {
          map.addOffset(key, injectedEditor.logicalPositionToOffset(injectedEditor.hostToInjected(originalEditor.offsetToLogicalPosition(oldMap.getOffset(key)))));
        }
        CompletionContext newContext = new CompletionContext(file.getProject(), injectedEditor, injectedFile, map);
        int injectedOffset = newContext.getStartOffset();
        PsiElement element = findElementAt(injectedFile, injectedOffset);

        int toHost = injectedLanguageManager == null ? hostStartOffset : injectedLanguageManager.injectedToHost(injectedFile, injectedOffset);
        // maybe injected fragment is ended before hostStartOffset
        if (element != null && toHost == hostStartOffset) {
          EditorFactory.getInstance().releaseEditor(editor);
          return Pair.create(newContext, element);
        }
        else {
          PsiElement elementAfterCommit = findElementAt(hostFile, hostStartOffset);
          fileCopy = elementAfterCommit == null ? oldFileCopy : elementAfterCommit.getContainingFile();
        }
      }
      EditorFactory.getInstance().releaseEditor(editor);
    }
    PsiElement element = findElementAt(fileCopy, context.getStartOffset());
    if (element == null) {
      LOG.error("offset " + context.getStartOffset() + " at:\ntext=\"" + fileCopy.getText() + "\"\ninstance=" + fileCopy);
    }
    return Pair.create(context, element);
  }

  private static PsiElement findElementAt(final PsiFile fileCopy, int startOffset) {
    PsiElement element = fileCopy.findElementAt(startOffset);
    if (element instanceof MetadataPsiElementBase) {
      final PsiElement source = ((MetadataPsiElementBase)element).getSourceElement();
      if (source != null) {
        return source.findElementAt(startOffset - source.getTextRange().getStartOffset());
      }
    }
    return element;
  }


  private boolean isAutocompleteCommonPrefixOnInvocation() {
    return invokedExplicitly && CodeInsightSettings.getInstance().AUTOCOMPLETE_COMMON_PREFIX;
  }

  protected void handleEmptyLookup(Project project, Editor editor, final CompletionParameters parameters, final CompletionProgressIndicator indicator) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    if (!invokedExplicitly) {
      return;
    }

    for (final CompletionContributor contributor : CompletionContributor.forParameters(parameters)) {
      final String text = contributor.handleEmptyLookup(parameters, editor);
      if (StringUtil.isNotEmpty(text)) {
        final EditorHintListener listener = new EditorHintListener() {
          public void hintShown(final Project project, final LightweightHint hint, final int flags) {
            indicator.liveAfterDeath(hint);
          }
        };
        final MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(EditorHintListener.TOPIC, listener);
        HintManager.getInstance().showErrorHint(editor, text);
        connection.disconnect();
        break;
      }
    }
    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    if (codeAnalyzer != null) {
      codeAnalyzer.updateVisibleHighlighters(editor);
    }
  }

  private static void lookupItemSelected(final CompletionProgressIndicator context, @NotNull final LookupElement item, final char completionChar,
                                         final List<LookupElement> items) {
    final Editor editor = context.getEditor();
    final PsiFile file = context.getParameters().getOriginalFile();
    final InsertionContext context1 = new InsertionContext(context.getOffsetMap(), completionChar, items.toArray(new LookupElement[items.size()]), file, editor);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final int idEndOffset = context.getIdentifierEndOffset();
        if (idEndOffset != context.getSelectionEndOffset() && CompletionUtil.isOverwrite(item, completionChar)) {
          editor.getDocument().deleteString(context.getSelectionEndOffset(), idEndOffset);
        }

        PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
        item.handleInsert(context1);
        PostprocessReformattingAspect.getInstance(context.getProject()).doPostponedFormatting();

        if (context1.shouldAddCompletionChar() &&
            completionChar != Lookup.AUTO_INSERT_SELECT_CHAR && completionChar != Lookup.REPLACE_SELECT_CHAR &&
            completionChar != Lookup.NORMAL_SELECT_CHAR && completionChar != Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
          TailType.insertChar(editor, context1.getTailOffset(), completionChar);
        }
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    });
    final Runnable runnable = context1.getLaterRunnable();
    if (runnable != null) {
      final Runnable runnable1 = new Runnable() {
        public void run() {
          final Project project = context1.getProject();
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

  public static final Key<SoftReference<PsiFile>> FILE_COPY_KEY = Key.create("CompletionFileCopy");

  protected PsiFile createFileCopy(PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (file.isPhysical() && virtualFile != null && virtualFile.getFileSystem() == LocalFileSystem.getInstance()
        // must not cache injected file copy, since it does not reflect changes in host document
        && !InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) {
      final SoftReference<PsiFile> reference = file.getUserData(FILE_COPY_KEY);
      if (reference != null) {
        final PsiFile copy = reference.get();
        if (copy != null && copy.isValid() && copy.getClass().equals(file.getClass())) {
          final Document document = copy.getViewProvider().getDocument();
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
    file.putUserData(FILE_COPY_KEY, new SoftReference<PsiFile>(copy));
    return copy;
  }

  private static boolean isAutocompleteOnInvocation(final CompletionType type) {
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    switch (type) {
      case CLASS_NAME:
        return settings.AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION;
      case SMART:
        return settings.AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION;
      case BASIC:
      default:
        return settings.AUTOCOMPLETE_ON_CODE_COMPLETION;
    }
  }
}
