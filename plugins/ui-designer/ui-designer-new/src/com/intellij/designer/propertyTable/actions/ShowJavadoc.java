/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.propertyTable.actions;

import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.designer.DesignerBundle;
import com.intellij.designer.DesignerToolWindowManager;
import com.intellij.designer.propertyTable.Property;
import com.intellij.designer.propertyTable.PropertyTable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.awt.RelativePoint;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class ShowJavadoc extends AnAction implements IPropertyTableAction {
  public ShowJavadoc() {
    Presentation presentation = getTemplatePresentation();
    String text = DesignerBundle.message("designer.properties.show.javadoc");
    presentation.setText(text);
    presentation.setDescription(text);
    presentation.setIcon(IconLoader.getIcon("/actions/help.png"));
  }

  @Override
  public void update(AnActionEvent e) {
    PropertyTable table = DesignerToolWindowManager.getInstance(e.getProject()).getPropertyTable();
    setEnabled(table, e.getPresentation());
  }

  @Override
  public void update(PropertyTable table) {
    setEnabled(table, getTemplatePresentation());
  }

  private static void setEnabled(PropertyTable table, Presentation presentation) {
    Property property = table.getSelectionProperty();
    presentation.setEnabled(property != null && (property.getJavadocElement() != null || !StringUtil.isEmpty(property.getJavadocText())));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    DocumentationManager documentationManager = DocumentationManager.getInstance(project);
    final DocumentationComponent component = new DocumentationComponent(documentationManager);

    final PropertyTable table = DesignerToolWindowManager.getInstance(project).getPropertyTable();
    final Property property = table.getSelectionProperty();
    PsiElement javadocElement = property.getJavadocElement();

    ActionCallback callback;
    if (javadocElement == null) {
      callback = new ActionCallback();
      component.setText("<html><body>" + property.getJavadocText() + "</body></html>", null, true);
    }
    else {
      callback = documentationManager.queueFetchDocInfo(javadocElement, component);
    }

    callback.doWhenProcessed(new Runnable() {
      public void run() {
        final JBPopup hint =
          JBPopupFactory.getInstance().createComponentPopupBuilder(component, component)
            .setDimensionServiceKey(project, DocumentationManager.JAVADOC_LOCATION_AND_SIZE, false)
            .setResizable(true)
            .setMovable(true)
            .setRequestFocus(true)
            .setTitle(DesignerBundle.message("designer.properties.javadoc.title", property.getName()))
            .createPopup();
        component.setHint(hint);
        Disposer.register(hint, component);
        hint.show(new RelativePoint(table.getParent(), new Point(0, 0)));
      }
    });

    if (javadocElement == null) {
      callback.setDone();
    }
  }
}