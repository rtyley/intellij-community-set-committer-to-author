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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.EventObject;

/**
 * @author peter
 */
public abstract class CompletionPhase implements Disposable {
  public static final CompletionPhase NoCompletion = new CompletionPhase(null) {
    @Override
    public CompletionProgressIndicator newCompletionStarted() {
      return null;
    }
  };

  public final CompletionProgressIndicator indicator;

  protected CompletionPhase(CompletionProgressIndicator indicator) {
    this.indicator = indicator;
  }

  @Override
  public void dispose() {
  }

  @Nullable
  public abstract CompletionProgressIndicator newCompletionStarted();

  public static class AutoPopupAlarm extends CompletionPhase {
    public AutoPopupAlarm() {
      super(null);
    }

    @Override
    public CompletionProgressIndicator newCompletionStarted() {
      return null;
    }
  }
  public static class Synchronous extends CompletionPhase {
    public Synchronous(CompletionProgressIndicator indicator) {
      super(indicator);
    }

    @Override
    public CompletionProgressIndicator newCompletionStarted() {
      throw new UnsupportedOperationException("Not implemented");
    }
  }
  public static class BgCalculation extends CompletionPhase {
    boolean modifiersChanged = false;
    volatile boolean focusLookupWhenDone = false;

    public BgCalculation(final CompletionProgressIndicator indicator) {
      super(indicator);
      ApplicationManager.getApplication().addApplicationListener(new ApplicationAdapter() {
        @Override
        public void beforeWriteActionStart(Object action) {
          indicator.scheduleRestart();
        }
      }, this);
    }

    @Override
    public CompletionProgressIndicator newCompletionStarted() {
      indicator.closeAndFinish(false);
      return indicator;
    }
  }
  public static class ItemsCalculated extends CompletionPhase {
    public final boolean focusLookup;

    public ItemsCalculated(CompletionProgressIndicator indicator, boolean focusLookup) {
      super(indicator);
      this.focusLookup = focusLookup;
    }

    @Override
    public CompletionProgressIndicator newCompletionStarted() {
      indicator.closeAndFinish(false);
      return indicator;
    }
  }
  public static class Restarted extends CompletionPhase {
    public Restarted(CompletionProgressIndicator indicator) {
      super(indicator);
    }

    @Override
    public CompletionProgressIndicator newCompletionStarted() {
      indicator.closeAndFinish(false);
      return indicator;
    }
  }

  public static class ZombiePhase extends CompletionPhase {

    protected ZombiePhase(@Nullable final LightweightHint hint, final CompletionProgressIndicator indicator) {
      super(indicator);
      @NotNull Editor editor = indicator.getEditor();
      final HintListener hintListener = new HintListener() {
        public void hintHidden(final EventObject event) {
          CompletionServiceImpl.setCompletionPhase(NoCompletion);
        }
      };
      final DocumentAdapter documentListener = new DocumentAdapter() {
        @Override
        public void beforeDocumentChange(DocumentEvent e) {
          CompletionServiceImpl.setCompletionPhase(NoCompletion);
        }
      };
      final SelectionListener selectionListener = new SelectionListener() {
        public void selectionChanged(SelectionEvent e) {
          CompletionServiceImpl.setCompletionPhase(NoCompletion);
        }
      };
      final CaretListener caretListener = new CaretListener() {
        public void caretPositionChanged(CaretEvent e) {
          CompletionServiceImpl.setCompletionPhase(NoCompletion);
        }
      };

      final Document document = editor.getDocument();
      final SelectionModel selectionModel = editor.getSelectionModel();
      final CaretModel caretModel = editor.getCaretModel();


      if (hint != null) {
        hint.addHintListener(hintListener);
      }
      document.addDocumentListener(documentListener);
      selectionModel.addSelectionListener(selectionListener);
      caretModel.addCaretListener(caretListener);

      Disposer.register(this, new Disposable() {
        @Override
        public void dispose() {
          if (hint != null) {
            hint.removeHintListener(hintListener);
          }
          document.removeDocumentListener(documentListener);
          selectionModel.removeSelectionListener(selectionListener);
          caretModel.removeCaretListener(caretListener);
        }
      });
    }

    @Override
    public CompletionProgressIndicator newCompletionStarted() {
      return indicator;
    }
  }

  public static class InsertedSingleItem extends ZombiePhase {
    public final Runnable restorePrefix;

    public InsertedSingleItem(CompletionProgressIndicator indicator, Runnable restorePrefix) {
      super(null, indicator);
      this.restorePrefix = restorePrefix;
    }
  }
  public static class NoSuggestionsHint extends ZombiePhase {
    public NoSuggestionsHint(@Nullable LightweightHint hint, CompletionProgressIndicator indicator) {
      super(hint, indicator);
    }
  }
  public static class PossiblyDisturbingAutoPopup extends CompletionPhase {
    public PossiblyDisturbingAutoPopup(CompletionProgressIndicator indicator) {
      super(indicator);
    }

    @Override
    public CompletionProgressIndicator newCompletionStarted() {
      return null;
    }
  }
  public static class EmptyAutoPopup extends CompletionPhase {
    private final Editor editor;
    private final Project project;
    private final EditorMouseAdapter mouseListener;
    private final CaretListener caretListener;
    private final DocumentAdapter documentListener;
    private final PropertyChangeListener lookupListener;
    private boolean changeGuard = false;

    public EmptyAutoPopup(CompletionProgressIndicator indicator) {
      super(indicator);
      this.editor = indicator.getEditor();
      this.project = indicator.getProject();
      MessageBusConnection connection = project.getMessageBus().connect(this);
      connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
        @Override
        public void selectionChanged(FileEditorManagerEvent event) {
          stopAutoPopup();
        }
      });

      mouseListener = new EditorMouseAdapter() {
        @Override
        public void mouseClicked(EditorMouseEvent e) {
          stopAutoPopup();
        }
      };

      caretListener = new CaretListener() {
        @Override
        public void caretPositionChanged(CaretEvent e) {
          if (!changeGuard) {
            stopAutoPopup();
          }
        }
      };
      editor.getSelectionModel().addSelectionListener(new SelectionListener() {
        @Override
        public void selectionChanged(SelectionEvent e) {
          stopAutoPopup();
        }
      });
      documentListener = new DocumentAdapter() {
        @Override
        public void documentChanged(DocumentEvent e) {
          if (!changeGuard) {
            stopAutoPopup();
          }
        }
      };
      lookupListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          stopAutoPopup();
        }
      };

      editor.addEditorMouseListener(mouseListener);
      editor.getCaretModel().addCaretListener(caretListener);
      editor.getDocument().addDocumentListener(documentListener);
      LookupManager.getInstance(project).addPropertyChangeListener(lookupListener);
    }

    @Override
    public void dispose() {
      editor.removeEditorMouseListener(mouseListener);
      editor.getCaretModel().removeCaretListener(caretListener);
      editor.getDocument().removeDocumentListener(documentListener);
      LookupManager.getInstance(project).removePropertyChangeListener(lookupListener);
    }

    public void handleTyping(char c) {
      changeGuard = true;
      try {
        EditorModificationUtil.typeInStringAtCaretHonorBlockSelection(editor, String.valueOf(c), true);
      }
      finally {
        changeGuard = false;
      }

    }

    private static void stopAutoPopup() {
      CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
    }

    @Override
    public CompletionProgressIndicator newCompletionStarted() {
      return null;
    }
  }

}
