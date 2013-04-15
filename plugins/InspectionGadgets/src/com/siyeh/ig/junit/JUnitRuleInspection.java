/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 4/12/13
 */
public class JUnitRuleInspection extends BaseInspection {
  public static final String RULE_FQN = "org.junit.Rule";
  public static final String CLASS_RULE_FQN = "org.junit.ClassRule";
  public boolean REPORT_RULE_PROBLEMS = true;
  public boolean REPORT_CLASS_RULE_PROBLEMS = true;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("junit.rule.display.name");
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox("Report @Rule problems", "REPORT_RULE_PROBLEMS");
    panel.addCheckbox("Report @ClassRule problems", "REPORT_CLASS_RULE_PROBLEMS");
    return panel;
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return (String)infos[0];
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return infos.length > 1 ? new MakePublicStaticFix((String)infos[1], (String)infos[2]) : null;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitField(PsiField field) {
        final boolean ruleAnnotated = REPORT_RULE_PROBLEMS && AnnotationUtil.isAnnotated(field, RULE_FQN, false);
        final boolean classRuleAnnotated = REPORT_CLASS_RULE_PROBLEMS && AnnotationUtil.isAnnotated(field, CLASS_RULE_FQN, false);
        if (ruleAnnotated || classRuleAnnotated) {
          String annotation = ruleAnnotated ? RULE_FQN : CLASS_RULE_FQN;
          String errorMessage = null;
          final boolean hasStatic = field.hasModifierProperty(PsiModifier.STATIC);
          final boolean hasPublic = field.hasModifierProperty(PsiModifier.PUBLIC);
          if (!hasPublic) {
            if (classRuleAnnotated) {
              if (!hasStatic) {
                errorMessage = "public and static";
              } else {
                errorMessage = "public";
              }
            }
            else {
              if (!hasStatic){
                errorMessage = "public";
              } else {
                errorMessage = "public and non-static";
              }
            }
          }
          else {
            if (!hasStatic) {
              if (classRuleAnnotated) {
                errorMessage = "static";
              }
            }
            else if (ruleAnnotated) {
              errorMessage = "non-static";
            }
          }
          if (errorMessage != null) {
            registerError(field.getNameIdentifier(), InspectionGadgetsBundle.message("junit.rule.problem.descriptor", annotation, errorMessage), "Make field " + errorMessage, annotation);
          }
          if (!InheritanceUtil.isInheritor(PsiUtil.resolveClassInClassTypeOnly(field.getType()), false, "org.junit.rules.TestRule")) {
            registerError(field.getNameIdentifier(), InspectionGadgetsBundle.message("junit.rule.type.problem.descriptor"));
          }
        }
      }
    };
  }

  private static class MakePublicStaticFix extends InspectionGadgetsFix {
    private final String myName;
    private final boolean myMakeStatic;

    public MakePublicStaticFix(String name, String annotation) {
      myName = name;
      myMakeStatic = annotation.equals(CLASS_RULE_FQN);
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (element != null) {
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiField) {
          PsiUtil.setModifierProperty((PsiField)parent, PsiModifier.PUBLIC, true);
          PsiUtil.setModifierProperty((PsiField)parent, PsiModifier.STATIC, myMakeStatic);
        }
      }
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }
  }
}
