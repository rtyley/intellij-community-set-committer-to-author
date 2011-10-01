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
package com.siyeh.ig.security;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public class CloneableClassInSecureContextInspection extends BaseInspection {

  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "cloneable.class.in.secure.context.display.name");
  }

  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "cloneable.class.in.secure.context.problem.descriptor");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new CloneableClassInSecureContextVisitor();
  }

  private static class CloneableClassInSecureContextVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so it doesn't drill down
      if (aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      if (!CloneUtils.isCloneable(aClass)) {
        return;
      }
      final PsiMethod[] methods = aClass.getMethods();
      for (final PsiMethod method : methods) {
        if (CloneUtils.isClone(method)) {
          if (ControlFlowUtils.methodAlwaysThrowsException(method)) {
            return;
          }
        }
      }
      registerClassError(aClass);
    }
  }
}