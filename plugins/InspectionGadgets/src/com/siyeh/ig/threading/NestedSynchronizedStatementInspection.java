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
package com.siyeh.ig.threading;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSynchronizedStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class NestedSynchronizedStatementInspection extends BaseInspection {

  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "nested.synchronized.statement.display.name");
  }

  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "nested.synchronized.statement.problem.descriptor");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new NestedSynchronizedStatementVisitor();
  }

  private static class NestedSynchronizedStatementVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitSynchronizedStatement(
      @NotNull PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      final PsiElement containingSynchronizedStatement =
        PsiTreeUtil.getParentOfType(statement,
                                    PsiSynchronizedStatement.class);
      if (containingSynchronizedStatement == null) {
        return;
      }
      final PsiMethod containingMethod =
        PsiTreeUtil.getParentOfType(statement,
                                    PsiMethod.class);
      final PsiMethod containingContainingMethod =
        PsiTreeUtil.getParentOfType(containingSynchronizedStatement,
                                    PsiMethod.class);
      if (containingMethod == null ||
          containingContainingMethod == null ||
          !containingMethod.equals(containingContainingMethod)) {
        return;
      }
      registerStatementError(statement);
    }
  }
}