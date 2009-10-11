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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 14.11.2006
 * Time: 19:04:28
 */
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.Consumer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

public class CreatePatchConfigurationPanel {
  private JPanel myPanel;
  private TextFieldWithBrowseButton myFileNameField;
  private JCheckBox myReversePatchCheckbox;
  private JLabel myErrorLabel;
  private Consumer<Boolean> myOkEnabledListener;

  public CreatePatchConfigurationPanel() {
    myFileNameField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(myFileNameField.getText()));
        if (fileChooser.showSaveDialog(myFileNameField) != JFileChooser.APPROVE_OPTION) {
          return;
        }
        myFileNameField.setText(fileChooser.getSelectedFile().getPath());
      }
    });

    myFileNameField.getTextField().addInputMethodListener(new InputMethodListener() {
      public void inputMethodTextChanged(final InputMethodEvent event) {
        checkName();
      }
      public void caretPositionChanged(final InputMethodEvent event) {
      }
    });
    myFileNameField.getTextField().addKeyListener(new KeyListener() {
      public void keyTyped(final KeyEvent e) {
        checkName();
      }
      public void keyPressed(final KeyEvent e) {
        checkName();
      }
      public void keyReleased(final KeyEvent e) {
        checkName();
      }
    });
    myErrorLabel.setForeground(Color.RED);
    checkName();
  }

  private void checkName() {
    final PatchNameChecker patchNameChecker = new PatchNameChecker(myFileNameField.getText());
    if (patchNameChecker.nameOk()) {
      myErrorLabel.setText("");
    } else {
      myErrorLabel.setText(patchNameChecker.getError());
    }
    if (myOkEnabledListener != null) {
      myOkEnabledListener.consume(patchNameChecker.nameOk());
    }
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public void installOkEnabledListener(final Consumer<Boolean> runnable) {
    myOkEnabledListener = runnable;
  }

  public String getFileName() {
    return myFileNameField.getText();
  }

  public void setFileName(final File file) {
    myFileNameField.setText(file.getPath());
    checkName();
  }

  public boolean isReversePatch() {
    return myReversePatchCheckbox.isSelected();
  }

  public void setReversePatch(boolean reverse) {
    myReversePatchCheckbox.setSelected(reverse);
  }

  public boolean isOkToExecute() {
    return myErrorLabel.getText() == null || myErrorLabel.getText().length() == 0; 
  }

  public String getError() {
    return myErrorLabel.getText() == null ? "" : myErrorLabel.getText();
  }
}