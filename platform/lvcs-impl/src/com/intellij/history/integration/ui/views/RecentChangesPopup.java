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

package com.intellij.history.integration.ui.views;

import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.revisions.RecentChange;
import com.intellij.history.integration.FormatUtil;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class RecentChangesPopup {
  private final IdeaGateway myGateway;
  private final LocalVcs myVcs;

  public RecentChangesPopup(IdeaGateway gw, LocalVcs vcs) {
    myGateway = gw;
    myVcs = vcs;
  }

  public void show() {
    List<RecentChange> cc = myVcs.getRecentChanges();
    if (cc.isEmpty()) {
      String message = LocalHistoryBundle.message("recent.changes.to.changes");
      myGateway.showMessage(message, getTitle());
      return;
    }

    final JList list = new JList(createModel(cc));
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new RecentChangesListCellRenderer());

    Runnable selectAction = new Runnable() {
      public void run() {
        RecentChange c = (RecentChange)list.getSelectedValue();
        showRecentChangeDialog(c);
      }
    };

    showList(list, selectAction);
  }

  private ListModel createModel(List<RecentChange> cc) {
    DefaultListModel m = new DefaultListModel();
    for (RecentChange c : cc) {
      m.addElement(c);
    }
    return m;
  }

  private void showList(JList list, Runnable selectAction) {
    new PopupChooserBuilder(list).
      setTitle(getTitle()).
      setItemChoosenCallback(selectAction).
      createPopup().
      showCenteredInCurrentWindow(myGateway.getProject());
  }

  private void showRecentChangeDialog(RecentChange c) {
    DialogWrapper d = new RecentChangeDialog(myGateway, c);
    d.show();
  }

  private String getTitle() {
    return LocalHistoryBundle.message("recent.changes.popup.title");
  }

  private static class RecentChangesListCellRenderer implements ListCellRenderer {
    private final JPanel myPanel = new JPanel(new BorderLayout());
    private final JLabel myActionLabel = new JLabel("", JLabel.LEFT);
    private final JLabel myDateLabel = new JLabel("", JLabel.RIGHT);
    private final JPanel mySpacePanel = new JPanel();

    public RecentChangesListCellRenderer() {
      myPanel.add(myActionLabel, BorderLayout.WEST);
      myPanel.add(myDateLabel, BorderLayout.EAST);
      myPanel.add(mySpacePanel, BorderLayout.CENTER);

      Dimension d = new Dimension(40, mySpacePanel.getPreferredSize().height);
      mySpacePanel.setMinimumSize(d);
      mySpacePanel.setMaximumSize(d);
      mySpacePanel.setPreferredSize(d);
    }

    public Component getListCellRendererComponent(JList l, Object val, int i, boolean isSelected, boolean cellHasFocus) {
      RecentChange c = (RecentChange)val;
      myActionLabel.setText(c.getChangeName());
      myDateLabel.setText(FormatUtil.formatTimestamp(c.getChange().getTimestamp()));

      updateColors(isSelected);
      return myPanel;
    }

    private void updateColors(boolean isSelected) {
      Color bg = isSelected ? UIUtil.getTableSelectionBackground() : UIUtil.getTableBackground();
      Color fg = isSelected ? UIUtil.getTableSelectionForeground() : UIUtil.getTableForeground();

      setColors(bg, fg, myPanel, myActionLabel, myDateLabel, mySpacePanel);
    }

    private void setColors(Color bg, Color fg, JComponent... cc) {
      for (JComponent c : cc) {
        c.setBackground(bg);
        c.setForeground(fg);
      }
    }
  }
}
