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
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.integration.IdeaGateway;
import static com.intellij.history.integration.LocalHistoryBundle.message;
import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.ex.DiffStatusBar;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesTreeList;
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DirectoryHistoryDialog extends HistoryDialog<DirectoryHistoryDialogModel> {
  private ChangesTreeList<Change> myChangesTree;

  public DirectoryHistoryDialog(IdeaGateway gw, VirtualFile f) {
    this(gw, f, true);
  }

  protected DirectoryHistoryDialog(IdeaGateway gw, VirtualFile f, boolean doInit) {
    super(gw, f, doInit);
  }

  @Override
  protected void init() {
    super.init();
    setTitle(myModel.getTitle());
  }

  @Override
  protected DirectoryHistoryDialogModel createModel(LocalVcs vcs) {
    return new DirectoryHistoryDialogModel(myGateway, vcs, myFile);
  }

  @Override
  protected Dimension getInitialSize() {
    return new Dimension(700, 600);
  }

  @Override
  protected JComponent createDiffPanel() {
    initChangesTree();

    JPanel p = new JPanel(new BorderLayout());

    p.add(new DiffStatusBar(DiffStatusBar.DEFAULT_TYPES), BorderLayout.SOUTH);
    p.add(myChangesTree, BorderLayout.CENTER);

    ActionToolbar tb = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, createChangesTreeActions(), true);
    p.add(tb.getComponent(), BorderLayout.NORTH);

    return p;
  }

  private void initChangesTree() {
    myChangesTree = createChangesTree();
    registerChangesTreeActions();
    updateDiffs();
  }

  private ChangesTreeList<Change> createChangesTree() {
    return new ChangesTreeList<Change>(getProject(), Collections.<Change>emptyList(), false, false, null, null) {
      @Override
      protected DefaultTreeModel buildTreeModel(List<Change> cc, ChangeNodeDecorator changeNodeDecorator) {
        return new TreeModelBuilder(getProject(), false).buildModel(cc, changeNodeDecorator);
      }

      @Override
      protected List<Change> getSelectedObjects(ChangesBrowserNode node) {
        return node.getAllChangesUnder();
      }

      protected Change getLeadSelectedObject(final ChangesBrowserNode node) {
        final Object o = node.getUserObject();
        if (o instanceof Change) {
          return (Change) o;
        }
        return null;
      }
    };
  }

  private void registerChangesTreeActions() {
    myChangesTree.setDoubleClickHandler(new Runnable() {
      public void run() {
        ShowDifferenceAction a = new ShowDifferenceAction();
        if (a.isEnabled()) a.perform();
      }
    });
    myChangesTree.installPopupHandler(createChangesTreeActions());
  }

  private ActionGroup createChangesTreeActions() {
    DefaultActionGroup result = new DefaultActionGroup();
    ShowDifferenceAction a = new ShowDifferenceAction();
    a.registerCustomShortcutSet(CommonShortcuts.getDiff(), myChangesTree);
    result.add(a);
    result.add(new RevertSelectionAction());
    return result;
  }

  @Override
  protected void updateDiffs() {
    myChangesTree.setChangesToDisplay(myModel.getChanges());
  }

  @Override
  protected String getHelpId() {
    return "reference.dialogs.localHistory.show.folder";
  }

  private List<DirectoryChange> getSelectedChanges() {
    return (List)myChangesTree.getSelectedChanges();
  }

  private class ShowDifferenceAction extends ActionOnSelection {
    public ShowDifferenceAction() {
      super(message("action.show.difference"), "/actions/diff.png");
    }

    @Override
    protected void performOn(List<DirectoryChange> changes) {
      DiffRequest r = createDifference(getFirstChange(changes).getFileDifferenceModel());
      DiffManager.getInstance().getDiffTool().show(r);
    }

    @Override
    protected boolean isEnabledFor(List<DirectoryChange> changes) {
      DirectoryChange c = getFirstChange(changes);
      return c != null && c.canShowFileDifference();
    }

    private DirectoryChange getFirstChange(List<DirectoryChange> changes) {
      return changes.isEmpty() ? null : changes.get(0);
    }
  }

  private class RevertSelectionAction extends ActionOnSelection {
    public RevertSelectionAction() {
      super(message("action.revert.selection"), "/actions/rollback.png");
    }

    @Override
    protected void performOn(List<DirectoryChange> changes) {
      List<Difference> diffs = new ArrayList<Difference>();
      for (DirectoryChange each : changes) {
        diffs.add(each.getModel().getDifference());
      }
      revert(myModel.createRevisionReverter(diffs));
    }

    @Override
    protected boolean isEnabledFor(List<DirectoryChange> changes) {
      return myModel.isRevertEnabled();
    }
  }

  private abstract class ActionOnSelection extends AnAction {
    public ActionOnSelection(String name, String iconName) {
      super(name, null, IconLoader.getIcon(iconName));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      perform();
    }

    public void perform() {
      performOn(getSelectedChanges());
    }

    protected abstract void performOn(List<DirectoryChange> changes);

    @Override
    public void update(AnActionEvent e) {
      Presentation p = e.getPresentation();
      p.setEnabled(isEnabled());
    }

    public boolean isEnabled() {
      return isEnabledFor(getSelectedChanges());
    }

    protected boolean isEnabledFor(List<DirectoryChange> changes) {
      return true;
    }
  }
}
