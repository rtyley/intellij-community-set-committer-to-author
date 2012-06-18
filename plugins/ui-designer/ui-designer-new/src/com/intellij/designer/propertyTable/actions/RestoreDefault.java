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

import com.intellij.designer.DesignerBundle;
import com.intellij.designer.propertyTable.Property;
import com.intellij.designer.propertyTable.PropertyTable;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;

/**
 * @author Alexander Lobas
 */
public class RestoreDefault extends AnAction implements IPropertyTableAction {
  private final PropertyTable myTable;

  public RestoreDefault(PropertyTable table) {
    myTable = table;

    Presentation presentation = getTemplatePresentation();
    String text = DesignerBundle.message("designer.properties.restore_default");
    presentation.setText(text);
    presentation.setDescription(text);
    presentation.setIcon(AllIcons.Actions.Reset_to_default);
  }

  @Override
  public void update(AnActionEvent e) {
    setEnabled(myTable, e.getPresentation());
  }

  @Override
  public void update() {
    setEnabled(myTable, getTemplatePresentation());
  }

  private static void setEnabled(PropertyTable table, Presentation presentation) {
    try {
      Property property = table.getSelectionProperty();
      presentation.setEnabled(property != null && !table.isDefault(property));
    }
    catch (Exception e) {
      presentation.setEnabled(false);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myTable.restoreDefaultValue();
    setEnabled(myTable, getTemplatePresentation());
  }
}