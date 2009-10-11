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

package com.intellij.refactoring.util;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * @author yole
*/
public class RadioUpDownListener extends KeyAdapter {
  private final JRadioButton[] myRadioButtons;

  public RadioUpDownListener(final JRadioButton... radioButtons) {
    myRadioButtons = radioButtons;
    for (JRadioButton radioButton : radioButtons) {
      radioButton.addKeyListener(this);
    }
  }

  public void keyPressed(final KeyEvent e) {
    final int selected = getSelected();
    if (selected != -1) {
      if (e.getKeyCode() == KeyEvent.VK_UP) {
        int newIdx = selected - 1;
        if (newIdx < 0) newIdx = myRadioButtons.length - 1;
        click(myRadioButtons[newIdx]);
      }
      else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
        int newIdx = selected + 1;
        if (newIdx > myRadioButtons.length - 1) newIdx = 0;
        click(myRadioButtons[newIdx]);
      }
    }
  }

  private int getSelected() {
    for (int i = 0; i < myRadioButtons.length; i++) {
      if (myRadioButtons[i].isSelected()) {
        return i;
      }
    }
    return -1;
  }

  private static void click(final JRadioButton button) {
    if (button.isEnabled()) {
      button.requestFocus();
      button.doClick();
    }
  }
}
