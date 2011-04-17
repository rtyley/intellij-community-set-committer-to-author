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
package git4idea.ui;

import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import git4idea.GitRemote;
import git4idea.GitVcs;
import git4idea.config.GitConfigUtil;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * Utilities for git plugin user interface
 */
public class GitUIUtil {
  /**
   * Text containing in the label when there is no current branch
   */
  public static final String NO_CURRENT_BRANCH = GitBundle.getString("common.no.active.branch");

  /**
   * A private constructor for utility class
   */
  private GitUIUtil() { }

  public static void notifyMessages(Project project, @Nullable String title, @Nullable String description, NotificationType type, boolean important, @Nullable Collection<String> messages) {
    if (StringUtil.isEmptyOrSpaces(title)) {
      title = description;
    }
    String desc = (description != null ? description.replace("\n", "<br/>") : "");
    if (messages != null && !messages.isEmpty()) {
      desc += "<hr/>" + StringUtil.join(messages, "<br/>");
    }
    String id = important ? GitVcs.IMPORTANT_ERROR_NOTIFICATION : GitVcs.NOTIFICATION_GROUP_ID;
    Notifications.Bus.notify(new Notification(id, title, desc, type), project);
  }

  public static void notifyMessage(Project project, @Nullable String title, @Nullable String description, NotificationType type, boolean important, @Nullable Collection<VcsException> errors) {
    Collection<String> errorMessages;
    if (errors == null) {
      errorMessages = null;
    } else {
      errorMessages = new HashSet<String>(errors.size());
      for (VcsException error : errors) {
        for (String message : error.getMessages()) {
          errorMessages.add(message.replace("\n", "<br/>"));
        }
      }
    }
    notifyMessages(project, title, description, type, important, errorMessages);
  }

  public static void notifyError(Project project, String title, String description, boolean important, @Nullable VcsException error) {
    notifyMessage(project, title, description, NotificationType.ERROR, important, Collections.singleton(error));
  }

  /**
   * Splits the given VcsExceptions to one string. Exceptions are separated by &lt;br/&gt;
   * Line separator is also replaced by &lt;br/&gt;
   */
  public static @NotNull String stringifyErrors(@Nullable Collection<VcsException> errors) {
    if (errors == null) {
      return "";
    }
    StringBuilder content = new StringBuilder();
    for (VcsException e : errors) {
      for (String message : e.getMessages()) {
        content.append(message.replace("\n", "<br/>")).append("<br/>");
      }
    }
    return content.toString();
  }

  /**
   * Displays a "success"-notification.
   */
  public static void notifySuccess(Project project, String title, String description) {
    Notifications.Bus.notify(new Notification(GitVcs.NOTIFICATION_GROUP_ID, title, description, NotificationType.INFORMATION), project);
  }

  public static void notifyError(Project project, String title, String description) {
    notifyMessage(project, title, description, NotificationType.ERROR, false, null);
  }

  public static void notifyImportantError(Project project, String title, String description) {
    notifyMessage(project, title, description, NotificationType.ERROR, true, null);
  }

  public static void notifyGitErrors(Project project, String title, String description, Collection<VcsException> gitErrors) {
    StringBuilder content = new StringBuilder();
    if (!StringUtil.isEmptyOrSpaces(description)) {
      content.append(description);
    }
    if (!gitErrors.isEmpty()) {
      content.append("<br/>");
    }
    for (VcsException e : gitErrors) {
      content.append(e.getLocalizedMessage()).append("<br/>");
    }
    notifyError(project, title, content.toString());
  }

  /**
   * @return a list cell renderer for virtual files (it renders presentable URL)
   * @param listCellRenderer
   */
  public static ListCellRenderer getVirtualFileListCellRenderer(final ListCellRenderer listCellRenderer) {
    return new ListCellRendererWrapper<VirtualFile>(listCellRenderer) {
      @Override
      public void customize(final JList list, final VirtualFile file, final int index, final boolean selected, final boolean hasFocus) {
        setText(file == null || !file.isValid() ? "(invalid)" : file.getPresentableUrl());
      }
    };
  }

