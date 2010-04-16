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

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.ex.DiffStatusBar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesTreeList;
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SearchTextFieldWithStoredHistory;
import com.intellij.util.Consumer;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.history.integration.LocalHistoryBundle.message;

public class DirectoryHistoryDialog extends HistoryDialog<DirectoryHistoryDialogModel> {
  private ChangesTreeList<Change> myChangesTree;
  private ActionToolbar myToolBar;
  private SearchTextField mySearchField;

  public DirectoryHistoryDialog(Project p, IdeaGateway gw, VirtualFile f) {
    this(p, gw, f, true);
  }

  protected DirectoryHistoryDialog(Project p, IdeaGateway gw, VirtualFile f, boolean doInit) {
    super(p, gw, f, doInit);
  }

  @Override
  protected DirectoryHistoryDialogModel createModel(LocalHistoryFacade vcs) {
    return new DirectoryHistoryDialogModel(myProject, myGateway, vcs, myFile);
  }

  @Override
  protected Dimension getInitialSize() {
    return new Dimension(700, 600);
  }

  @Override
  protected JComponent createDiffPanel() {
    initChangesTree();

    JPanel p = new JPanel(new BorderLayout());

    myToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, createChangesTreeActions(), true);
    p.add(myToolBar.getComponent(), BorderLayout.NORTH);

    p.add(myChangesTree, BorderLayout.CENTER);
    p.add(new DiffStatusBar(DiffStatusBar.DEFAULT_TYPES), BorderLayout.SOUTH);

    return p;
  }

  @Override
  protected JComponent createRevisionsList() {
    JPanel result = new JPanel(new BorderLayout());
    result.add(createSearchBox(), BorderLayout.NORTH);
    result.add(super.createRevisionsList(), BorderLayout.CENTER);
    return result;
  }

  private JComponent createSearchBox() {
    mySearchField = new SearchTextFieldWithStoredHistory(getPropertiesKey() + ".searchHistory");
    mySearchField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        scheduleRevisionsUpdate(new Consumer<DirectoryHistoryDialogModel>() {
          public void consume(DirectoryHistoryDialogModel m) {
            m.setFilter(mySearchField.getText());
          }
        });
      }
    });
    
    new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        mySearchField.requestFocusInWindow();
      }
    }.registerCustomShortcutSet(CommonShortcuts.getFind(), mySplitter, this); // a little hack
    
    return mySearchField;
  }

  private void initChangesTree() {
    myChangesTree = createChangesTree();
    myChangesTree.setDoubleClickHandler(new Runnable() {
      public void run() {
        new ShowDifferenceAction().performIfEnabled();
      }
    });
    myChangesTree.installPopupHandler(createChangesTreeActions());
  }

  private ChangesTreeList<Change> createChangesTree() {
    return new ChangesTreeList<Change>(myProject, Collections.<Change>emptyList(), false, false, null, null) {
      @Override
      protected DefaultTreeModel buildTreeModel(List<Change> cc, ChangeNodeDecorator changeNodeDecorator) {
        return new TreeModelBuilder(myProject, false).buildModel(cc, changeNodeDecorator);
      }

      @Override
      protected List<Change> getSelectedObjects(ChangesBrowserNode node) {
        return node.getAllChangesUnder();
      }

      protected Change getLeadSelectedObject(final ChangesBrowserNode node) {
        final Object o = node.getUserObject();
        if (o instanceof Change) {
          return (Change)o;
        }
        return null;
      }
    };
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
  protected void updateActions() {
    super.updateActions();
    myToolBar.updateActionsImmediately();
  }

  @Override
  protected Runnable doUpdateDiffs(DirectoryHistoryDialogModel model) {
    final List<Change> changes = model.getChanges();
    return new Runnable() {
      public void run() {
        myChangesTree.setChangesToDisplay(changes);
      }
    };
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
    protected void doPerform(DirectoryHistoryDialogModel model, List<DirectoryChange> changes) {
      DiffRequest r = createDifference(getFirstChange(changes).getFileDifferenceModel());
      DiffManager.getInstance().getDiffTool().show(r);
    }

    @Override
    protected boolean isEnabledFor(DirectoryHistoryDialogModel model, List<DirectoryChange> changes) {
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
    protected void doPerform(DirectoryHistoryDialogModel model, List<DirectoryChange> changes) {
      List<Difference> diffs = new ArrayList<Difference>();
      for (DirectoryChange each : changes) {
        diffs.add(each.getModel().getDifference());
      }
      revert(model.createRevisionReverter(diffs));
    }

    @Override
    protected boolean isEnabledFor(DirectoryHistoryDialogModel model, List<DirectoryChange> changes) {
      return model.isRevertEnabled();
    }
  }

  private abstract class ActionOnSelection extends MyAction {
    public ActionOnSelection(String name, String iconName) {
      super(name, null, IconLoader.getIcon(iconName));
    }

    @Override
    protected void doPerform(DirectoryHistoryDialogModel model) {
      doPerform(model, getSelectedChanges());
    }

    protected abstract void doPerform(DirectoryHistoryDialogModel model, List<DirectoryChange> changes);


    @Override
    protected boolean isEnabled(DirectoryHistoryDialogModel model) {
      final List<DirectoryChange> changes = getSelectedChanges();
      if (changes.isEmpty()) return false;
      return isEnabledFor(model, changes);
    }

    protected boolean isEnabledFor(DirectoryHistoryDialogModel model, List<DirectoryChange> changes) {
      return true;
    }
  }
}
