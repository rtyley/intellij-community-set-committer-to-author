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

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: anna
 * Date: 06-Jun-2006
 */
public class LanguageLevelConfigurable implements UnnamedConfigurable {
  private LanguageLevelCombo myLanguageLevelCombo;
  private JPanel myPanel = new JPanel(new GridBagLayout());
  public LanguageLevelModuleExtension myLanguageLevelExtension;

  public LanguageLevelConfigurable(ModifiableRootModel rootModule) {
    myLanguageLevelExtension = rootModule.getModuleExtension(LanguageLevelModuleExtension.class);
    myLanguageLevelCombo = new LanguageLevelCombo();
    myLanguageLevelCombo.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final Object languageLevel = myLanguageLevelCombo.getSelectedItem();
        myLanguageLevelExtension.setLanguageLevel(languageLevel instanceof LanguageLevel ? (LanguageLevel)languageLevel : null);
      }
    });
    myLanguageLevelCombo.insertItemAt(LanguageLevelCombo.USE_PROJECT_LANGUAGE_LEVEL, 0);
    myPanel.add(new JLabel(ProjectBundle.message("module.module.language.level")), new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(8, 6, 6, 0), 0, 0));
    myPanel.add(myLanguageLevelCombo, new GridBagConstraints(1, 0, 1, 1, 1.0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(6, 6, 6, 0), 0, 0));
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return myLanguageLevelExtension.isChanged();
  }

  public void apply() throws ConfigurationException {
    myLanguageLevelExtension.commit();
  }

  public void reset() {
    myLanguageLevelCombo.setSelectedItem(myLanguageLevelExtension.getLanguageLevel());
  }

  public void disposeUIResources() {
    myPanel = null;
    myLanguageLevelCombo = null;
    myLanguageLevelExtension = null;
  }
}
