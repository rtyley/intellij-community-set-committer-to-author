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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.scope.processor.VariablesNotProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Nov 20, 2002
 * Time: 3:01:25 PM
 * To change this template use Options | File Templates.
 */
public class ReuseVariableDeclarationFix implements IntentionAction {
  private final PsiVariable variable;
  private final PsiIdentifier identifier;

  public ReuseVariableDeclarationFix(PsiVariable variable, PsiIdentifier identifier) {
    this.variable = variable;
    this.identifier = identifier;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("reuse.variable.declaration.family");
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("reuse.variable.declaration.text", variable.getName());
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {

    PsiVariable previousVariable = findPreviousVariable();
    return
        variable != null
        && variable.isValid()
        && variable instanceof PsiLocalVariable
        && previousVariable != null
        && Comparing.equal(previousVariable.getType(), variable.getType())
        && identifier != null
        && identifier.isValid()
        && variable.getManager().isInProject(variable);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiVariable refVariable = findPreviousVariable();
    if (refVariable == null) return;
    if (!CodeInsightUtil.preparePsiElementsForWrite(variable, refVariable)) return;
    PsiUtil.setModifierProperty(refVariable, PsiModifier.FINAL, false);
    if (variable.getInitializer() == null)  {
      variable.delete();
      return;
    }
    PsiDeclarationStatement declaration = (PsiDeclarationStatement) variable.getParent();
    PsiElementFactory factory = JavaPsiFacade.getInstance(variable.getProject()).getElementFactory();
    PsiStatement statement = factory.createStatementFromText(variable.getName() + " = " + variable.getInitializer().getText()+";", variable);
    declaration.replace(statement);
  }

  private PsiVariable findPreviousVariable() {
    PsiElement scope = variable.getParent();
    while (scope != null) {
      if (scope instanceof PsiFile || scope instanceof PsiMethod || scope instanceof PsiClassInitializer) break;
      scope = scope.getParent();
    }
    if (scope == null) return null;
    VariablesNotProcessor proc = new VariablesNotProcessor(variable, false);
    PsiScopesUtil.treeWalkUp(proc, identifier, scope);

    if(proc.size() > 0)
      return proc.getResult(0);
    return null;
  }

  public boolean startInWriteAction() {
    return true;
  }

}
