/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 15-Jun-2010
 */
package com.intellij.execution.junit;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class AddToTestsPatternAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final PsiElement[] psiElements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    final Set<PsiClass> classes = collectTestClasses(psiElements);

    final String classNames = StringUtil.join(classes, new Function<PsiClass, String>() {
      public String fun(PsiClass psiClass) {
        return psiClass.getQualifiedName();
      }
    }, "||");
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final List<JUnitConfiguration> patternConfigurations = collectPatternConfigurations(classes, project);
    if (patternConfigurations.size() == 1) {
      final JUnitConfiguration configuration = patternConfigurations.get(0);
      configuration.getPersistentData().PATTERN += (configuration.getPersistentData().PATTERN.length() > 0 ? "||" : "") + classNames;
    } else {
      JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<JUnitConfiguration>("Choose suite to add", patternConfigurations) {
        @Override
        public PopupStep onChosen(JUnitConfiguration configuration, boolean finalChoice) {
          configuration.getPersistentData().PATTERN += (configuration.getPersistentData().PATTERN.length() > 0 ? "||" : "") + classNames;
          return FINAL_CHOICE;
        }

        @Override
        public Icon getIconFor(JUnitConfiguration configuration) {
          return configuration.getIcon();
        }

        @NotNull
        @Override
        public String getTextFor(JUnitConfiguration value) {
          return value.getName();
        }
      }).showInBestPositionFor(dataContext);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setVisible(false);
    final DataContext dataContext = e.getDataContext();
    final PsiElement[] psiElements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    if (psiElements != null) {
      final Set<PsiClass> foundClasses = collectTestClasses(psiElements);
      if (foundClasses.isEmpty()) return;
      final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
      if (project != null) {
        final List<JUnitConfiguration> foundConfigurations = collectPatternConfigurations(foundClasses, project);
        if (!foundConfigurations.isEmpty()) {
          presentation.setVisible(true);
          if (foundConfigurations.size() == 1) {
            presentation.setText(presentation.getText() + ": " + foundConfigurations.get(0).getName());
          }
        }
      }
    }
  }

  private static List<JUnitConfiguration> collectPatternConfigurations(Set<PsiClass> foundClasses, Project project) {
    final RunConfiguration[] configurations = RunManager.getInstance(project).getConfigurations(JUnitConfigurationType.getInstance());
    final List<JUnitConfiguration> foundConfigurations = new ArrayList<JUnitConfiguration>();
    for (RunConfiguration configuration : configurations) {
      final JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
      if (data.TEST_OBJECT == JUnitConfiguration.TEST_PATTERN) {
        if (foundClasses.size() > 1 || data.getPattern().indexOf(foundClasses.iterator().next().getQualifiedName()) < 0 ) {
          foundConfigurations.add((JUnitConfiguration)configuration);
        }
      }
    }
    return foundConfigurations;
  }

  private static Set<PsiClass> collectTestClasses(PsiElement[] psiElements) {
    final Set<PsiClass> foundClasses = new HashSet<PsiClass>();
    for (PsiElement psiElement : psiElements) {
      if (psiElement instanceof PsiClassOwner) {
        final PsiClass[] classes = ((PsiClassOwner)psiElement).getClasses();
        for (PsiClass aClass : classes) {
          if (JUnitUtil.isTestClass(aClass)) {
            foundClasses.add(aClass);
          }
        }
      } else if (psiElement instanceof PsiClass) {
        if (JUnitUtil.isTestClass((PsiClass)psiElement)) {
          foundClasses.add((PsiClass)psiElement);
        }
      }
    }
    return foundClasses;
  }
}