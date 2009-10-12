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

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.ui.SeparatorFactory;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author max
 */
public class CommitLegendPanel {
  private JLabel myModifiedShown;
  private JLabel myModifiedIncluded;
  private JLabel myNewShown;
  private JLabel myNewIncluded;
  private JLabel myDeletedIncluded;
  private JLabel myTotalShown;
  private JLabel myTotalIncluded;
  private JPanel myRootPanel;
  private JLabel myDeletedShown;
  private JPanel myModifiedPanel;
  private JLabel myModifiedLabel;
  private JPanel myNewPanel;
  private JLabel myNewLabel;
  private JPanel myDeletedPanel;
  private JLabel myDeletedLabel;
  private JPanel myHeadingPanel;


  public CommitLegendPanel() {
    final Color background = UIUtil.getListBackground();
    myModifiedPanel.setBackground(background);
    myNewPanel.setBackground(background);
    myDeletedPanel.setBackground(background);

    myModifiedLabel.setForeground(FileStatus.MODIFIED.getColor());
    myModifiedLabel.putClientProperty("Quaqua.Component.visualMargin", new Insets(0, 0, 0, 0));
    myNewLabel.setForeground(FileStatus.ADDED.getColor());
    myNewLabel.putClientProperty("Quaqua.Component.visualMargin", new Insets(0, 0, 0, 0));
    myDeletedLabel.setForeground(FileStatus.DELETED.getColor());
    myDeletedLabel.putClientProperty("Quaqua.Component.visualMargin", new Insets(0, 0, 0, 0));

    boldLabel(myModifiedLabel, true);
    boldLabel(myNewLabel, true);
    boldLabel(myDeletedLabel, true);
  }

  public JComponent getComponent() {
    return myRootPanel;
  }

  public void update(final List<Change> displayedChanges, final List<Change> includedChanges) {
    updateCategory(myTotalShown, myTotalIncluded, displayedChanges, includedChanges, ALL_FILTER);
    updateCategory(myModifiedShown, myModifiedIncluded, displayedChanges, includedChanges, MODIFIED_FILTER);
    updateCategory(myNewShown, myNewIncluded, displayedChanges, includedChanges, NEW_FILTER);
    updateCategory(myDeletedShown, myDeletedIncluded, displayedChanges, includedChanges, DELETED_FILTER);
  }

  private void createUIComponents() {
    myHeadingPanel = (JPanel)SeparatorFactory.createSeparator(VcsBundle.message("commit.legend.summary"), null);
  }

  private interface Filter<T> {
    boolean matches(T item);
  }

  private static final Filter<Change> MODIFIED_FILTER = new Filter<Change>() {
    public boolean matches(final Change item) {
      return item.getType() == Change.Type.MODIFICATION || item.getType() == Change.Type.MOVED;
    }
  };
  private static final Filter<Change> NEW_FILTER = new Filter<Change>() {
    public boolean matches(final Change item) {
      return item.getType() == Change.Type.NEW;
    }
  };
  private static final Filter<Change> DELETED_FILTER = new Filter<Change>() {
    public boolean matches(final Change item) {
      return item.getType() == Change.Type.DELETED;
    }
  };
  private static final Filter<Change> ALL_FILTER = new Filter<Change>() {
    public boolean matches(final Change item) {
      return true;
    }
  };

  private static <T> int countMatchingItems(List<T> items, Filter<T> filter) {
    int count = 0;
    for (T item : items) {
      if (filter.matches(item)) count++;
    }

    return count;
  }

  private static void updateCategory(JLabel totalLabel,
                                     JLabel includedLabel,
                                     List<Change> totalList,
                                     List<Change> includedList,
                                     Filter<Change> filter) {
    int totalCount = countMatchingItems(totalList, filter);
    int includedCount = countMatchingItems(includedList, filter);
    updateLabel(totalLabel, totalCount, false);
    updateLabel(includedLabel, includedCount, totalCount != includedCount);
  }

  private static void updateLabel(JLabel label, int count, boolean bold) {
    label.setText(String.valueOf(count));
    label.setEnabled(bold || count != 0);
    boldLabel(label, bold);
  }

  private static void boldLabel(final JLabel label, final boolean bold) {
    label.setFont(label.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN));
  }
}
