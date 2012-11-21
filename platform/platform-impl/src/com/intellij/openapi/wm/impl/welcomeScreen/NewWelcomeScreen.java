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

/*
 * @author max
 */
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.updateSettings.impl.CheckForUpdateAction;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class NewWelcomeScreen extends JPanel implements WelcomeScreen {

  public NewWelcomeScreen(JRootPane rootPane) {
    super(new BorderLayout());
    add(createHeaderPanel(), BorderLayout.NORTH);
    add(createFooterPanel(), BorderLayout.SOUTH);
    add(createInnerPanel(), BorderLayout.CENTER);
  }

  private WelcomePane createInnerPanel() {
    WelcomeScreenGroup root = new WelcomeScreenGroup(null, "Root");

    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup quickStart = (ActionGroup)actionManager.getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART);
    for (AnAction child : quickStart.getChildren(null)) {
      root.add(child);
    }

    root.add(buildRootGroup(AllIcons.General.Configure, "Configure", IdeActions.GROUP_WELCOME_SCREEN_CONFIGURE));
    root.add(buildRootGroup(AllIcons.General.ReadHelp, "Docs and How-Tos", IdeActions.GROUP_WELCOME_SCREEN_DOC));

    return new WelcomePane(root);
  }

  private WelcomeScreenGroup buildRootGroup(Icon groupIcon, String groupText, String groupId) {
    WelcomeScreenGroup docs = new WelcomeScreenGroup(groupIcon, groupText);
    ActionGroup docsActions = (ActionGroup)ActionManager.getInstance().getAction(groupId);
    for (AnAction child : docsActions.getChildren(null)) {
      docs.add(child);
    }
    return docs;
  }

  private JPanel createFooterPanel() {
    JLabel versionLabel = new JLabel(ApplicationNamesInfo.getInstance().getFullProductName() +
                             " " +
                             ApplicationInfo.getInstance().getFullVersion() +
                             " Build " +
                             ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode());
    makeSmallFont(versionLabel);
    versionLabel.setForeground(WelcomeScreenColors.FOOTER_FOREGROUND);

    JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    footerPanel.setBackground(WelcomeScreenColors.FOOTER_BACKGROUND);
    footerPanel.setBorder(new EmptyBorder(2, 5, 2, 5) {
      @Override
      public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        g.setColor(WelcomeScreenColors.BORDER_COLOR);
        g.drawLine(x, y, x + width, y);
      }
    });
    footerPanel.add(versionLabel);
    footerPanel.add(makeSmallFont(new JLabel(".  ")));
    footerPanel.add(makeSmallFont(new LinkLabel("Check", null, new LinkListener() {
      @Override
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        CheckForUpdateAction.actionPerformed(null, true, null, UpdateSettings.getInstance());
      }
    })));
    footerPanel.add(makeSmallFont(new JLabel(" for updates now.")));
    return footerPanel;
  }

  private static JLabel makeSmallFont(JLabel label) {
    label.setFont(label.getFont().deriveFont((float)10));
    return label;
  }

  private JPanel createHeaderPanel() {
    JPanel header = new JPanel(new BorderLayout());
    JLabel welcome = new JLabel("Welcome to " + ApplicationNamesInfo.getInstance().getFullProductName(), AllIcons.General.Logo_welcomeScreen, SwingConstants.LEFT);
    welcome.setBorder(new EmptyBorder(10, 15, 10, 15));
    welcome.setFont(welcome.getFont().deriveFont((float) 32));
    welcome.setIconTextGap(20);
    welcome.setForeground(WelcomeScreenColors.WELCOME_HEADER_FOREGROUND);
    header.add(welcome);
    header.setBackground(WelcomeScreenColors.WELCOME_HEADER_BACKGROUND);

    header.setBorder(new BottomLineBorder());
    return header;
  }

  @Override
  public JComponent getWelcomePanel() {
    return this;
  }

  @Override
  public void dispose() {
  }

  private static class WelcomeScreenGroup extends DefaultActionGroup {
    private WelcomeScreenGroup(Icon icon, String text, AnAction... actions) {
      super(text, true);
      for (AnAction action : actions) {
        add(action);
      }

      getTemplatePresentation().setText(text);
      getTemplatePresentation().setIcon(icon);
    }
  }
}