  /**
   * Get text field from combobox
   *
   * @param comboBox a combobox to examine
   * @return the text field reference
   */
  public static JTextField getTextField(JComboBox comboBox) {
    return (JTextField)comboBox.getEditor().getEditorComponent();
  }

  /**
   * Create list cell renderer for remotes. It shows both name and url and highlights the default
   * remote for the branch with bold.
   *
   *
   * @param defaultRemote a default remote
   * @param fetchUrl      if true, the fetch url is shown
   * @param listCellRenderer
   * @return a list cell renderer for virtual files (it renders presentable URL
   */
  public static ListCellRenderer getGitRemoteListCellRenderer(final String defaultRemote, final boolean fetchUrl,
                                                              final ListCellRenderer listCellRenderer) {
    return new ListCellRendererWrapper<GitRemote>(listCellRenderer) {
      @Override
      public void customize(final JList list, final GitRemote remote, final int index, final boolean selected, final boolean hasFocus) {
        final String text;
        if (remote == null) {
          text = GitBundle.getString("util.remote.renderer.none");
        }
        else if (".".equals(remote.name())) {
          text = GitBundle.getString("util.remote.renderer.self");
        }
        else {
          String key;
          if (defaultRemote != null && defaultRemote.equals(remote.name())) {
            key = "util.remote.renderer.default";
          }
          else {
            key = "util.remote.renderer.normal";
          }
          text = GitBundle.message(key, remote.name(), fetchUrl ? remote.fetchUrl() : remote.pushUrl());
        }
        setText(text);
      }
    };
  }

