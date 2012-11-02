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
package com.siyeh.ig.logging;

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.refactoring.psi.PropertyUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ui.UiUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class PublicMethodWithoutLoggingInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public String loggerClassName = "java.util.logging.Logger" + ',' +
                                  "org.slf4j.Logger" + ',' +
                                  "org.apache.commons.logging.Log" + ',' +
                                  "org.apache.log4j.Logger";

  private final List<String> loggerClassNames = new ArrayList();

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "public.method.without.logging.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("public.method.without.logging.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    final ListTable table = new ListTable(
      new ListWrappingTableModel(loggerClassNames, InspectionGadgetsBundle.message("logger.class.name")));
    return UiUtils.createAddRemoveTreeClassChooserPanel(table, InspectionGadgetsBundle.message("choose.logger.class"));
  }

  @Override
  public void readSettings(Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(loggerClassName, loggerClassNames);
  }

  @Override
  public void writeSettings(Element element) throws WriteExternalException {
    loggerClassName = formatString(loggerClassNames);
    super.writeSettings(element);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PublicMethodWithoutLoggingVisitor();
  }

  private class PublicMethodWithoutLoggingVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      //no drilldown
      if (method.getNameIdentifier() == null) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      if (method.isConstructor()) {
        return;
      }
      if (PropertyUtils.isSimpleGetter(method) || PropertyUtils.isSimpleSetter(method)) {
        return;
      }
      if (containsLoggingCall(body)) {
        return;
      }
      registerMethodError(method);
    }

    private boolean containsLoggingCall(PsiCodeBlock block) {
      final ContainsLoggingCallVisitor visitor = new ContainsLoggingCallVisitor();
      block.accept(visitor);
      return visitor.containsLoggingCall();
    }
  }

  private class ContainsLoggingCallVisitor extends JavaRecursiveElementVisitor {

    private boolean containsLoggingCall = false;

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (containsLoggingCall) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      if (containsLoggingCall) {
        return;
      }
      super.visitMethodCallExpression(expression);
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String containingClassName = containingClass.getQualifiedName();
      if (containingClassName == null) {
        return;
      }
      if (loggerClassNames.contains(containingClassName)) {
        containsLoggingCall = true;
      }
    }

    public boolean containsLoggingCall() {
      return containsLoggingCall;
    }
  }
}
