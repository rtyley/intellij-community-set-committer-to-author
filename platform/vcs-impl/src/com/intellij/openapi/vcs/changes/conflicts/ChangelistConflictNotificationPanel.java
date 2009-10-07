package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.InplaceButton;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

/**
* @author Dmitry Avdeev
*/
public class ChangelistConflictNotificationPanel extends EditorNotificationPanel {

  private final ChangeList myChangeList;
  private final Change myChange;
  private final VirtualFile myFile;
  private ChangelistConflictTracker myTracker;

  public ChangelistConflictNotificationPanel(ChangelistConflictTracker tracker, VirtualFile file) {

    myTracker = tracker;
    myFile = file;
    final ChangeListManager manager = tracker.getChangeListManager();
    myChange = manager.getChange(file);
    myChangeList = manager.getChangeList(myChange);
    assert myChangeList != null;
    myLabel.setText("File from non-active changelist is modified");
    createActionLabel("Move changes", "move").
      setToolTipText("Move changes to active changelist (" + manager.getDefaultChangeList().getName() + ")");
    createActionLabel("Switch changelist", "switch").
      setToolTipText("Set active changelist to '" + myChangeList.getName() + "'");
    createActionLabel("Ignore", "ignore").
      setToolTipText("Hide this notification");
    setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));

    myLinksPanel.add(new InplaceButton("Show options dialog", IconLoader.getIcon("/general/ideOptions.png"), new ActionListener() {
      public void actionPerformed(ActionEvent e) {

        ShowSettingsUtil.getInstance().editConfigurable(myTracker.getProject(),
                                                        new ChangelistConflictConfigurable((ChangeListManagerImpl)manager));
      }
    }));
  }

  @Override
  protected void executeAction(String actionId) {
    if (actionId.equals("move")) {
      MoveChangesDialog dialog =
        new MoveChangesDialog(myTracker.getProject(), Collections.singletonList(myChange), Collections.singleton(myChangeList),
                              "Move Changes to Active Changelist");
      dialog.show();
      if (dialog.isOK()) {
        ChangelistConflictResolution.MOVE.resolveConflict(myTracker.getProject(), dialog.getSelectedChanges());
      }
    } else if (actionId.equals("switch")) {
      List<Change> changes = Collections.singletonList(myTracker.getChangeListManager().getChange(myFile));
      ChangelistConflictResolution.SWITCH.resolveConflict(myTracker.getProject(), changes);
    } else if (actionId.equals("ignore")) {
      myTracker.ignoreConflict(myFile, true);
    }
  }
}