  /**
   * Setup root chooser with specified elements and link selection to the current branch label.
   *
   * @param project            a context project
   * @param roots              git roots for the project
   * @param defaultRoot        a default root
   * @param gitRootChooser     git root selector
   * @param currentBranchLabel current branch label (might be null)
   */
  public static void setupRootChooser(@NotNull final Project project,
                                      @NotNull final List<VirtualFile> roots,
                                      @Nullable final VirtualFile defaultRoot,
                                      @NotNull final JComboBox gitRootChooser,
                                      @Nullable final JLabel currentBranchLabel) {
    for (VirtualFile root : roots) {
      gitRootChooser.addItem(root);
    }
    gitRootChooser.setRenderer(getVirtualFileListCellRenderer(gitRootChooser.getRenderer()));
    gitRootChooser.setSelectedItem(defaultRoot != null ? defaultRoot : roots.get(0));
    if (currentBranchLabel != null) {
      final ActionListener listener = new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          try {
            VirtualFile root = (VirtualFile)gitRootChooser.getSelectedItem();
            assert root != null : "The root must not be null";
            GitBranch current = GitBranch.current(project, root);
            if (current == null) {
              currentBranchLabel.setText(NO_CURRENT_BRANCH);
            }
            else {
              currentBranchLabel.setText(current.getName());
            }
          }
          catch (VcsException ex) {
            GitVcs.getInstance(project).showErrors(Collections.singletonList(ex), GitBundle.getString("merge.retrieving.branches"));
          }
        }
      };
      listener.actionPerformed(null);
      gitRootChooser.addActionListener(listener);
    }
  }

  /**
   * Get root from the chooser
   *
   * @param gitRootChooser the chooser constructed with {@link #setupRootChooser(Project, List, VirtualFile, JComboBox, JLabel)}.
   * @return the current selection
   */
  public static VirtualFile getRootFromRootChooser(JComboBox gitRootChooser) {
    return (VirtualFile)gitRootChooser.getSelectedItem();
  }

  /**
   * Show error associated with the specified operation
   *
   * @param project   the project
   * @param ex        the exception
   * @param operation the operation name
   */
  public static void showOperationError(final Project project, final VcsException ex, @NonNls @NotNull final String operation) {
    showOperationError(project, operation, ex.getMessage());
  }

  /**
   * Show errors associated with the specified operation
   *
   * @param project   the project
   * @param exs       the exceptions to show
   * @param operation the operation name
   */
  public static void showOperationErrors(final Project project,
                                         final Collection<VcsException> exs,
                                         @NonNls @NotNull final String operation) {
    if (exs.size() == 1) {
      //noinspection ThrowableResultOfMethodCallIgnored
      showOperationError(project, operation, exs.iterator().next().getMessage());
    }
    else if (exs.size() > 1) {
      // TODO use dialog in order to show big messages
      StringBuilder b = new StringBuilder();
      for (VcsException ex : exs) {
        b.append(GitBundle.message("errors.message.item", ex.getMessage()));
      }
      showOperationError(project, operation, GitBundle.message("errors.message", b.toString()));
    }
  }

  /**
   * Show error associated with the specified operation
   *
   * @param project   the project
   * @param message   the error description
   * @param operation the operation name
   */
  public static void showOperationError(final Project project, final String operation, final String message) {
    Messages.showErrorDialog(project, message, GitBundle.message("error.occurred.during", operation));
  }

  /**
   * Show errors on the tab
   *
   * @param project the context project
   * @param title   the operation title
   * @param errors  the errors to display
   */
  public static void showTabErrors(Project project, String title, List<VcsException> errors) {
    AbstractVcsHelper.getInstance(project).showErrors(errors, title);
  }

  /**
   * Setup remotes combobox. The default remote for the current branch is selected by default.
   * This method gets current branch for the project.
   *
   * @param project        the project
   * @param root           the git root
   * @param remoteCombobox the combobox to update
   * @param fetchUrl       if true, the fetch url is shown instead of push url
   */
  public static void setupRemotes(final Project project, final VirtualFile root, final JComboBox remoteCombobox, final boolean fetchUrl) {
    GitBranch gitBranch = null;
    try {
      gitBranch = GitBranch.current(project, root);
    }
    catch (VcsException ex) {
      // ignore error
    }
    final String branch = gitBranch != null ? gitBranch.getName() : null;
    setupRemotes(project, root, branch, remoteCombobox, fetchUrl);

  }

  /**
   * Setup remotes combobox. The default remote for the current branch is selected by default.
   *
   * @param project        the project
   * @param root           the git root
   * @param currentBranch  the current branch
   * @param remoteCombobox the combobox to update
   * @param fetchUrl       if true, the fetch url is shown for remotes, push otherwise
   */
  public static void setupRemotes(final Project project,
                                  final VirtualFile root,
                                  final String currentBranch,
                                  final JComboBox remoteCombobox,
                                  final boolean fetchUrl) {
    try {
      List<GitRemote> remotes = GitRemote.list(project, root);
      String remote = null;
      if (currentBranch != null) {
        remote = GitConfigUtil.getValue(project, root, "branch." + currentBranch + ".remote");
      }
      remoteCombobox.setRenderer(getGitRemoteListCellRenderer(remote, fetchUrl, remoteCombobox.getRenderer()));
      GitRemote toSelect = null;
      remoteCombobox.removeAllItems();
      for (GitRemote r : remotes) {
        remoteCombobox.addItem(r);
        if (r.name().equals(remote)) {
          toSelect = r;
        }
      }
      if (toSelect != null) {
        remoteCombobox.setSelectedItem(toSelect);
      }
    }
    catch (VcsException e) {
      GitVcs.getInstance(project).showErrors(Collections.singletonList(e), GitBundle.getString("pull.retrieving.remotes"));
    }
  }

  /**
   * Checks state of the {@code checked} checkbox and if state is {@code checkedState} than to disable {@code changed}
   * checkbox and change its state to {@code impliedState}. When the {@code checked} checkbox changes states to other state,
   * than enable {@code changed} and restore its state. Note that the each checkbox should be implied by only one other checkbox.
   *
   * @param checked      the checkbox to monitor
   * @param checkedState the state that triggers disabling changed state
   * @param changed      the checkbox to change
   * @param impliedState the implied state of checkbox
   */
  public static void imply(final JCheckBox checked, final boolean checkedState, final JCheckBox changed, final boolean impliedState) {
    ActionListener l = new ActionListener() {
      Boolean previousState;

      public void actionPerformed(ActionEvent e) {
        if (checked.isSelected() == checkedState) {
          if (previousState == null) {
            previousState = changed.isSelected();
          }
          changed.setEnabled(false);
          changed.setSelected(impliedState);
        }
        else {
          changed.setEnabled(true);
          if (previousState != null) {
            changed.setSelected(previousState);
            previousState = null;
          }
        }
      }
    };
    checked.addActionListener(l);
    l.actionPerformed(null);
  }

  /**
   * Declares states for two checkboxes to be mutually exclusive. When one of the checkboxes goes to the specified state, other is
   * disabled and forced into reverse of the state (to prevent very fast users from selecting incorrect state or incorrect
   * initial configuration).
   *
   * @param first       the first checkbox
   * @param firstState  the state of the first checkbox
   * @param second      the second checkbox
   * @param secondState the state of the second checkbox
   */
  public static void exclusive(final JCheckBox first, final boolean firstState, final JCheckBox second, final boolean secondState) {
    ActionListener l = new ActionListener() {
      /**
       * One way check for the condition
       * @param checked the first to check
       * @param checkedState the state to match
       * @param changed the changed control
       * @param impliedState the implied state
       */
      private void check(final JCheckBox checked, final boolean checkedState, final JCheckBox changed, final boolean impliedState) {
        if (checked.isSelected() == checkedState) {
          changed.setSelected(impliedState);
          changed.setEnabled(false);
        }
        else {
          changed.setEnabled(true);
        }
      }

      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent e) {
        check(first, firstState, second, !secondState);
        check(second, secondState, first, !firstState);
      }
    };
    first.addActionListener(l);
    second.addActionListener(l);
    l.actionPerformed(null);
  }

  /**
   * Checks state of the {@code checked} checkbox and if state is {@code checkedState} than to disable {@code changed}
   * text field and clean it. When the {@code checked} checkbox changes states to other state,
   * than enable {@code changed} and restore its state. Note that the each text field should be implied by
   * only one other checkbox.
   *
   * @param checked      the checkbox to monitor
   * @param checkedState the state that triggers disabling changed state
   * @param changed      the checkbox to change
   */
  public static void implyDisabled(final JCheckBox checked, final boolean checkedState, final JTextField changed) {
    ActionListener l = new ActionListener() {
      String previousState;

      public void actionPerformed(ActionEvent e) {
        if (checked.isSelected() == checkedState) {
          if (previousState == null) {
            previousState = changed.getText();
          }
          changed.setEnabled(false);
          changed.setText("");
        }
        else {
          changed.setEnabled(true);
          if (previousState != null) {
            changed.setText(previousState);
            previousState = null;
          }
        }
      }
    };
    checked.addActionListener(l);
    l.actionPerformed(null);
  }

  /**
   * Handles a low-level Git execution exception.
   * Checks that Git executable is valid. If it is not, then shows proper notification with an option to fix the path to Git.
   * If it's valid, then we don't know what could happen and just display the general error notification.
   */
  public static void checkGitExecutableAndShowNotification(final Project project, VcsException e) {
    if (GitVcs.getInstance(project).getExecutableValidator().checkExecutableAndNotifyIfNeeded()) {
      Notification notification = new Notification(GitVcs.NOTIFICATION_GROUP_ID, GitBundle.getString("general.error"), e.getLocalizedMessage(), NotificationType.ERROR);
      Notifications.Bus.notify(notification, project);
    }
  }
}
