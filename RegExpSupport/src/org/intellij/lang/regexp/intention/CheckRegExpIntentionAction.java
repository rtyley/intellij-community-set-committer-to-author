/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.intellij.lang.regexp.intention;

import com.intellij.codeInsight.intention.impl.QuickEditAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.intellij.lang.regexp.RegExpLanguage;
import org.intellij.lang.regexp.RegExpRangeProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 * @author Anna Bulenkova
 */
public class CheckRegExpIntentionAction extends QuickEditAction implements Iconable {

  @Override
  protected Pair<PsiElement, TextRange> getRangePair(PsiFile file, Editor editor) {
    Pair<PsiElement, TextRange> pair = super.getRangePair(file, editor);
    if (pair != null) return pair;
    RegExpRangeProvider[] rangeProviders = RegExpRangeProvider.EP_NAME.getExtensions();
    PsiElement element = PsiUtilBase.getElementAtCaret(editor);
    for (RegExpRangeProvider provider : rangeProviders) {
      TextRange range = provider.getTextRange(element);
      if (range != null) {
        return Pair.create(element, range);
      }
    }
    return null;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final Pair<PsiElement, TextRange> pair = getRangePair(file, editor);
    /*super.isAvailable(project, editor, file) && */
    return pair != null && pair.first != null && pair.first.getLanguage() == RegExpLanguage.INSTANCE;
  }

  @Override
  protected boolean isShowInBalloon() {
    return true;
  }

  @Override
  protected JComponent createBalloonComponent(PsiFile file, final Ref<Balloon> ref) {
    final Project project = file.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document != null) {
      return new CheckRegExpForm(new Pair<PsiFile, Ref<Balloon>>(file, ref)).getRootPanel();
    }
    return null;
  }

  @NotNull
  @Override
  public String getText() {
    return "Check RegExp";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public Icon getIcon(int flags) {
    //noinspection ConstantConditions
    return RegExpLanguage.INSTANCE.getAssociatedFileType().getIcon();
  }
}
