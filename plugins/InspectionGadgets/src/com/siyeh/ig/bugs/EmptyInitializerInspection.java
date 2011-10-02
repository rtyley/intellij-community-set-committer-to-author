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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiStatement;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class EmptyInitializerInspection extends BaseInspection {

  @NotNull
  public String getID() {
    return "EmptyClassInitializer";
  }

  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "empty.class.initializer.display.name");
  }

  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "empty.class.initializer.problem.descriptor");
  }

  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new EmptyInitializerFix();
  }

  private static class EmptyInitializerFix extends InspectionGadgetsFix {

    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "empty.class.initializer.delete.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement codeBlock = element.getParent();
      assert codeBlock != null;
      final PsiElement classInitializer = codeBlock.getParent();
      assert classInitializer != null;
      deleteElement(classInitializer);
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new EmptyInitializerVisitor();
  }

  private static class EmptyInitializerVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClassInitializer(
      @NotNull PsiClassInitializer initializer) {
      super.visitClassInitializer(initializer);
      final PsiCodeBlock body = initializer.getBody();
      if (!codeBlockIsEmpty(body)) {
        return;
      }
      registerClassInitializerError(initializer);
    }

    private static boolean codeBlockIsEmpty(PsiCodeBlock codeBlock) {
      final PsiStatement[] statements = codeBlock.getStatements();
      return statements.length == 0;
    }
  }
}