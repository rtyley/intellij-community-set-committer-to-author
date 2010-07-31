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
package git4idea.checkout.branches;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.GitCommand;
import git4idea.commands.GitHandlerUtil;
import git4idea.commands.GitLineHandler;
import git4idea.ui.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;

/**
 * The git branches widget
 */
public class GitBranchesWidget extends JLabel implements CustomStatusBarWidget {
  /**
   * The logger
   */
  private static final Logger LOG = Logger.getInstance(GitBranchesWidget.class.getName());

  /**
   * The ID of the widget
   */
  public static final String ID = "git4idea.BranchConfigurations";
  /**
   * The listener
   */
  final GitBranchConfigurationsListener myConfigurationsListener;
  /**
   * The project
   */
  final Project myProject;
  /**
   * The configurations instance
   */
  private final GitBranchConfigurations myConfigurations;
  /**
   * The selectable configurations. Null if invalidated or non-initialized
   */
  private AnAction[] mySelectableConfigurations;
  /**
   * The candidate remote configurations. Null if invalidated or non-initialized
   */
  private AnAction[] myRemoveConfigurations;
  /**
   * The status bar
   */
  private StatusBar myStatusBar;
  /**
   * If true, the popup is enabled
   */
  private boolean myPopupEnabled = false;
  /**
   * The action group for pop up
   */
  private DefaultActionGroup myPopupActionGroup;
  /**
   * The current popup
   */
  private ListPopup myPopup;
  /**
   * The default foreground color
   */
  private final Color myDefaultForeground;


