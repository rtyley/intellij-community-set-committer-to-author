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
package org.intellij.plugins.intelliLang.inject;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Gregory.Shrago
 */
public class CustomErrorElementFilter extends HighlightErrorFilter implements HighlightInfoFilter {
  public boolean shouldHighlightErrorElement(@NotNull final PsiErrorElement element) {
    return !value(element);
  }

  public static boolean value(final PsiErrorElement psiErrorElement) {
    return isFrankenstein(psiErrorElement.getContainingFile());
  }

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
    if (highlightInfo.getSeverity() != HighlightSeverity.WARNING &&
        highlightInfo.getSeverity() != HighlightSeverity.WEAK_WARNING) return true;
    if (!isFrankenstein(file)) return true;
    int start = highlightInfo.getStartOffset();
    int end = highlightInfo.getEndOffset();
    // the bad news are: offsets may be in host file or in injected file
    String text = (end < file.getTextLength() ? file.getText() : file.getContext().getContainingFile().getText()).substring(start, end);
    return !"missingValue".equals(text);
  }

  private static boolean isFrankenstein(PsiFile file) {
    return file != null && Boolean.TRUE.equals(file.getUserData(InjectedLanguageUtil.FRANKENSTEIN_INJECTION));
  }
}
