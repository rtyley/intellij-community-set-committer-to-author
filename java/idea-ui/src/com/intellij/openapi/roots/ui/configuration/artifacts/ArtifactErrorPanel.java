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
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.util.IconLoader;
import com.intellij.packaging.ui.ArtifactProblemQuickFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author nik
 */
public class ArtifactErrorPanel {
  private JPanel myMainPanel;
  private JButton myFixButton;
  private JLabel myErrorLabel;
  private ArtifactProblemQuickFix myCurrentQuickFix;

  public ArtifactErrorPanel(final ArtifactEditorImpl artifactEditor) {
    myErrorLabel.setIcon(IconLoader.getIcon("/runConfigurations/configurationWarning.png"));
    myFixButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myCurrentQuickFix != null) {
          myCurrentQuickFix.performFix(artifactEditor);
          artifactEditor.queueValidation();
        }
      }
    });
    clearError();
  }

  public void showError(@NotNull String message, @Nullable ArtifactProblemQuickFix quickFix) {
    myErrorLabel.setVisible(true);
    myErrorLabel.setText("<html>" + message + "</html>");
    myMainPanel.setVisible(true);
    myCurrentQuickFix = quickFix;
    myFixButton.setVisible(quickFix != null);
    if (quickFix != null) {
      myFixButton.setText(quickFix.getActionName());
    }
  }

  public void clearError() {
    myMainPanel.setVisible(false);
    myErrorLabel.setVisible(false);
    myFixButton.setVisible(false);
  }

  public JComponent getMainPanel() {
    return myMainPanel;
  }
}
