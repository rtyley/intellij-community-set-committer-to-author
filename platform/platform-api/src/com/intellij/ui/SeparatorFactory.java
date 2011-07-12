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

import com.intellij.util.ui.UIUtil;
import com.jgoodies.forms.factories.DefaultComponentFactory;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class SeparatorFactory {
  private SeparatorFactory() {
  }

  public static JComponent createSeparator(String textWithMnemonic, @Nullable JComponent labelFor) {
    final JComponent separator = DefaultComponentFactory.getInstance().createSeparator(textWithMnemonic);
    if (labelFor != null) {
      ((JLabel) separator.getComponent(0)).setLabelFor(labelFor);
    }
    separator.setBorder(IdeBorderFactory.createEmptyBorder(3, 0, 5, 5));
    return separator;
  }

  public static JComponent createSeparatorWithBoldTitle(String textWithMnemonic, @Nullable JComponent labelFor) {
    Font oldFont = UIManager.getFont("TitledBorder.font");
    UIManager.put("TitledBorder.font", UIUtil.getBorderFont());
    final JComponent separator = DefaultComponentFactory.getInstance().createSeparator(textWithMnemonic);
    UIManager.put("TitledBorder.font", oldFont);
    if (labelFor != null) {
      ((JLabel) separator.getComponent(0)).setLabelFor(labelFor);
    }
    separator.setBorder(IdeBorderFactory.createEmptyBorder(3, 0, 5, 5));
    return separator;
  }
}