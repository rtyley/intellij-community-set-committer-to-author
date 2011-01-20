/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.wholeTree;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author irengrig
 */
public abstract class BasePopupAction extends AnAction implements CustomComponentAction {
  private static final Icon ARROWS_ICON = IconLoader.getIcon("/ide/statusbar_arrows.png");
  protected final JLabel myLabel;
  private final JPanel myPanel;
  private final Project myProject;

  public BasePopupAction(final Project project, final String labeltext) {
    myProject = project;
    myPanel = new JPanel();
    final BoxLayout layout = new BoxLayout(myPanel, BoxLayout.X_AXIS);
    myPanel.setLayout(layout);
    myLabel = new JLabel();
    final JLabel show = new JLabel(labeltext);
    show.setForeground(UIUtil.getInactiveTextColor());
    show.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 1));
    myLabel.setForeground(UIUtil.getInactiveTextColor().darker().darker());
    myPanel.add(show);
    myPanel.add(myLabel);
    myPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 3));
    final JLabel iconLabel = new JLabel(ARROWS_ICON);
    iconLabel.setBorder(BorderFactory.createEmptyBorder(0,0,0,2));
    myPanel.add(iconLabel, myLabel);
    final MouseAdapter mouseAdapter = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        super.mouseEntered(e);
        show.setForeground(UIUtil.getTextAreaForeground());
        myLabel.setForeground(UIUtil.getTextFieldForeground());
      }

      @Override
      public void mouseExited(MouseEvent e) {
        super.mouseExited(e);
        show.setForeground(UIUtil.getInactiveTextColor());
        myLabel.setForeground(UIUtil.getInactiveTextColor().darker().darker());
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        doAction(e);
      }
    };
    myPanel.addMouseListener(mouseAdapter);
  }

  protected void doAction(MouseEvent e) {
    final DefaultActionGroup group = createActionGroup();
    final DataContext parent = DataManager.getInstance().getDataContext((Component) myPanel.getParent());
    final DataContext dataContext = SimpleDataContext.getSimpleContext(PlatformDataKeys.PROJECT.getName(), myProject, parent);
    final ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(null, group, dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
                              new Runnable() {
                                @Override
                                public void run() {
                                  // todo ?
                                }
                              }, 20);
    if (e != null) {
      popup.show(new RelativePoint(e));
    } else {
      final Dimension dimension = popup.getContent().getPreferredSize();
      final Point at = new Point(-dimension.width / 2, -dimension.height);
      popup.show(new RelativePoint(myLabel, at));
    }
  }

  protected abstract DefaultActionGroup createActionGroup();

  @Override
  public void actionPerformed(AnActionEvent e) {
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    return myPanel;
  }
}
