/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.tasks.actions;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.binding.BindControl;
import com.intellij.openapi.options.binding.ControlBinder;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.tasks.*;
import com.intellij.tasks.config.TaskRepositoriesConfigurable;
import com.intellij.tasks.impl.TaskCompletionContributor;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.LanguageTextField;
import com.intellij.util.Consumer;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;

/**
 * @author Dmitry Avdeev
 */
public class ActivateTaskDialog extends DialogWrapper {

  private JPanel myPanel;

  @BindControl(value = "clearContext", instant = true)
  private JCheckBox myClearContext;
  @BindControl(value = "createChangelist", instant = true)
  private JCheckBox myCreateChangelist;
  private EditorTextField myTaskName;
  private JCheckBox myMarkAsInProgressBox;
  private HyperlinkLabel myServers;
  private JPanel myEditorPanel;
  private JLabel myNameLabel;

  private final Project myProject;
  private Task mySelectedTask;
  private boolean myVcsEnabled;
  private boolean myMarkAsInProgress;
  private AsyncProcessIcon myUpdateIcon;
  private JLabel myUpdateLabel;

  protected ActivateTaskDialog(Project project) {

    super(project, true);
    myProject = project;
    setTitle("Open Task");

    myTaskName = new LanguageTextField(PlainTextLanguage.INSTANCE, project, "");
    myEditorPanel.add(myTaskName);

    TaskCompletionContributor.installCompletion(myTaskName.getDocument(), myProject, new Consumer<Task>() {
      @Override
      public void consume(Task task) {
        mySelectedTask = task;
        taskChanged();
      }
    }, true);
    
    myTaskName.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        taskChanged();        
      }
    });

    final Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    final Shortcut[] shortcuts = keymap.getShortcuts("CodeCompletion");
    if (shortcuts.length > 0) {
      myNameLabel.setText("Enter task name or press " + KeymapUtil.getShortcutText(shortcuts[0]) + " to choose an existing task:");
    }
    myNameLabel.setLabelFor(myTaskName);
    
    TaskManagerImpl manager = (TaskManagerImpl)TaskManager.getManager(project);
    ControlBinder binder = new ControlBinder(manager.getState());
    binder.bindAnnotations(this);
    binder.reset();

    myVcsEnabled = manager.isVcsEnabled();

    myMarkAsInProgress = manager.getState().markAsInProgress;
    myMarkAsInProgressBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (isMarkAsInProgressApplicable()) {
          myMarkAsInProgress = myMarkAsInProgressBox.isSelected();
        }
      }
    });
    taskChanged();

    init();

    if (manager.getState().updateEnabled) {
      manager.updateIssues(new Runnable() {
        public void run() {
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              myUpdateIcon.suspend();
              myUpdateIcon.setVisible(false);
              myUpdateLabel.setText("");
            }
          });
        }
      });
    } else {
      myUpdateIcon.setVisible(false);
      myUpdateLabel.setText("");
    }
  }

  @Override
  protected JComponent createSouthPanel() {
    JComponent panel = super.createSouthPanel();
    JPanel updatePanel = new JPanel(new BorderLayout());
    myUpdateIcon = new AsyncProcessIcon("task update");
    updatePanel.add(myUpdateIcon, BorderLayout.WEST);
    myUpdateLabel = new JLabel(" Updating...");
    updatePanel.add(myUpdateLabel);
    assert panel != null;
    panel.add(updatePanel, BorderLayout.WEST);
    return panel;
  }

  private void taskChanged() {
    TaskManagerImpl taskManager = (TaskManagerImpl)TaskManager.getManager(myProject);
    Task task = getSelectedTask();

    myMarkAsInProgressBox.setVisible(false);
    for (TaskRepository repository : taskManager.getAllRepositories()) {
      if (repository.getRepositoryType().getPossibleTaskStates().contains(TaskState.IN_PROGRESS)) {
        myMarkAsInProgressBox.setVisible(true);
        break;
      }
    }

    if (myMarkAsInProgressBox.isVisible()) {
      myMarkAsInProgressBox.setEnabled(false);
      if (task != null) {
        TaskRepository repository = task.getRepository();
        if (repository != null && repository.getRepositoryType().getPossibleTaskStates().contains(TaskState.IN_PROGRESS)) {
          myMarkAsInProgressBox.setEnabled(true);
        }
      }
    }
    myMarkAsInProgressBox.setSelected(myMarkAsInProgress && isMarkAsInProgressApplicable());

    // refresh change lists
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    for (Iterator<ChangeListInfo> it = taskManager.getOpenChangelists(task).iterator(); it.hasNext();) {
      ChangeListInfo changeListInfo = it.next();
      if (changeListManager.getChangeList(changeListInfo.id) == null) {
        it.remove();
      }
    }

    if (!myVcsEnabled) {
      myCreateChangelist.setEnabled(false);
      myCreateChangelist.setSelected(false);
    } else if (task != null && !taskManager.getOpenChangelists(task).isEmpty()) {
      myCreateChangelist.setSelected(true);
      myCreateChangelist.setEnabled(false);
    } else {
      myCreateChangelist.setSelected(taskManager.getState().createChangelist);
      myCreateChangelist.setEnabled(true);
    }

    setOKActionEnabled(isOKActionEnabled());
  }

  @Override
  protected void doOKAction() {
    TaskManagerImpl manager = (TaskManagerImpl)TaskManager.getManager(myProject);
    if (mySelectedTask == null) {
      String taskName = getTaskName();

      String lastId = null;
      for (final TaskRepository repository : manager.getAllRepositories()) {
        final String id = repository.extractId(taskName);
        if (id != null) {
          lastId = id;
          ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
            public void run() {
              try {
                mySelectedTask = repository.findTask(id);
              }
              catch (Exception e) {
                //
              }
            }
          }, "Getting " + id + " from " + repository.getPresentableName() + "...", true, myProject);
        }
        if (mySelectedTask != null) {
          break;
        }
      }
      if (lastId == null) {
        mySelectedTask = manager.createLocalTask(taskName);
      }
      else if (mySelectedTask == null) {
        if (Messages.showOkCancelDialog(myProject,
                            "Issue " + lastId + " not found.\n" +
                            "Do you want to create local task?",
                            "Issue Not Found",
                            CommonBundle.getNoButtonText(), CommonBundle.getYesButtonText(),
                            Messages.getQuestionIcon()) == DialogWrapper.OK_EXIT_CODE) {
          return;
        }
        mySelectedTask = manager.createLocalTask(taskName);
      }
    }
    if (isMarkAsInProgressApplicable()) {
      manager.getState().markAsInProgress = myMarkAsInProgress;
    }
    super.doOKAction();
  }

  @Nullable
  public Task getSelectedTask() {
    return mySelectedTask;
  }

  public boolean isClearContext() {
    return myClearContext.isSelected();
  }

  public boolean isCreateChangelist() {
    return myCreateChangelist.isSelected();
  }

  boolean isMarkAsInProgress() {
    return isMarkAsInProgressApplicable() && myMarkAsInProgressBox.isEnabled();
  }

  private boolean isMarkAsInProgressApplicable() {
    return myMarkAsInProgressBox.isSelected() && myMarkAsInProgressBox.isVisible();
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "ActivateTaskDialog";
  }

  @Override
  public boolean isOKActionEnabled() {
    return !StringUtil.isEmptyOrSpaces(getTaskName());
  }

  private String getTaskName() {
    return myTaskName.getText();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTaskName;
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private void createUIComponents() {
    myServers = new HyperlinkLabel("Configure");
    myServers.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        TaskRepositoriesConfigurable configurable = new TaskRepositoriesConfigurable(myProject);
        if (ShowSettingsUtil.getInstance().editConfigurable(myProject, configurable)) {
          taskChanged();
        }
      }
    });
  }
}
