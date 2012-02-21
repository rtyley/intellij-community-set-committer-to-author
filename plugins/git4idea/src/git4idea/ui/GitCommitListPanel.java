/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import git4idea.history.browser.GitCommit;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.List;

/**
 * A table with the list of commits.
 *
 * @author Kirill Likhodedov
 */
public class GitCommitListPanel extends JPanel implements TypeSafeDataProvider {

  private final Project myProject;
  private final List<GitCommit> myCommits;
  private final TableView<GitCommit> myTable;

  public GitCommitListPanel(@NotNull Project project, @NotNull List<GitCommit> commits) {
    myProject = project;
    myCommits = commits;


    myTable = new TableView<GitCommit>();
    updateModel();
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.setStriped(true);

    setLayout(new BorderLayout());
    add(ScrollPaneFactory.createScrollPane(myTable));
  }

  /**
   * Adds a listener that would be called once user selects a commit in the table.
   */
  public void addListSelectionListener(final @NotNull Consumer<GitCommit> listener) {
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        ListSelectionModel lsm = (ListSelectionModel)e.getSource();
        int i = lsm.getMaxSelectionIndex();
        if (i >= 0) {
          listener.consume(myCommits.get(i));
        }
      }
    });
  }

  /**
   * Registers the diff action which will be called when the diff shortcut is pressed in the table.
   */
  public void registerDiffAction(@NotNull AnAction diffAction) {
    diffAction.registerCustomShortcutSet(CommonShortcuts.getDiff(), myTable);
  }

  // Make changes available for diff action
  @Override
  public void calcData(DataKey key, DataSink sink) {
    if (VcsDataKeys.CHANGES.equals(key)) {
      int[] rows = myTable.getSelectedRows();
      if (rows.length != 1) return;
      int row = rows[0];

      GitCommit gitCommit = myCommits.get(row);
      sink.put(key, ArrayUtil.toObjectArray(gitCommit.getChanges(), Change.class));
    }
  }

  @NotNull
  public JComponent getPreferredFocusComponent() {
    return myTable;
  }

  public void clearSelection() {
    myTable.clearSelection();
  }

  public void setCommits(@NotNull List<GitCommit> commits) {
    myCommits.clear();
    myCommits.addAll(commits);
    updateModel();
    myTable.repaint();
  }

  private void updateModel() {
    myTable.setModelAndUpdateColumns(new ListTableModel<GitCommit>(generateColumnsInfo(myCommits), myCommits, 0));
  }

  @NotNull
  private ColumnInfo[] generateColumnsInfo(@NotNull List<GitCommit> commits) {
    ItemAndWidth hash = new ItemAndWidth("", 0);
    ItemAndWidth author = new ItemAndWidth("", 0);
    ItemAndWidth time = new ItemAndWidth("", 0);
    for (GitCommit commit : commits) {
      hash = getMax(hash, getHash(commit));
      author = getMax(author, getAuthor(commit));
      time = getMax(time, getTime(commit));
    }

    return new ColumnInfo[] {
    new GitCommitColumnInfo("Hash", hash.myItem) {
      @Override
      public String valueOf(GitCommit commit) {
        return getHash(commit);
      }
    },
    new ColumnInfo<GitCommit, String>("Subject") {
      @Override
      public String valueOf(GitCommit commit) {
        return commit.getSubject();
      }
    },
    new GitCommitColumnInfo("Author", author.myItem) {
      @Override
      public String valueOf(GitCommit commit) {
        return getAuthor(commit);
      }
    },
    new GitCommitColumnInfo("Author time", time.myItem) {
      @Override
      public String valueOf(GitCommit commit) {
        return getTime(commit);
      }
    }
    };
  }

  private ItemAndWidth getMax(ItemAndWidth current, String candidate) {
    int width = myTable.getFontMetrics(myTable.getFont()).stringWidth(candidate);
    if (width > current.myWidth) {
      return new ItemAndWidth(candidate, width);
    }
    return current;
  }

  private static class ItemAndWidth {
    private final String myItem;
    private final int myWidth;

    private ItemAndWidth(String item, int width) {
      myItem = item;
      myWidth = width;
    }
  }

  private static String getHash(GitCommit commit) {
    return commit.getShortHash().toString();
  }

  private static String getAuthor(GitCommit commit) {
    return commit.getAuthor();
  }

  private static String getTime(GitCommit commit) {
    return DateFormatUtil.formatPrettyDateTime(commit.getAuthorTime());
  }

  private abstract static class GitCommitColumnInfo extends ColumnInfo<GitCommit, String> {

    @NotNull private final String myMaxString;

    public GitCommitColumnInfo(@NotNull String name, @NotNull String maxString) {
      super(name);
      myMaxString = maxString;
    }

    @Override
    public String getMaxStringValue() {
      return myMaxString;
    }

    @Override
    public int getAdditionalWidth() {
      return UIUtil.DEFAULT_HGAP;
    }
  }

}
