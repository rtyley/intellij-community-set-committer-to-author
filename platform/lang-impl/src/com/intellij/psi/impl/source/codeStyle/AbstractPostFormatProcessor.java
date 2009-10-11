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
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Jul 21, 2005
 * Time: 7:53:12 PM
 * To change this template use File | Settings | File Templates.
 */
public class AbstractPostFormatProcessor {
  protected final CodeStyleSettings mySettings;
  private TextRange myResultTextRange;

  public AbstractPostFormatProcessor(final CodeStyleSettings settings) {
    mySettings = settings;
  }

  protected void updateResultRange(final int oldTextLength, final int newTextLength) {
    if (myResultTextRange == null) return;

    myResultTextRange = new TextRange(myResultTextRange.getStartOffset(),
                                      myResultTextRange.getEndOffset()  - oldTextLength + newTextLength);
  }

  protected boolean checkElementContainsRange(final PsiElement element) {
    if (myResultTextRange == null) return true;

    final TextRange elementRange = element.getTextRange();
    if (elementRange.getEndOffset() < myResultTextRange.getStartOffset()) return false;
    return elementRange.getStartOffset() <= myResultTextRange.getEndOffset();

  }

  protected boolean checkRangeContainsElement(final PsiElement element) {
    if (myResultTextRange == null) return true;

    final TextRange elementRange = element.getTextRange();

    return elementRange.getStartOffset() >= myResultTextRange.getStartOffset()
           && elementRange.getEndOffset() <= myResultTextRange.getEndOffset();
  }

  protected static boolean isMultiline(@Nullable PsiElement statement) {
    if (statement == null) {
      return false;
    } else {
      return statement.textContains('\n');
    }
  }

  public void setResultTextRange(final TextRange resultTextRange) {
    myResultTextRange = resultTextRange;
  }

  public TextRange getResultTextRange() {
    return myResultTextRange;
  }
}
