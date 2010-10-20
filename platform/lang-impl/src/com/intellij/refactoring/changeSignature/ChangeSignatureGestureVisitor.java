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
package com.intellij.refactoring.changeSignature;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * User: anna
 * Date: Sep 8, 2010
 */
public class ChangeSignatureGestureVisitor implements HighlightVisitor {
  private static final Logger LOG = Logger.getInstance("#" + ChangeSignatureGestureDetector.class.getName());
  @NonNls private static final String SIGNATURE_SHOULD_BE_POSSIBLY_CHANGED = "Signature should be possibly changed";

  @Override
  public boolean suitableForFile(PsiFile file) {
    return file != null && LanguageChangeSignatureDetectors.isSuitableForLanguage(file.getLanguage());
  }

  @Override
  public void visit(final PsiElement element, HighlightInfoHolder holder) {
    final ChangeSignatureGestureDetector detector = ChangeSignatureGestureDetector.getInstance(element.getProject());
    if (detector.isChangeSignatureAvailable(element)) {
      final TextRange range = LanguageChangeSignatureDetectors.getHighlightingRange(element);
      LOG.assertTrue(range != null);
      final ChangeInfo changeInfo = detector.getChangeInfo(element.getContainingFile());
      LOG.assertTrue(changeInfo != null);
      final boolean toHighlight = LanguageChangeSignatureDetectors.INSTANCE.forLanguage(element.getLanguage()).isToHighlight(element, changeInfo);
      final HighlightInfo info = new HighlightInfo(new TextAttributes(null, null, null, toHighlight ? EffectType.BOXED : null, Font.PLAIN),
                                                   HighlightInfoType.INFORMATION, range.getStartOffset(), range.getEndOffset(),
                                                   SIGNATURE_SHOULD_BE_POSSIBLY_CHANGED, SIGNATURE_SHOULD_BE_POSSIBLY_CHANGED,
                                                   HighlightSeverity.INFORMATION, false, true, false);
      final ChangeSignatureDetectorAction action = new ChangeSignatureDetectorAction();
      info.setGutterIconRenderer(new MyGutterIconRenderer(action, element));
      holder.add(info);
    }
  }

  @Override
  public boolean analyze(Runnable action, boolean updateWholeFile, PsiFile file) {
    action.run();
    return true;
  }

  @Override
  public HighlightVisitor clone() {
    return new ChangeSignatureGestureVisitor();
  }

  @Override
  public int order() {
    return 10;
  }

  private static class MyGutterIconRenderer extends GutterIconRenderer {
    private final ChangeSignatureDetectorAction myAction;
    private final PsiElement myElement;

    public MyGutterIconRenderer(ChangeSignatureDetectorAction action, PsiElement element) {
      myAction = action;
      myElement = element;
    }

    @NotNull
    @Override
    public Icon getIcon() {
      return Icons.ADVICE_ICON;
    }

    @Override
    public AnAction getClickAction() {
      return new AnAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          myAction.invoke(myElement.getProject(), null, myElement.getContainingFile());
        }
      };
    }

    @Override
    public String getTooltipText() {
      return SIGNATURE_SHOULD_BE_POSSIBLY_CHANGED;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof MyGutterIconRenderer)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return 0;
    }
  }
}
