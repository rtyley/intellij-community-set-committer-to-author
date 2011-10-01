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
package com.siyeh.ig.classlayout;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MoveClassFix;
import org.jetbrains.annotations.NotNull;

public class MultipleTopLevelClassesInFileInspection extends BaseInspection {

  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "multiple.top.level.classes.in.file.display.name");
  }

  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "multiple.top.level.classes.in.file.problem.descriptor");
  }

  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new MoveClassFix();
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new MultipleTopLevelClassesInFileVisitor();
  }

  private static class MultipleTopLevelClassesInFileVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so that it doesn't drill down to inner classes
      if (!(aClass.getParent() instanceof PsiJavaFile)) {
        return;
      }
      final PsiJavaFile file = (PsiJavaFile)aClass.getParent();
      if (file == null) {
        return;
      }
      int numClasses = 0;
      final PsiElement[] children = file.getChildren();
      for (final PsiElement child : children) {
        if (child instanceof PsiClass) {
          numClasses++;
        }
      }
      if (numClasses <= 1) {
        return;
      }
      registerClassError(aClass);
    }
  }
}