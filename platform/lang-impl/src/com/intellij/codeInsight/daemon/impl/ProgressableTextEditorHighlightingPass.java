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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author cdr
 */
public abstract class ProgressableTextEditorHighlightingPass extends TextEditorHighlightingPass {
  private volatile boolean myFinished;
  private volatile long myProgessLimit = 0;
  private final AtomicLong myProgressCount = new AtomicLong();
  private final Icon myInProgressIcon;
  private final String myPresentableName;
  protected final PsiFile myFile;

  protected ProgressableTextEditorHighlightingPass(final Project project, @Nullable final Document document, final Icon inProgressIcon,
                                                   final String presentableName, @NotNull PsiFile file, boolean runIntentionPassAfter) {
    super(project, document, runIntentionPassAfter);
    myInProgressIcon = inProgressIcon;
    myPresentableName = presentableName;
    myFile = file;
  }

  public final void doCollectInformation(final ProgressIndicator progress) {
    myFinished = false;
    collectInformationWithProgress(progress);
  }

  protected abstract void collectInformationWithProgress(final ProgressIndicator progress);

  public final void doApplyInformationToEditor() {
    myFinished = true;
    applyInformationWithProgress();
    DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).getFileStatusMap().markFileUpToDate(myDocument, myFile, getId());
  }

  protected abstract void applyInformationWithProgress();

  /**
   * @return number in the [0..1] range;
   * <0 means progress is not available
   */
  public double getProgress() {
    if (myProgessLimit == 0) return -1;
    return (double)myProgressCount.get() / myProgessLimit;
  }

  public boolean isFinished() {
    return myFinished;
  }

  protected final Icon getInProgressIcon() {
    return myInProgressIcon;
  }

  protected final String getPresentableName() {
    return myPresentableName;
  }

  public void setProgressLimit(long limit) {
    myProgessLimit = limit;
  }

  public void advanceProgress(int delta) {
    myProgressCount.addAndGet(delta);
  }

  public static class EmptyPass extends TextEditorHighlightingPass {
    public EmptyPass(final Project project, @Nullable final Document document, Icon icon, String text) {
      super(project, document, false);
    }

    public void doCollectInformation(final ProgressIndicator progress) {

    }

    public void doApplyInformationToEditor() {

    }
  }
}
