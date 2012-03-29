/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.methodmetrics;

import com.intellij.psi.PsiMethod;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;

public class MethodCouplingInspection extends MethodMetricInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_includeJavaClasses = false;
  /**
   * @noinspection PublicField
   */
  public boolean m_includeLibraryClasses = false;

  @Override
  @NotNull
  public String getID() {
    return "OverlyCoupledMethod";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("method.coupling.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Integer coupling = (Integer)infos[0];
    return InspectionGadgetsBundle.message("method.coupling.problem.descriptor", coupling);
  }

  @Override
  protected int getDefaultLimit() {
    return 10;
  }

  @Override
  protected String getConfigurationLabel() {
    return InspectionGadgetsBundle.message("method.coupling.limit.option");
  }

  @Override
  public Collection<? extends JComponent> createExtraOptions() {
    final CheckBox arrayCheckBox =
      new CheckBox(InspectionGadgetsBundle.message("include.java.system.classes.option"), this, "m_includeJavaClasses");
    final CheckBox objectCheckBox =
      new CheckBox(InspectionGadgetsBundle.message("include.library.classes.option"), this, "m_includeLibraryClasses");
    return Arrays.asList(arrayCheckBox, objectCheckBox);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodCouplingVisitor();
  }

  private class MethodCouplingVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      // note: no call to super
      if (method.getNameIdentifier() == null) {
        return;
      }
      final CouplingVisitor visitor = new CouplingVisitor(method, m_includeJavaClasses, m_includeLibraryClasses);
      method.accept(visitor);
      final int coupling = visitor.getNumDependencies();

      if (coupling <= getLimit()) {
        return;
      }
      registerMethodError(method, Integer.valueOf(coupling));
    }
  }
}