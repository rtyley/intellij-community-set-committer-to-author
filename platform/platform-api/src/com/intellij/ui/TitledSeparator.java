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

package com.intellij.ui;

import javax.swing.*;
import java.awt.*;

/**
 * @author cdr
 */
public class TitledSeparator extends JPanel {
  protected final JLabel myLabel = new JLabel();

  public TitledSeparator() {
    setLayout(new GridBagLayout());
    add(myLabel, new GridBagConstraints(0,0,1,0,0,0,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,8), 0,0));
    JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
    add(separator, new GridBagConstraints(1,0,GridBagConstraints.REMAINDER,0,1,0,GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(3,0,0,5), 0,0));
  }

  public TitledSeparator(String text) {
    this();
    setText(text);
  }

  public String getText() {
    return myLabel.getText();
  }

  public void setText(String text) {
    myLabel.setText(text);
  }

  public void setTitleFont(Font font) {
    myLabel.setFont(font);
  }
}
