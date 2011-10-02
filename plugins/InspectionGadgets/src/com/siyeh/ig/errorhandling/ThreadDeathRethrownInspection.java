/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class ThreadDeathRethrownInspection extends BaseInspection {

  @NotNull
  public String getID() {
    return "ThreadDeathNotRethrown";
  }

  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "thread.death.rethrown.display.name");
  }

  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "thread.death.rethrown.problem.descriptor");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ThreadDeathRethrownVisitor();
  }

  private static class ThreadDeathRethrownVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitTryStatement(@NotNull PsiTryStatement statement) {
      super.visitTryStatement(statement);
      final PsiCatchSection[] catchSections = statement.getCatchSections();
      for (PsiCatchSection catchSection : catchSections) {
        final PsiParameter parameter = catchSection.getParameter();
        final PsiCodeBlock catchBlock =
          catchSection.getCatchBlock();
        if (parameter != null && catchBlock != null) {
          checkCatchBlock(parameter, catchBlock);
        }
      }
    }

    private void checkCatchBlock(PsiParameter parameter,
                                 PsiCodeBlock catchBlock) {
      final PsiType type = parameter.getType();
      if (!TypeUtils.typeEquals("java.lang.ThreadDeath", type)) {
        return;
      }
      final PsiTypeElement typeElement = parameter.getTypeElement();
      final PsiStatement[] statements = catchBlock.getStatements();
      if (statements.length == 0) {
        registerError(typeElement);
        return;
      }
      final PsiStatement lastStatement =
        statements[statements.length - 1];
      if (!(lastStatement instanceof PsiThrowStatement)) {
        registerError(typeElement);
        return;
      }
      final PsiThrowStatement throwStatement =
        (PsiThrowStatement)lastStatement;
      final PsiExpression exception = throwStatement.getException();
      if (!(exception instanceof PsiReferenceExpression)) {
        registerError(typeElement);
        return;
      }
      final PsiElement element = ((PsiReference)exception).resolve();
      if (parameter.equals(element)) {
        return;
      }
      registerError(typeElement);
    }
  }
}
