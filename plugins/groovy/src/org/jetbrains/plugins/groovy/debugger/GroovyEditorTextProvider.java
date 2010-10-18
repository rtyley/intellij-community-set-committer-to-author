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
package org.jetbrains.plugins.groovy.debugger;

import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.EditorTextProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisReferenceExpression;

/**
 * @author Maxim.Medvedev
 */
public class GroovyEditorTextProvider implements EditorTextProvider {
  @Override
  public TextWithImports getEditorText(PsiElement elementAtCaret) {
    String result = "";
    PsiElement element = findExpression(elementAtCaret);
    if (element != null) {
      if (element instanceof GrReferenceExpression) {
        final GrReferenceExpression reference = (GrReferenceExpression)element;
        if (reference.getQualifier() == null) {
          final PsiElement resolved = reference.resolve();
          if (resolved instanceof PsiEnumConstant) {
            final PsiEnumConstant enumConstant = (PsiEnumConstant)resolved;
            final PsiClass enumClass = enumConstant.getContainingClass();
            if (enumClass != null) {
              result = enumClass.getName() + "." + enumConstant.getName();
            }
          }
        }
      }
      if (result.length() == 0) {
        result = element.getText();
      }
    }
    return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, result);
  }

  @Nullable
  private static PsiElement findExpression(PsiElement element) {
    PsiElement parent = element.getParent();
    if (parent instanceof PsiVariable && element == ((PsiVariable)parent).getNameIdentifier()) {
      return element;
    }
    if (parent instanceof GrReferenceExpression) {
      if (parent.getParent() instanceof GrCall) return parent.getParent();
      return parent;
    }
    if (parent instanceof GrThisReferenceExpression) {
      return parent;
    }
    return null;
  }
}
