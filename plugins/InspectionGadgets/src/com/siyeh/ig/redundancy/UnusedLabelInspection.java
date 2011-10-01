/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class UnusedLabelInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unused.label.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnusedLabelVisitor();
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unused.label.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnusedLabelFix();
  }

  private static class UnusedLabelFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "unused.label.remove.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement label = descriptor.getPsiElement();
      final PsiLabeledStatement labeledStatement =
        (PsiLabeledStatement)label.getParent();
      assert labeledStatement != null;
      final PsiStatement statement = labeledStatement.getStatement();
      if (statement == null) {
        return;
      }
      final String statementText = statement.getText();
      replaceStatement(labeledStatement, statementText);
    }
  }

  private static class UnusedLabelVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLabeledStatement(
      PsiLabeledStatement statement) {
      if (containsBreakOrContinueForLabel(statement)) {
        return;
      }
      final PsiIdentifier labelIdentifier =
        statement.getLabelIdentifier();
      registerError(labelIdentifier);
    }

    private static boolean containsBreakOrContinueForLabel(
      PsiLabeledStatement statement) {
      final LabelFinder labelFinder = new LabelFinder(statement);
      statement.accept(labelFinder);
      return labelFinder.jumpFound();
    }
  }

  private static class LabelFinder extends JavaRecursiveElementVisitor {

    private boolean found = false;
    private String label = null;

    private LabelFinder(PsiLabeledStatement target) {
      final PsiIdentifier labelIdentifier = target.getLabelIdentifier();
      label = labelIdentifier.getText();
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (found) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitContinueStatement(
      @NotNull PsiContinueStatement continueStatement) {
      if (found) {
        return;
      }
      super.visitContinueStatement(continueStatement);
      final PsiIdentifier labelIdentifier =
        continueStatement.getLabelIdentifier();
      if (labelMatches(labelIdentifier)) {
        found = true;
      }
    }

    @Override
    public void visitBreakStatement(
      @NotNull PsiBreakStatement breakStatement) {
      if (found) {
        return;
      }
      super.visitBreakStatement(breakStatement);
      final PsiIdentifier labelIdentifier =
        breakStatement.getLabelIdentifier();
      if (labelMatches(labelIdentifier)) {
        found = true;
      }
    }

    private boolean labelMatches(PsiIdentifier labelIdentifier) {
      if (labelIdentifier == null) {
        return false;
      }
      final String labelText = labelIdentifier.getText();
      return labelText.equals(label);
    }

    public boolean jumpFound() {
      return found;
    }
  }
}