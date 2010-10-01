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
import com.intellij.codeInsight.lookup.LookupAdapter;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.EventObject;
import java.util.List;

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
  private boolean myDisposed;
  private boolean myInitialized;
  private int myCount;
  private final Update myUpdate = new Update("update") {
    public void run() {
      updateLookup();
    }
  };
  private LightweightHint myHint;
  private final CompletionContext myContextOriginal;
  private final Semaphore myFreezeSemaphore;

  private boolean myModifiersReleased;

  private String myOldDocumentText;
  private int myOldCaret;
  private int myOldStart;
  private int myOldEnd;
  private boolean myBackgrounded = true;

  public CompletionProgressIndicator(final Editor editor, CompletionParameters parameters, CodeCompletionHandlerBase handler,
                                     final CompletionContext contextOriginal, Semaphore freezeSemaphore) {
    myEditor = editor;
    myParameters = parameters;
    myHandler = handler;
    myContextOriginal = contextOriginal;
    myFreezeSemaphore = freezeSemaphore;

    myLookup = (LookupImpl)LookupManager.getInstance(editor.getProject()).createLookup(editor, LookupElement.EMPTY_ARRAY, "", new CompletionLookupArranger(parameters));
    if (editor.isOneLineMode()) {
      myLookup.setForceShowAsPopup(true);
      myLookup.setCancelOnClickOutside(true);
      myLookup.setCancelOnOtherWindowOpen(true);
      myLookup.setResizable(false);
      myLookup.setForceLightweightPopup(false);
    }
    myLookup.setFocused(handler.focusLookup);

    myLookup.addLookupListener(new LookupAdapter() {
      public void itemSelected(LookupEvent event) {
        lookupClosed();

        LookupElement item = event.getItem();
        if (item == null) return;

        setMergeCommand();

        contextOriginal.setStartOffset(myEditor.getCaretModel().getOffset() - item.getLookupString().length());
        CodeCompletionHandlerBase.selectLookupItem(item, event.getCompletionChar(), contextOriginal, myLookup.getItems());
      }


      public void lookupCanceled(final LookupEvent event) {
        lookupClosed();
      }
    });
    myLookup.setCalculating(true);

    myQueue = new MergingUpdateQueue("completion lookup progress", 200, true, myEditor.getContentComponent());

    ApplicationManager.getApplication().assertIsDispatchThread();
    registerItself();

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      scheduleAdvertising();
    }

    trackModifiers();
  }

  void notifyBackgrounded() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myBackgrounded = true;
  }

  boolean isBackgrounded() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myBackgrounded;
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
                if (isOutdated()) {
                  return;
                }
                if (!myLookup.isFocused() && !myInitialized) {
                  return;
                }
                if (!isBackgrounded()) {
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
    return myDisposed ||
           myEditor.isDisposed() ||
           !ApplicationManager.getApplication().isUnitTestMode() && myEditor.getComponent().getRootPane() == null;
  }

  private void trackModifiers() {
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
          myModifiersReleased = true;
          if (myOldDocumentText != null) {
            cleanup();
          }
          contentComponent.removeKeyListener(this);
        }
      }
    });
  }

  private void setMergeCommand() {
    CommandProcessor.getInstance().setCurrentCommandGroupId("Completion" + hashCode());
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

  public void liveAfterDeath(@Nullable final LightweightHint hint) {
    if (myModifiersReleased || ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    registerItself();
    myHint = hint;
    if (hint != null) {
      hint.addHintListener(new HintListener() {
        public void hintHidden(final EventObject event) {
          hint.removeHintListener(this);
          cleanup();
        }
      });
    }
    final Document document = myEditor.getDocument();
    document.addDocumentListener(new DocumentAdapter() {
      @Override
      public void beforeDocumentChange(DocumentEvent e) {
        document.removeDocumentListener(this);
        cleanup();
      }
    });
    final SelectionModel selectionModel = myEditor.getSelectionModel();
    selectionModel.addSelectionListener(new SelectionListener() {
      public void selectionChanged(SelectionEvent e) {
        selectionModel.removeSelectionListener(this);
        cleanup();
      }
    });
    final CaretModel caretModel = myEditor.getCaretModel();
    caretModel.addCaretListener(new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        caretModel.removeCaretListener(this);
        cleanup();
      }
    });
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

    if (!myInitialized) {
      myInitialized = true;
      myLookup.show();
    }
    myLookup.refreshUi();
  }

  public int getCount() {
    return myCount;
  }

  private boolean isInsideIdentifier() {
    return myContextOriginal.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET) != myContextOriginal.getSelectionEndOffset();
  }


  public synchronized void addItem(final LookupElement item) {
    if (!isRunning()) return;
    ProgressManager.checkCanceled();

    final boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    if (!unitTestMode) {
      assert !ApplicationManager.getApplication().isDispatchThread();
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

  public void closeAndFinish() {
    if (myHint != null) {
      myHint.hide();
    }
    LookupManager.getInstance(myEditor.getProject()).hideActiveLookup();
  }

  private void lookupClosed() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        cancel();
      }
    });

    assert !myDisposed;
    myDisposed = true;

    ApplicationManager.getApplication().assertIsDispatchThread();
    Disposer.dispose(myQueue);
    cleanup();
  }

  @TestOnly
  public static void cleanupForNextTest() {
    CompletionProgressIndicator currentCompletion = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
    if (currentCompletion != null) {
      currentCompletion.lookupClosed();
    }
  }

  private void cleanup() {
    assert ApplicationManager.getApplication().isDispatchThread();
    myHint = null;
    myOldDocumentText = null;
    unregisterItself();
  }

  private void unregisterItself() {
    CompletionServiceImpl.getCompletionService().setCurrentCompletion(null);
  }

  public void stop() {
    super.stop();

    myQueue.cancelAllUpdates();
    myFreezeSemaphore.up();

    invokeLaterIfNotDispatch(new Runnable() {
      public void run() {
        if (isOutdated()) return;
        if (isCanceled()) return;

        //what if a new completion was invoked by the user before this 'later'?
        if (CompletionProgressIndicator.this != CompletionServiceImpl.getCompletionService().getCurrentCompletion()) return;

        myLookup.setCalculating(false);

        if (!isBackgrounded()) return;

        if (myCount == 0) {
          LookupManager.getInstance(myContextOriginal.project).hideActiveLookup();
          assert CompletionServiceImpl.getCompletionService().getCurrentCompletion() == null;

          if (myLookup.isFocused() ) {
            myHandler.handleEmptyLookup(myContextOriginal.project, myEditor, myParameters, CompletionProgressIndicator.this);
          }
        } else {
          updateLookup();
        }

      }
    });

  }

  private void invokeLaterIfNotDispatch(final Runnable runnable) {
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      runnable.run();
    } else {
      application.invokeLater(runnable, myQueue.getModalityState());
    }
  }

  public volatile String cancelTrace;
  @Override
  public void cancel() {
    if (cancelTrace == null) {
      Throwable t = new Throwable();
      final StringWriter writer = new StringWriter();
      t.printStackTrace(new PrintWriter(writer));
      cancelTrace = writer.toString();
    }
    super.cancel();
  }

  @Override
  public void start() {
    if (isCanceled()) {
      throw new AssertionError("Restarting completion process is prohibited");
    }

    super.start();
  }

  @Override
  public void initStateFrom(@NotNull ProgressIndicatorEx indicator) {
    if (isCanceled()) {
      throw new AssertionError("Re-init-ting completion process is prohibited");
    }

    super.initStateFrom(indicator);
  }

  public boolean fillInCommonPrefix(final boolean explicit) {
    if (isInsideIdentifier()) {
      return false;
    }

    final Boolean aBoolean = new WriteCommandAction<Boolean>(myEditor.getProject()) {
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

  public boolean isInitialized() {
    return myInitialized;
  }

  public void restorePrefix() {
    setMergeCommand();

    if (myOldDocumentText != null) {
      myEditor.getDocument().setText(myOldDocumentText);
      myEditor.getSelectionModel().setSelection(myOldStart, myOldEnd);
      myEditor.getCaretModel().moveToOffset(myOldCaret);
      myOldDocumentText = null;
      return;
    }

    getLookup().restorePrefix();
  }

  public Editor getEditor() {
    return myEditor;
  }

  public void rememberDocumentState() {
    if (myModifiersReleased) {
      return;
    }

    myOldDocumentText = myEditor.getDocument().getText();
    myOldCaret = myEditor.getCaretModel().getOffset();
    myOldStart = myEditor.getSelectionModel().getSelectionStart();
    myOldEnd = myEditor.getSelectionModel().getSelectionEnd();
  }

  public boolean isRepeatedInvocation(CompletionType completionType, Editor editor) {
    return completionType == myParameters.getCompletionType() && editor == myEditor;
  }
}
