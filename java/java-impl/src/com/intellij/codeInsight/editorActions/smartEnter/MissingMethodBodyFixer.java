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
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 7:24:03 PM
 * To change this template use Options | File Templates.
 */
public class MissingMethodBodyFixer implements Fixer {
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiMethod)) return;
    PsiMethod method = (PsiMethod) psiElement;
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || containingClass.isInterface()
        || method.hasModifierProperty(PsiModifier.ABSTRACT) || method.hasModifierProperty(PsiModifier.NATIVE)) return;

    final PsiCodeBlock body = method.getBody();
    final Document doc = editor.getDocument();
    if (body != null) {
      // See IDEADEV-1093. This is quite hacky heuristic but it seem to be best we can do.
      String bodyText = body.getText();
      if (bodyText.startsWith("{")) {
        final PsiStatement[] statements = body.getStatements();
        if (statements.length > 0) {
          if (statements[0] instanceof PsiDeclarationStatement) {
            if (PsiTreeUtil.getDeepestLast(statements[0]) instanceof PsiErrorElement) {
              if (containingClass.getRBrace() == null) {
                doc.insertString(body.getTextRange().getStartOffset() + 1, "\n}");
              }
            }
          }
        }
      }
      return;
    }
    int endOffset = method.getTextRange().getEndOffset();
    if (StringUtil.endsWithChar(method.getText(), ';')) {
      doc.deleteString(endOffset - 1, endOffset);
      endOffset--;
    }
    doc.insertString(endOffset, "{\n}");
  }
}
