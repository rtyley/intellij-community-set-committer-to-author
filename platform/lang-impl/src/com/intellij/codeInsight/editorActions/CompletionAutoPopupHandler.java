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
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class CompletionAutoPopupHandler extends TypedHandlerDelegate {
  public static volatile boolean ourTestingAutopopup = false;

  @Override
  public Result beforeCharTyped(char c,
                                Project project,
                                Editor editor,
                                PsiFile file,
                                FileType fileType) {
    CompletionPhase phase = CompletionServiceImpl.getCompletionPhase();
    if (phase instanceof CompletionPhase.EmptyAutoPopup) {
      ((CompletionPhase.EmptyAutoPopup)phase).handleTyping(c);
      return Result.STOP;
    }

    return Result.CONTINUE;
  }

  @Override
  public Result checkAutoPopup(char charTyped, final Project project, final Editor editor, final PsiFile file) {
    if (!CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP) return Result.CONTINUE;
    if (PowerSaveMode.isEnabled()) return Result.CONTINUE;

    if (LookupManager.getActiveLookup(editor) != null) {
      return Result.CONTINUE;
    }

    CompletionPhase oldPhase = CompletionServiceImpl.getCompletionPhase();
    if (oldPhase instanceof CompletionPhase.EmptyAutoPopup && ((CompletionPhase.EmptyAutoPopup)oldPhase).editor != editor) {
      CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
    }

    if (!Character.isLetter(charTyped) && charTyped != '_') {
      if (CompletionServiceImpl.isPhase(CompletionPhase.EmptyAutoPopup.class)) {
        CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion);
      }
      return Result.CONTINUE;
    }

    if (!CompletionServiceImpl.isPhase(CompletionPhase.AutoPopupAlarm.class, CompletionPhase.NoCompletion.getClass())) {
      return Result.CONTINUE;
    }

    scheduleAutoPopup(editor, null);
    return Result.STOP;
  }

  public static void scheduleAutoPopup(final Editor editor, @Nullable final Condition<PsiFile> condition) {
    final Project project = editor.getProject();
    assert project != null;
    final CompletionPhase.AutoPopupAlarm phase = new CompletionPhase.AutoPopupAlarm(false, editor);
    CompletionServiceImpl.setCompletionPhase(phase);

    AutoPopupController.getInstance(project).invokeAutoPopupRunnable(new Runnable() {
      @Override
      public void run() {
        runLaterWithCommitted(project, editor.getDocument(), new Runnable() {
          @Override
          public void run() {
            if (phase.isExpired()) return;

            PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
            if (file != null && condition != null && !condition.value(file)) return;

            invokeCompletion(CompletionType.BASIC, true, project, editor, 0);
          }
        });
      }
    }, CodeInsightSettings.getInstance().AUTO_LOOKUP_DELAY);
  }

  public static void invokeCompletion(CompletionType completionType,
                                      boolean autopopup,
                                      Project project, Editor editor, int time) {
    // retrieve the injected file from scratch since our typing might have destroyed the old one completely
    Editor topLevelEditor = InjectedLanguageUtil.getTopLevelEditor(editor);
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(topLevelEditor.getDocument());
    if (file == null) return;

    PsiFile topLevelFile = InjectedLanguageUtil.getTopLevelFile(file);
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    Editor newEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(topLevelEditor, topLevelFile);
    try {
      new CodeCompletionHandlerBase(completionType, false, autopopup).invokeCompletion(project, newEditor, time, false);
    }
    catch (IndexNotReadyException ignored) {
    }
  }

  public static void runLaterWithCommitted(@NotNull final Project project, final Document document, final Runnable runnable) {
    final long beforeStamp = document.getModificationStamp();
    ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(project)).performWhenAllCommitted(new Runnable() {
        @Override
        public void run() {
          // later because we may end up in write action here if there was a synchronous commit
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              if (beforeStamp != document.getModificationStamp()) {
                // no luck, will try later
                runLaterWithCommitted(project, document, runnable);
              }
              else {
                runnable.run();
              }
            }
          }, project.getDisposed());
        }
      });
  }
}
