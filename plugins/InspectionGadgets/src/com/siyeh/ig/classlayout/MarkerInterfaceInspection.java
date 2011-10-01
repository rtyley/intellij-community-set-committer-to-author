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
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class MarkerInterfaceInspection extends BaseInspection {

  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("marker.interface.display.name");
  }

  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "marker.interface.problem.descriptor");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new MarkerInterfaceVisitor();
  }

  private static class MarkerInterfaceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so that it doesn't drill down to inner classes
      if (!aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      final PsiField[] fields = aClass.getFields();
      if (fields.length != 0) {
        return;
      }
      final PsiMethod[] methods = aClass.getMethods();
      if (methods.length != 0) {
        return;
      }
      final PsiClassType[] extendsList = aClass.getExtendsListTypes();
      if (extendsList.length > 1) {
        return;
      }
      registerClassError(aClass);
    }
  }
}