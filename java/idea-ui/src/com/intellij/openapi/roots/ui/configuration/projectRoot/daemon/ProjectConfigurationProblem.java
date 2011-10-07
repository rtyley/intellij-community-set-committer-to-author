/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ConfigurationError;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
* @author nik
*/
class ProjectConfigurationProblem extends ConfigurationError {
  private final ProjectStructureProblemDescription myDescription;
  private final Project myProject;

  public ProjectConfigurationProblem(ProjectStructureProblemDescription description, Project project) {
    super(computeMessage(description), computeDescription(description),
          ProjectStructureProblemsSettings.getInstance(project).isIgnored(description));
    myDescription = description;
    myProject = project;
  }

  private static String computeDescription(ProjectStructureProblemDescription description) {
    final String descriptionString = description.getDescription();
    return descriptionString != null ? descriptionString : computeMessage(description);
  }

  private static String computeMessage(ProjectStructureProblemDescription description) {
    return description.getPlace().getContainingElement().getPresentableName() + ": " + StringUtil.decapitalize(description.getMessage());
  }

  @Override
  public void ignore(boolean b) {
    super.ignore(b);
    ProjectStructureProblemsSettings.getInstance(myProject).setIgnored(myDescription, b);
  }

  @Override
  public void navigate() {
    myDescription.getPlace().navigate();
  }

  @Override
  public boolean canBeFixed() {
    return !myDescription.getFixes().isEmpty();
  }

  @Override
  public void fix(final JComponent contextComponent, RelativePoint relativePoint) {
    JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<ConfigurationErrorQuickFix>(null, myDescription.getFixes()) {
      @NotNull
      @Override
      public String getTextFor(ConfigurationErrorQuickFix value) {
        return value.getActionName();
      }

      @Override
      public PopupStep onChosen(final ConfigurationErrorQuickFix selectedValue, boolean finalChoice) {
        return doFinalStep(new Runnable() {
          @Override
          public void run() {
            selectedValue.performFix();
          }
        });
      }
    }).show(relativePoint);
  }
}
