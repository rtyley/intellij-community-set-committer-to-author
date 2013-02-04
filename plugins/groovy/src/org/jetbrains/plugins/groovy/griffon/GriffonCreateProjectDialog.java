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
package org.jetbrains.plugins.groovy.griffon;

import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.mvc.MvcCommand;

import javax.swing.*;

/**
 * @author peter
 */
public class GriffonCreateProjectDialog extends DialogWrapper {
  private JTextField myOptionField;
  private JPanel myComponent;
  private JRadioButton myCreateApp;
  private JRadioButton myCreatePlugin;
  private JRadioButton myCreateAddon;
  private JRadioButton myCreateArchetype;
  private JLabel myCreateLabel;

  public GriffonCreateProjectDialog(@NotNull Module module) {
    super(module.getProject());
    setTitle("Create Griffon Structure");
    myCreateLabel.setText("Create Griffon structure in module '" + module.getName() + "':");
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myComponent;
  }

  MvcCommand getCommand() {
    String cmd;

    if (myCreateAddon.isSelected()) {
      cmd = "create-addon";
    }
    else if (myCreateApp.isSelected()) {
      cmd = "create-app";
    }
    else if (myCreateArchetype.isSelected()) {
      cmd = "create-archetype";
    }
    else if (myCreatePlugin.isSelected()) {
      cmd = "create-plugin";
    }
    else {
      throw new AssertionError("No selection");
    }

    String text = myOptionField.getText();
    if (text == null) text = "";

    return new MvcCommand(cmd, ParametersList.parse(text));
  }

}
