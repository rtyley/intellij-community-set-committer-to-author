/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.util.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 22, 2004
 */
public class ProjectOutputPathsStep extends ModuleWizardStep{
  private static final Icon NEW_PROJECT_ICON = IconLoader.getIcon("/newprojectwizard.png");
  private final JPanel myPanel;
  private final NamePathComponent myNamePathComponent;
  private final WizardContext myWizardContext;

  public ProjectOutputPathsStep(WizardContext wizardContext) {
    myWizardContext = wizardContext;
    myNamePathComponent = new NamePathComponent("", IdeBundle.message("label.select.compiler.output.path"), IdeBundle.message("title.select.compiler.output.path"), "", false);
    myNamePathComponent.setNameComponentVisible(false);
    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());
    myPanel.add(myNamePathComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 6, 0, 6), 0, 0));
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void updateDataModel() {
    myWizardContext.setCompilerOutputDirectory(myNamePathComponent.getPath());
  }

  public void updateStep() {
    if (!myNamePathComponent.isPathChangedByUser()) {
      final String projectFilePath = myWizardContext.getProjectFileDirectory();
      if (projectFilePath != null) {
        @NonNls String path = myWizardContext.getCompilerOutputDirectory();
        if (path == null) {
          path = StringUtil.endsWithChar(projectFilePath, '/') ? projectFilePath + "classes" : projectFilePath + "/classes";
        }
        myNamePathComponent.setPath(path.replace('/', File.separatorChar));
        myNamePathComponent.getPathComponent().selectAll();
      }
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return  myNamePathComponent.getPathComponent();
  }

  public boolean isStepVisible() {
    return myWizardContext.getProjectFileDirectory() != null;
  }

  public Icon getIcon() {
    return NEW_PROJECT_ICON;
  }

  public String getHelpId() {
    return null;
  }

  public String getCompileOutputPath() {
    return myNamePathComponent.getPath();
  }
}
