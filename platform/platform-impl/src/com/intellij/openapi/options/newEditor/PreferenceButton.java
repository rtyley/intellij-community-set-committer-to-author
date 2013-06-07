/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class PreferenceButton extends JComponent {
  private final String myLabel;
  private final Icon myIcon;

  public PreferenceButton(String label, Icon icon) {
    myLabel = label;
    myIcon = icon;
    if (SystemInfo.isMac) {
      setFont(new Font("Lucida Grande", Font.PLAIN, 11));
    } else {
      setFont(UIUtil.getLabelFont());
    }
    setPreferredSize(new Dimension(100, 70));
    setOpaque(false);
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  @Override
  protected void paintComponent(Graphics g) {
    Color bg = getBackground();
    if (bg == null) {
      bg = UIUtil.getPanelBackground();
    }
    g.setColor(bg);
    if (isOpaque()) {
      g.fillRect(0,0, getWidth() - 1, getHeight()-1);
    }
    final Border border = getBorder();
    final Insets insets = border == null ? new Insets(0,0,0,0) : border.getBorderInsets(this);
    int x = (getWidth() - insets.left - insets.right - myIcon.getIconWidth()) / 2;
    int y = insets.top;
    myIcon.paintIcon(this, g, x, y);
    g.setFont(getFont());
    y += myIcon.getIconHeight();
    final FontMetrics metrics = getFontMetrics(getFont());
    x = (getWidth() - insets.left - insets.right - metrics.stringWidth(myLabel)) / 2;
    y += 1.5 * metrics.getHeight();
    g.setColor(UIUtil.getLabelForeground());
    g.drawString(myLabel, x, y);
  }
}
