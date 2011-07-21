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

import com.intellij.ide.StartupProgress;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class Splash extends JDialog implements StartupProgress {
  private final Icon myImage;
  private final JLabel myLabel;
  private final Color myTextColor;

  public Splash(String imageName, final Color textColor) {
    setUndecorated(true);
    setResizable(false);
    setModal(false);
    setFocusableWindowState(false);

    Icon originalImage = IconLoader.getIcon(imageName);
    myTextColor = textColor;
    myImage = new MyIcon(originalImage, myTextColor);
    myLabel = new JLabel(myImage);
    Container contentPane = getContentPane();
    contentPane.setLayout(new BorderLayout());
    contentPane.add(myLabel, BorderLayout.CENTER);
    Dimension size = getPreferredSize();
    setSize(size);
    pack();
    setLocationRelativeTo(null);
  }

  public void show() {
    super.show();
    toFront();
    myLabel.paintImmediately(0, 0, myImage.getIconWidth(), myImage.getIconHeight());
  }

  @Override
  public void showProgress(String message, float progress) {
    Graphics g = getGraphics();
    UIUtil.applyRenderingHints(g);
    g.setFont(new Font(UIUtil.ARIAL_FONT_NAME, Font.PLAIN, 10));

    int y = getHeight() - 21;
    int brightness = 220;
    g.setColor(new Color(brightness, brightness, brightness));
    int x = 20;
    int progressWidth = (int)(398 * progress);
    g.fillRect(1, y, progressWidth, 20);

    brightness = 240;
    g.setColor(new Color(brightness, brightness, brightness));
    g.fillRect(1 + progressWidth, y, 398 - progressWidth, 20);

    g.setColor(Color.DARK_GRAY);
//    g.setXORMode(Color.WHITE);
    g.drawString(message, x, getHeight() - 8);
  }

  public static boolean showLicenseeInfo(Graphics g, int x, int y, final int height, final Color textColor) {
    if (!ApplicationInfoImpl.getShadowInstance().showLicenseeInfo()) {
      return false;
    }
    LicensingFacade provider = LicensingFacade.getInstance();
    if (provider != null) {
      UIUtil.applyRenderingHints(g);
      g.setFont(new Font(UIUtil.ARIAL_FONT_NAME, Font.BOLD, 11));
      g.setColor(textColor);
      final String licensedToMessage = provider.getLicensedToMessage();
      final List<String> licenseRestrictionsMessages = provider.getLicenseRestrictionsMessages();
      int indent = 20;
      g.drawString(licensedToMessage, x + indent, y + height - 49);
      if (licenseRestrictionsMessages.size() > 0) {
        g.drawString(licenseRestrictionsMessages.get(0), x + indent, y + height - 33);
      }
    }
    return true;
  }

  private static final class MyIcon implements Icon {
    private final Icon myOriginalIcon;
    private final Color myTextColor;

    public MyIcon(Icon originalIcon, Color textColor) {
      myOriginalIcon = originalIcon;
      myTextColor = textColor;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
      yield();
      myOriginalIcon.paintIcon(c, g, x, y);

      showLicenseeInfo(g, x, y, getIconHeight(), myTextColor);
    }

    private static void yield() {
      try {
        Thread.sleep(10);
      }
      catch (InterruptedException ignore) {
      }
    }

    public int getIconWidth() {
      return myOriginalIcon.getIconWidth();
    }

    public int getIconHeight() {
      return myOriginalIcon.getIconHeight();
    }
  }
}
