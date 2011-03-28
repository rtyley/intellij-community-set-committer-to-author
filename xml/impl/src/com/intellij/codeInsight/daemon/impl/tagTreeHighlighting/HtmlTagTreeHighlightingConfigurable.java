/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.tagTreeHighlighting;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Eugene.Kudelevsky
 */
public class HtmlTagTreeHighlightingConfigurable implements UnnamedConfigurable {
  private JCheckBox myEnableTagTreeHighlightingCheckBox;
  private JSpinner myLevelsSpinner;
  private JPanel myLevelsPanel;
  private JPanel myContentPanel;

  public HtmlTagTreeHighlightingConfigurable() {
    myLevelsSpinner.setModel(new SpinnerNumberModel(1, 1, 50, 1));

    myEnableTagTreeHighlightingCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final boolean enabled = myEnableTagTreeHighlightingCheckBox.isSelected();
        UIUtil.setEnabled(myLevelsPanel, enabled, true);
      }
    });
  }

  @Override
  public JComponent createComponent() {
    return myContentPanel;
  }

  @Override
  public boolean isModified() {
    final WebEditorOptions options = WebEditorOptions.getInstance();

    if (myEnableTagTreeHighlightingCheckBox.isSelected() != options.isTagTreeHighlightingEnabled()) {
      return true;
    }

    if (getLevelCount() != options.getTagTreeHighlightingLevelCount()) {
      return true;
    }

    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    final WebEditorOptions options = WebEditorOptions.getInstance();

    options.setTagTreeHighlightingEnabled(myEnableTagTreeHighlightingCheckBox.isSelected());
    options.setTagTreeHighlightingLevelCount(getLevelCount());

    clearTagTreeHighlighting();
  }

  private int getLevelCount() {
    return ((Integer)myLevelsSpinner.getValue()).intValue();
  }

  private static void clearTagTreeHighlighting() {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      for (FileEditor fileEditor : FileEditorManager.getInstance(project).getAllEditors()) {
        if (fileEditor instanceof TextEditor) {
          final Editor editor = ((TextEditor)fileEditor).getEditor();
          HtmlTagTreeHighlightingPass.clearHighlightingAndLineMarkers(editor, project);
        }
      }
    }
  }

  @Override
  public void reset() {
    final WebEditorOptions options = WebEditorOptions.getInstance();
    final boolean enabled = options.isTagTreeHighlightingEnabled();

    myEnableTagTreeHighlightingCheckBox.setSelected(enabled);
    myLevelsSpinner.setValue(options.getTagTreeHighlightingLevelCount());
    UIUtil.setEnabled(myLevelsPanel, enabled, true);
  }

  @Override
  public void disposeUIResources() {
  }
}
