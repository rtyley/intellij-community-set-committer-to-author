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
package com.siyeh.ig.encapsulation;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.psi.*;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.EncapsulateVariableFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class PublicFieldInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreEnums = false;

  @SuppressWarnings({"PublicField"})
  public final ExternalizableStringSet ignorableAnnotations = new ExternalizableStringSet();

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("public.field.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "public.field.problem.descriptor");
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final JPanel annotationsListControl = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      ignorableAnnotations, InspectionGadgetsBundle.message("ignore.if.annotated.by"));
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.anchor = GridBagConstraints.WEST;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    panel.add(annotationsListControl, constraints);
    final CheckBox checkBox = new CheckBox(InspectionGadgetsBundle.message(
      "public.field.ignore.enum.type.fields.option"), this, "ignoreEnums");
    constraints.gridy = 1;
    panel.add(checkBox, constraints);
    return panel;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiField field = (PsiField)infos[0];
    return new EncapsulateVariableFix(field.getName());
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PublicFieldVisitor();
  }

  private class PublicFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      if (!field.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      if (AnnotationUtil.isAnnotated(field, ignorableAnnotations)) {
        return;
      }
      if (field.hasModifierProperty(PsiModifier.FINAL)) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) {
          return;
        }
        final PsiType type = field.getType();
        if (ClassUtils.isImmutable(type)) {
          return;
        }
        if (ignoreEnums) {
          if (type instanceof PsiClassType) {
            final PsiClassType classType = (PsiClassType)type;
            final PsiClass aClass = classType.resolve();
            if (aClass != null && aClass.isEnum()) {
              return;
            }
          }
        }
      }
      registerFieldError(field, field);
    }
  }
}