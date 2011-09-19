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
package org.jetbrains.plugins.groovy.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.unwrap.JavaUnwrapper;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTryCatchStatement;

public class GroovyCatchRemover extends GroovyUnwrapper {
  public GroovyCatchRemover() {
    super(CodeInsightBundle.message("remove.catch"));
  }

  public boolean isApplicableTo(PsiElement e) {
    return e instanceof GrCatchClause && tryHasSeveralCatches(e);
  }

  private static boolean tryHasSeveralCatches(PsiElement el) {
    return ((GrTryCatchStatement)el.getParent()).getCatchClauses().length > 1;
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    context.delete(element);
  }
}
