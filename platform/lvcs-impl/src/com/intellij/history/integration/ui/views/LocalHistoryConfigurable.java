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

package com.intellij.history.integration.ui.views;

import com.intellij.history.LocalHistoryConfiguration;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NumberDocument;
import org.jetbrains.annotations.Nullable;

import static javax.swing.BorderFactory.createCompoundBorder;
import static javax.swing.BorderFactory.createEmptyBorder;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import java.awt.*;

public class LocalHistoryConfigurable extends BaseConfigurable implements SearchableConfigurable {
  private static final int MILLIS_IN_DAY = 1000 * 60 * 60 * 24;

  private JTextField myPurgePeriodField;
  private JCheckBox myProjectOpenBox;
  private JCheckBox myOnProjectCompileBox;
  private JCheckBox myOnFileCompileBox;
  private JCheckBox myOnProjectMakeBox;
  private JCheckBox myOnRunningBox;
  private JCheckBox myOnUnitTestsPassedBox;
  private JCheckBox myOnUnitTestsFailedBox;

  public String getDisplayName() {
    return LocalHistoryBundle.message("config.dialog.title");
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableLocalVCS.png");
  }

  public String getHelpTopic() {
    return "project.propLocalVCS";
  }

  public String getId() {
    return getHelpTopic();
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  public void disposeUIResources() {
  }

  public JComponent createComponent() {
    JPanel controls = new JPanel(new BorderLayout(0, 5));
    controls.add(createPurgePeriodPanel(), BorderLayout.NORTH);
    controls.add(createLabelingPanel(), BorderLayout.CENTER);
    
    JPanel panel = new JPanel(new BorderLayout(0, 0));
    panel.add(controls, BorderLayout.NORTH);
    panel.add(Box.createVerticalGlue(), BorderLayout.CENTER);
    return panel;
  }

  private JPanel createPurgePeriodPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    myPurgePeriodField = new JTextField();

    JLabel l = new JLabel(LocalHistoryBundle.message("config.period"));
    l.setLabelFor(myPurgePeriodField);

    panel.add(l, BorderLayout.CENTER);
    panel.add(myPurgePeriodField, BorderLayout.EAST);

    Dimension size = new Dimension(50, myPurgePeriodField.getPreferredSize().height);
    myPurgePeriodField.setPreferredSize(size);
    myPurgePeriodField.setDocument(new NumberDocument() {
      @Override
      public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
        super.insertString(offs, str, a);
        setModified(true);
      }
    });

    return panel;
  }

  private JPanel createLabelingPanel() {
    JPanel panel = new JPanel(new GridLayout(7, 1));
    Border title = IdeBorderFactory.createTitledBorder(LocalHistoryBundle.message("config.put.label.group"));
    panel.setBorder(createCompoundBorder(title, createEmptyBorder(2, 2, 2, 2)));

    myProjectOpenBox = addCheckBox("config.put.label.on.project.opening", panel);
    myOnProjectCompileBox = addCheckBox("config.put.label.on.project.compilation", panel);
    myOnFileCompileBox = addCheckBox("config.put.label.on.file.package.compilation", panel);
    myOnProjectMakeBox = addCheckBox("config.put.label.on.project.make", panel);
    myOnRunningBox = addCheckBox("config.put.label.on.running.debugging", panel);
    myOnUnitTestsPassedBox = addCheckBox("config.put.label.on.unit.tests.passed", panel);
    myOnUnitTestsFailedBox = addCheckBox("config.put.label.on.unit.tests.failed", panel);

    return panel;
  }

  private JCheckBox addCheckBox(String messageKey, JPanel p) {
    final JCheckBox cb = new JCheckBox();

    cb.addChangeListener(new ChangeListener() {
      private boolean myOldValue = cb.isSelected();

      public void stateChanged(ChangeEvent e) {
        if (myOldValue != cb.isSelected()) {
          setModified(true);
          myOldValue = cb.isSelected();
        }
      }
    });

    cb.setText(LocalHistoryBundle.message(messageKey));
    p.add(cb);
    return cb;
  }

  public void apply() throws ConfigurationException {
    LocalHistoryConfiguration c = getConfiguration();

    c.PURGE_PERIOD = Long.parseLong(myPurgePeriodField.getText()) * MILLIS_IN_DAY;

    c.ADD_LABEL_ON_FILE_PACKAGE_COMPILATION = myOnFileCompileBox.isSelected();
    c.ADD_LABEL_ON_PROJECT_COMPILATION = myOnProjectCompileBox.isSelected();
    c.ADD_LABEL_ON_PROJECT_MAKE = myOnProjectMakeBox.isSelected();
    c.ADD_LABEL_ON_PROJECT_OPEN = myProjectOpenBox.isSelected();
    c.ADD_LABEL_ON_RUNNING = myOnRunningBox.isSelected();
    c.ADD_LABEL_ON_UNIT_TEST_PASSED = myOnUnitTestsPassedBox.isSelected();
    c.ADD_LABEL_ON_UNIT_TEST_FAILED = myOnUnitTestsFailedBox.isSelected();

    setModified(false);
  }

  public void reset() {
    LocalHistoryConfiguration c = getConfiguration();

    myPurgePeriodField.setText(String.valueOf(c.PURGE_PERIOD / MILLIS_IN_DAY));

    myOnFileCompileBox.setSelected(c.ADD_LABEL_ON_FILE_PACKAGE_COMPILATION);
    myOnProjectCompileBox.setSelected(c.ADD_LABEL_ON_PROJECT_COMPILATION);
    myOnProjectMakeBox.setSelected(c.ADD_LABEL_ON_PROJECT_MAKE);
    myProjectOpenBox.setSelected(c.ADD_LABEL_ON_PROJECT_OPEN);
    myOnRunningBox.setSelected(c.ADD_LABEL_ON_RUNNING);
    myOnUnitTestsPassedBox.setSelected(c.ADD_LABEL_ON_UNIT_TEST_PASSED);
    myOnUnitTestsFailedBox.setSelected(c.ADD_LABEL_ON_UNIT_TEST_FAILED);

    setModified(false);
  }

  private LocalHistoryConfiguration getConfiguration() {
    return LocalHistoryConfiguration.getInstance();
  }

}
