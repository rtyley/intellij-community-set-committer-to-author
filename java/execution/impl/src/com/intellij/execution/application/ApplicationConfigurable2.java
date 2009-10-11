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
package com.intellij.execution.application;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.junit2.configuration.ClassBrowser;
import com.intellij.execution.junit2.configuration.CommonJavaParameters;
import com.intellij.execution.junit2.configuration.ConfigurationModuleSelector;
import com.intellij.execution.ui.AlternativeJREPanel;
import com.intellij.execution.util.JreVersionDetector;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ApplicationConfigurable2 extends SettingsEditor<ApplicationConfiguration> {
  private CommonJavaParameters myCommonJavaParameters;
  private LabeledComponent<TextFieldWithBrowseButton> myMainClass;
  private LabeledComponent<JComboBox> myModule;
  private JPanel myWholePanel;

  private final ConfigurationModuleSelector myModuleSelector;
  private AlternativeJREPanel myAlternativeJREPanel;
  private JCheckBox myShowSwingInspectorCheckbox;
  private EnvironmentVariablesComponent myEnvVariablesComponent;
  private final JreVersionDetector myVersionDetector = new JreVersionDetector();

  public ApplicationConfigurable2(final Project project) {
    myModuleSelector = new ConfigurationModuleSelector(project, myModule.getComponent());
    myModule.getComponent().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myCommonJavaParameters.setModuleContext(myModuleSelector.getModule());
      }
    });
    ClassBrowser.createApplicationClassBrowser(project, myModuleSelector).setField(getMainClassField());
  }

  public void applyEditorTo(final ApplicationConfiguration configuration) throws ConfigurationException {
    myCommonJavaParameters.applyTo(configuration);
    myModuleSelector.applyTo(configuration);
    configuration.MAIN_CLASS_NAME = getMainClassField().getText();
    configuration.ALTERNATIVE_JRE_PATH = myAlternativeJREPanel.getPath();
    configuration.ALTERNATIVE_JRE_PATH_ENABLED = myAlternativeJREPanel.isPathEnabled();
    configuration.ENABLE_SWING_INSPECTOR = myVersionDetector.isJre50Configured(configuration) && myShowSwingInspectorCheckbox.isSelected();

    configuration.setEnvs(myEnvVariablesComponent.getEnvs());
    configuration.PASS_PARENT_ENVS = myEnvVariablesComponent.isPassParentEnvs();

    updateShowSwingInspector(configuration);
  }

  public void resetEditorFrom(final ApplicationConfiguration configuration) {
    myCommonJavaParameters.reset(configuration);
    myModuleSelector.reset(configuration);
    getMainClassField().setText(configuration.MAIN_CLASS_NAME);
    myAlternativeJREPanel.init(configuration.ALTERNATIVE_JRE_PATH, configuration.ALTERNATIVE_JRE_PATH_ENABLED);

    myEnvVariablesComponent.setEnvs(configuration.getEnvs());
    myEnvVariablesComponent.setPassParentEnvs(configuration.PASS_PARENT_ENVS);

    updateShowSwingInspector(configuration);
  }

  private void updateShowSwingInspector(final ApplicationConfiguration configuration) {
    if (myVersionDetector.isJre50Configured(configuration)) {
      myShowSwingInspectorCheckbox.setEnabled(true);
      myShowSwingInspectorCheckbox.setSelected(configuration.ENABLE_SWING_INSPECTOR);
      myShowSwingInspectorCheckbox.setText(ExecutionBundle.message("show.swing.inspector"));
    }
    else {
      myShowSwingInspectorCheckbox.setEnabled(false);
      myShowSwingInspectorCheckbox.setSelected(false);
      myShowSwingInspectorCheckbox.setText(ExecutionBundle.message("show.swing.inspector.disabled"));
    }
  }

  public TextFieldWithBrowseButton getMainClassField() {
    return myMainClass.getComponent();
  }

  public CommonJavaParameters getCommonJavaParameters() {
    return myCommonJavaParameters;
  }

  @NotNull
  public JComponent createEditor() {
    return myWholePanel;
  }

  public void disposeEditor() {
  }
}