  /**
   * The constructor
   *
   * @param project the project instance
   */
  public GitBranchesWidget(Project project, GitBranchConfigurations configurations) {
    myProject = project;
    setBorder(WidgetBorder.INSTANCE);
    //setBorder(BorderFactory.createEtchedBorder());
    myConfigurations = configurations;
    myDefaultForeground = getForeground();
    myConfigurationsListener = new MyGitBranchConfigurationsListener();
    myConfigurations.addConfigurationListener(myConfigurationsListener);
    setIcon(IconLoader.findIcon("/icons/branch.png", getClass()));
    Disposer.register(myConfigurations, this);
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        showPopup();
      }
    });
    updateLabel();
  }

  /**
   * Create and install widget
   *
   * @param project        the context project
   * @param configurations the configurations to use
   * @return the action that uninstalls widget
   */
  static Runnable install(final Project project, final GitBranchConfigurations configurations) {
    final Ref<GitBranchesWidget> widget = new Ref<GitBranchesWidget>();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
        if (statusBar != null) {
          final GitBranchesWidget w = new GitBranchesWidget(project, configurations);
          statusBar.addWidget(w, "after InsertOverwrite", project);
          widget.set(w);
        }
      }
    });
    return new Runnable() {
      @Override
      public void run() {
        if (widget.get() != null) {
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            @Override
            public void run() {
              Disposer.dispose(widget.get());
            }
          });
        }
      }
    };

  }

  /**
   * {@inheritDoc}
   */
  @Override
  public JComponent getComponent() {
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  @Override
  public String ID() {
    return ID;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public WidgetPresentation getPresentation(@NotNull Type type) {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void install(@NotNull StatusBar statusBar) {
    myStatusBar = statusBar;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void dispose() {
    myConfigurations.removeConfigurationListener(myConfigurationsListener);
    myStatusBar.removeWidget(ID());
  }

  /**
   * @return get or create remotes group
   */
  private AnAction[] getRemotes() {
    assert myPopupEnabled : "pop should be enabled";
    if (myRemoveConfigurations == null) {
      ArrayList<AnAction> rc = new ArrayList<AnAction>();
      for (final String c : myConfigurations.getRemotesCandidates()) {
        rc.add(new AnAction(c) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            myConfigurations.startCheckout(null, c, false);
          }
        });
      }
      rc.add(new RefreshRemotesAction());
      myRemoveConfigurations = rc.toArray(new AnAction[rc.size()]);
    }
    return myRemoveConfigurations;
  }

  void showPopup() {
    if (!myPopupEnabled) {
      return;
    }
    final DataContext parent = DataManager.getInstance().getDataContext((Component)myStatusBar);
    final DataContext dataContext = SimpleDataContext.getSimpleContext(PlatformDataKeys.PROJECT.getName(), myProject, parent);
    myPopup = JBPopupFactory.getInstance()
      .createActionGroupPopup("Checkout", getPopupActionGroup(), dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
                              new Runnable() {
                                @Override
                                public void run() {
                                  myPopup = null;

                                }
                              }, 20);
    final Dimension dimension = myPopup.getContent().getPreferredSize();
    final Point at = new Point(0, -dimension.height);
    myPopup.show(new RelativePoint(getComponent(), at));
  }

  /**
   * @return get or create selectable configuration group
   */
  private AnAction[] getSelectable() {
    assert myPopupEnabled : "pop should be enabled";
    if (mySelectableConfigurations == null) {
      ArrayList<AnAction> rc = new ArrayList<AnAction>();

      GitBranchConfiguration current;
      try {
        current = myConfigurations.getCurrentConfiguration();
      }
      catch (VcsException e) {
        LOG.error("Unexpected error at this point", e);
        mySelectableConfigurations = new AnAction[0];
        return mySelectableConfigurations;
      }
      String name = current == null ? "" : current.getName();
      for (final String c : myConfigurations.getConfigurationNames()) {
        if (name.equals(c)) {
          // skip current config
          continue;
        }
        rc.add(new AnAction(c) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            try {
              final GitBranchConfiguration toCheckout = myConfigurations.getConfiguration(c);
              if (toCheckout == null) {
                throw new VcsException("The configuration " + c + " cannot be found.");
              }
              myConfigurations.startCheckout(toCheckout, null, true);
            }
            catch (VcsException e1) {
              GitUIUtil.showOperationError(myProject, e1, "Unable to load: " + c);
            }
          }
        });
      }
      mySelectableConfigurations = rc.toArray(new AnAction[rc.size()]);
    }
    return mySelectableConfigurations;
  }

  /**
   * @return the action group for popup
   */
  ActionGroup getPopupActionGroup() {
    if (myPopupActionGroup == null) {
      myPopupActionGroup = new DefaultActionGroup(null, false);
      myPopupActionGroup.addAction(new AnAction("Manage Configurations ...") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          GitManageConfigurationsDialog.showDialog(myProject, myConfigurations);
        }
      });
      myPopupActionGroup.addAction(new AnAction("Modify Current Configuration ...") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          try {
            final GitBranchConfiguration current = myConfigurations.getCurrentConfiguration();
            myConfigurations.startCheckout(current, null, false);
          }
          catch (VcsException e1) {
            LOG.error("The current configuration must exists at this point", e1);
          }
        }
      });
      myPopupActionGroup.addAction(new AnAction("New Configuration ...") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          myConfigurations.startCheckout(null, null, false);
        }
      });
      myPopupActionGroup.add(new MyRemotesActionGroup());
      myPopupActionGroup.addSeparator("Branch Configurations");
      myPopupActionGroup.add(new MySelectableActionGroup());
    }
    return myPopupActionGroup;
  }

  /**
   * Update label on the widget
   */
  private void updateLabel() {
    cancelPopup();
    final GitBranchConfigurations.SpecialStatus status = myConfigurations.getSpecialStatus();
    String t;
    myPopupEnabled = false;
    switch (status) {
      case CHECKOUT_IN_PROGRESS:
        t = "Checkout in progress...";
        break;
      case MERGING:
        t = "Merging...";
        break;
      case REBASING:
        t = "Rebasing...";
        break;
      case NON_GIT:
        t = "Non-Git project";
        break;
      case SUBMODULES:
        t = "Submodules unsupported";
        break;
      case NORMAL:
        GitBranchConfiguration current;
        try {
          current = myConfigurations.getCurrentConfiguration();
        }
        catch (VcsException e) {
          current = null;
        }
        if (current == null) {
          t = "Detection in progress";
        }
        else {
          myPopupEnabled = true;
          t = current.getName();
        }
        break;
      default:
        t = "Unknown status: " + status;
    }
    setForeground(myPopupEnabled ? myDefaultForeground : Color.RED);
    setText(t);
  }

  /**
   * Cancel popup if it is shown
   */
  private void cancelPopup() {
    if (myPopup != null) {
      myPopup.cancel();
    }
  }

  /**
   * Remotes action group
   */
  class MySelectableActionGroup extends ActionGroup {
    /**
     * The constructor
     */
    public MySelectableActionGroup() {
      super(null, false);
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return getSelectable();
    }
  }

  /**
   * Remotes action group
   */
  class MyRemotesActionGroup extends ActionGroup {
    /**
     * The constructor
     */
    public MyRemotesActionGroup() {
      super("Remotes", true);
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return getRemotes();
    }
  }

  /**
   * The configuration listener
   */
  class MyGitBranchConfigurationsListener implements GitBranchConfigurationsListener {
    /**
     * {@inheritDoc}
     */
    @Override
    public void configurationsChanged() {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          mySelectableConfigurations = null;
        }
      });
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void specialStatusChanged() {
      refreshLabel();
    }

    private void refreshLabel() {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          updateLabel();
        }
      });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void currentConfigurationChanged() {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          mySelectableConfigurations = null;
          updateLabel();
        }
      });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void referencesChanged() {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          myRemoveConfigurations = null;
          updateLabel();
        }
      });
    }
  }

  /**
   * Refresh git remotes
   */
  class RefreshRemotesAction extends AnAction {
    /**
     * The constructor
     */
    public RefreshRemotesAction() {
      super("Refresh Remotes...", "Fetch all references for git vcs roots", IconLoader.findIcon("/vcs/refresh.png"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionPerformed(AnActionEvent e) {

      final String title = "Refreshing remotes";
      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, title, false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          final ArrayList<VcsException> exceptions = new ArrayList<VcsException>();
          try {
            for (VirtualFile root : GitUtil.getGitRoots(myProject, GitVcs.getInstance(myProject))) {
              GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.FETCH);
              h.addParameters("--all", "-v");
              final Collection<VcsException> e = GitHandlerUtil
                .doSynchronouslyWithExceptions(h, indicator, "Fetching all for " + GitUtil.relativePath(myProject.getBaseDir(), root));
              exceptions.addAll(e);
            }
          }
          catch (VcsException e1) {
            exceptions.add(e1);
          }
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            @Override
            public void run() {
              if (!exceptions.isEmpty()) {
                GitUIUtil.showTabErrors(myProject, title, exceptions);
                ToolWindowManager.getInstance(myProject).notifyByBalloon(
                  ChangesViewContentManager.TOOLWINDOW_ID, MessageType.ERROR, "Refreshing remotes failed.");
              }
              else {
                ToolWindowManager.getInstance(myProject).notifyByBalloon(
                  ChangesViewContentManager.TOOLWINDOW_ID, MessageType.INFO, "Refreshing remotes complete.");
              }
              myRemoveConfigurations = null;
              cancelPopup();
            }
          });
        }
      });
    }
  }

}
