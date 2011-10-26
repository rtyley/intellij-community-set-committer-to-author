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
package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public class StringLiteralManipulator extends AbstractElementManipulator<PsiLiteralExpression> {
  @Override
  public PsiLiteralExpression handleContentChange(PsiLiteralExpression expr, TextRange range, String newContent) throws IncorrectOperationException {
    final Object value = expr.getValue();
    if (!(value instanceof String)) throw new IncorrectOperationException("cannot handle content change for: "+ value+", expr: "+expr);
    String oldText = expr.getText();
    newContent = StringUtil.escapeStringCharacters(newContent);
    String newText = oldText.substring(0, range.getStartOffset()) + newContent + oldText.substring(range.getEndOffset());
    final PsiExpression newExpr = JavaPsiFacade.getInstance(expr.getProject()).getElementFactory().createExpressionFromText(newText, null);
    return (PsiLiteralExpression)expr.replace(newExpr);
  }

  @Override
  public TextRange getRangeInElement(final PsiLiteralExpression element) {
    return getValueRange(element);
  }

  public static TextRange getValueRange(PsiLiteralExpression element) {
    final Object value = element.getValue();
    if (!(value instanceof String || value instanceof Character)) return TextRange.from(0, element.getTextLength());
    return new TextRange(1, Math.max(1, element.getTextLength() - 1));
  }
}
