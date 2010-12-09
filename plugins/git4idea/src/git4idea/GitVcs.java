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
package git4idea;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.RemoteDifferenceStrategy;
import com.intellij.openapi.vcs.RepositoryChangeListener;
import com.intellij.openapi.vcs.TreeDiffProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.VcsOutgoingChangesProvider;
import com.intellij.openapi.vcs.VcsType;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.RevisionSelector;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ComparatorDelegate;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import git4idea.annotate.GitAnnotationProvider;
import git4idea.changes.GitChangeProvider;
import git4idea.changes.GitChangeUtils;
import git4idea.changes.GitCommittedChangeListProvider;
import git4idea.changes.GitOutgoingChangesProvider;
import git4idea.checkin.GitCheckinEnvironment;
import git4idea.checkin.GitCommitAndPushExecutor;
import git4idea.checkout.branches.GitBranches;
import git4idea.checkout.branches.GitCurrentBranchWidget;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.config.GitExecutableValidator;
import git4idea.config.GitVcsApplicationSettings;
import git4idea.config.GitVcsConfigurable;
import git4idea.config.GitVcsSettings;
import git4idea.config.GitVersion;
import git4idea.diff.GitDiffProvider;
import git4idea.diff.GitTreeDiffProvider;
import git4idea.history.GitHistoryProvider;
import git4idea.history.NewGitUsersComponent;
import git4idea.history.browser.GitProjectLogManager;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitMergeProvider;
import git4idea.rollback.GitRollbackEnvironment;
import git4idea.update.GitUpdateEnvironment;
import git4idea.vfs.GitConfigListener;
import git4idea.vfs.GitConfigTracker;
import git4idea.vfs.GitIgnoreTracker;
import git4idea.vfs.GitReferenceListener;
import git4idea.vfs.GitReferenceTracker;
import git4idea.vfs.GitRootTracker;
import git4idea.vfs.GitRootsListener;
import git4idea.vfs.GitVFSListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Git VCS implementation
 */
public class GitVcs extends AbstractVcs<CommittedChangeList> {
  public static final String NOTIFICATION_GROUP_ID = "Git";
  public static final String IMPORTANT_ERROR_NOTIFICATION = "Git Important Errors";
  public static final String NAME = "Git"; // Vcs name

  private static final Logger log = Logger.getInstance(GitVcs.class.getName());
  private static final VcsKey ourKey = createKey(NAME);

  private final ChangeProvider myChangeProvider;
  private final CheckinEnvironment myCheckinEnvironment;
  private final RollbackEnvironment myRollbackEnvironment;
  private final GitUpdateEnvironment myUpdateEnvironment;
  private final GitAnnotationProvider myAnnotationProvider;
  private final DiffProvider myDiffProvider;
  private final VcsHistoryProvider myHistoryProvider;
  private final ProjectLevelVcsManager myVcsManager;
  private final GitVcsApplicationSettings myAppSettings;
  private final Configurable myConfigurable;
  private final RevisionSelector myRevSelector;
  private final GitMergeProvider myMergeProvider;
  private final GitMergeProvider myReverseMergeProvider;
  private final GitCommittedChangeListProvider myCommittedChangeListProvider;

  private GitVFSListener myVFSListener; // a VFS listener that tracks file addition, deletion, and renaming.
  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"}) private GitVersion myVersion; // The currently detected git version or null.
  private final Object myCheckingVersion = new Object(); // Checking the version lock (used to prevent infinite recursion)
  private String myVersionCheckExcecutable = ""; // The path to executable at the time of version check

  private GitRootTracker myRootTracker; // The tracker that checks validity of git roots
  private final EventDispatcher<GitRootsListener> myRootListeners = EventDispatcher.create(GitRootsListener.class);
  private final EventDispatcher<GitConfigListener> myConfigListeners = EventDispatcher.create(GitConfigListener.class);
  private final EventDispatcher<GitReferenceListener> myReferenceListeners = EventDispatcher.create(GitReferenceListener.class);
  private GitIgnoreTracker myGitIgnoreTracker;
  private GitConfigTracker myConfigTracker;
  private final BackgroundTaskQueue myTaskQueue; // The queue that is used to schedule background task from actions
  private final ReadWriteLock myCommandLock = new ReentrantReadWriteLock(true); // The command read/write lock
  private final TreeDiffProvider myTreeDiffProvider;
  private final GitCommitAndPushExecutor myCommitAndPushExecutor;
  private GitReferenceTracker myReferenceTracker;
  private boolean isActivated; // If true, the vcs was activated
  private GitExecutableValidator myExecutableValidator;
  private RepositoryChangeListener myIndexChangeListener;
  private GitCurrentBranchWidget myCurrentBranchWidget;
  private GitBranches myBranches;

  @Nullable
  public static GitVcs getInstance(Project project) {
    if (project == null || project.isDisposed()) {
      return null;
    }
    return (GitVcs) ProjectLevelVcsManager.getInstance(project).findVcsByName(NAME);
  }

  public GitVcs(@NotNull Project project,
                @NotNull final GitChangeProvider gitChangeProvider,
                @NotNull final GitCheckinEnvironment gitCheckinEnvironment,
                @NotNull final ProjectLevelVcsManager gitVcsManager,
                @NotNull final GitAnnotationProvider gitAnnotationProvider,
                @NotNull final GitDiffProvider gitDiffProvider,
                @NotNull final GitHistoryProvider gitHistoryProvider,
                @NotNull final GitRollbackEnvironment gitRollbackEnvironment,
                @NotNull final GitVcsApplicationSettings gitSettings,
                @NotNull final GitVcsSettings gitProjectSettings) {
    super(project, NAME);
    myVcsManager = gitVcsManager;
    myAppSettings = gitSettings;
    myChangeProvider = gitChangeProvider;
    myCheckinEnvironment = gitCheckinEnvironment;
    myAnnotationProvider = gitAnnotationProvider;
    myDiffProvider = gitDiffProvider;
    myHistoryProvider = gitHistoryProvider;
    myRollbackEnvironment = gitRollbackEnvironment;
    myRevSelector = new GitRevisionSelector();
    myConfigurable = new GitVcsConfigurable(gitProjectSettings, myProject);
    myUpdateEnvironment = new GitUpdateEnvironment(myProject, this, gitProjectSettings);
    myMergeProvider = new GitMergeProvider(myProject);
    myReverseMergeProvider = new GitMergeProvider(myProject, true);
    myCommittedChangeListProvider = new GitCommittedChangeListProvider(myProject);
    myOutgoingChangesProvider = new GitOutgoingChangesProvider(myProject);
    myTreeDiffProvider = new GitTreeDiffProvider(myProject);
    myCommitAndPushExecutor = new GitCommitAndPushExecutor(gitCheckinEnvironment);
    myReferenceTracker = new GitReferenceTracker(myProject, this, myReferenceListeners.getMulticaster());
    myTaskQueue = new BackgroundTaskQueue(myProject, GitBundle.getString("task.queue.title"));
    myIndexChangeListener = new RepositoryChangeListener(myProject, ".git/index");
    myBranches = new GitBranches(myProject);
  }

  /**
   * @return the vfs listener instance
   */
  public GitVFSListener getVFSListener() {
    return myVFSListener;
  }

  /**
   * @return the command lock
   */
  public ReadWriteLock getCommandLock() {
    return myCommandLock;
  }

  /**
   * Run task in background using the common queue (per project)
   *
   * @param task the task to run
   */
  public static void runInBackground(Task.Backgroundable task) {
    GitVcs vcs = getInstance(task.getProject());
    if (vcs != null) {
      vcs.myTaskQueue.run(task);
    }
  }

  /**
   * Add listener for git roots
   *
   * @param listener the listener to add
   */
  public void addGitConfigListener(GitConfigListener listener) {
    myConfigListeners.addListener(listener);
  }

  /**
   * Remove listener for git roots
   *
   * @param listener the listener to remove
   */
  public void removeGitConfigListener(GitConfigListener listener) {
    myConfigListeners.removeListener(listener);
  }

  /**
   * Add listener for git roots
   *
   * @param listener the listener to add
   */
  public void addGitReferenceListener(GitReferenceListener listener) {
    myReferenceListeners.addListener(listener);
  }

  /**
   * Remove listener for git roots
   *
   * @param listener the listener to remove
   */
  public void removeGitReferenceListener(GitReferenceListener listener) {
    myReferenceListeners.removeListener(listener);
  }

  /**
   * Add listener for git roots
   *
   * @param listener the listener to add
   */
  public void addGitRootsListener(GitRootsListener listener) {
    myRootListeners.addListener(listener);
  }

  /**
   * Remove listener for git roots
   *
   * @param listener the listener to remove
   */
  public void removeGitRootsListener(GitRootsListener listener) {
    myRootListeners.removeListener(listener);
  }

  /**
   * @return a reverse merge provider for git (with reversed meaning of "theirs" and "yours", needed for the rebase and unstash)
   */
  @NotNull
  public MergeProvider getReverseMergeProvider() {
    return myReverseMergeProvider;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CommittedChangesProvider getCommittedChangesProvider() {
    return myCommittedChangeListProvider;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getRevisionPattern() {
    // return the full commit hash pattern, possibly other revision formats should be supported as well
    return "[0-9a-fA-F]{40}";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  public CheckinEnvironment getCheckinEnvironment() {
    return myCheckinEnvironment;
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  @Override
  public MergeProvider getMergeProvider() {
    return myMergeProvider;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  public RollbackEnvironment getRollbackEnvironment() {
    return myRollbackEnvironment;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  public VcsHistoryProvider getVcsHistoryProvider() {
    return myHistoryProvider;
  }

  @Override
  public VcsHistoryProvider getVcsBlockHistoryProvider() {
    return myHistoryProvider;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  public String getDisplayName() {
    return NAME;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Nullable
  public UpdateEnvironment getUpdateEnvironment() {
    return myUpdateEnvironment;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  public GitAnnotationProvider getAnnotationProvider() {
    return myAnnotationProvider;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  public DiffProvider getDiffProvider() {
    return myDiffProvider;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Nullable
  public RevisionSelector getRevisionSelector() {
    return myRevSelector;
  }

  /**
   * {@inheritDoc}
   */
  @SuppressWarnings({"deprecation"})
  @Override
  @Nullable
  public VcsRevisionNumber parseRevisionNumber(String revision, FilePath path) {
    if (revision == null || revision.length() == 0) return null;
    if (revision.length() > 40) {    // date & revision-id encoded string
      String dateString = revision.substring(0, revision.indexOf("["));
      String rev = revision.substring(revision.indexOf("[") + 1, 40);
      Date d = new Date(Date.parse(dateString));
      return new GitRevisionNumber(rev, d);
    }
    if (path != null) {
      try {
        VirtualFile root = GitUtil.getGitRoot(path);
        return GitRevisionNumber.resolve(myProject, root, revision);
      }
      catch (VcsException e) {
        log.error("Unexpected problem with resolving the git revision number: ", e);
      }
    }
    return new GitRevisionNumber(revision);

  }

  /**
   * {@inheritDoc}
   */
  @SuppressWarnings({"deprecation"})
  @Override
  @Nullable
  public VcsRevisionNumber parseRevisionNumber(String revision) {
    return parseRevisionNumber(revision, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isVersionedDirectory(VirtualFile dir) {
    return dir.isDirectory() && GitUtil.gitRootOrNull(dir) != null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void start() throws VcsException {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void shutdown() throws VcsException {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void activate() {
    isActivated = true;
    myExecutableValidator = new GitExecutableValidator(myProject);
    myExecutableValidator.checkExecutableAndShowDialogIfNeeded();
    if (!myProject.isDefault() && myRootTracker == null) {
      myRootTracker = new GitRootTracker(this, myProject, myRootListeners.getMulticaster());
    }
    if (myVFSListener == null) {
      myVFSListener = new GitVFSListener(myProject, this);
    }
    if (myConfigTracker == null) {
      myConfigTracker = new GitConfigTracker(myProject, this, myConfigListeners.getMulticaster());
    }
    if (myGitIgnoreTracker == null) {
      myGitIgnoreTracker = new GitIgnoreTracker(myProject, this);
    }
    myIndexChangeListener.activate();
    myReferenceTracker.activate();
    NewGitUsersComponent.getInstance(myProject).activate();
    GitProjectLogManager.getInstance(myProject).activate();

    addGitReferenceListener(myBranches);
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    if (statusBar != null) {
      myCurrentBranchWidget = new GitCurrentBranchWidget(myProject, myBranches);
      statusBar.addWidget(myCurrentBranchWidget, "after " + (SystemInfo.isMac ? "Encoding" : "InsertOverwrite"), myProject);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void deactivate() {
    isActivated = false;
    if (myRootTracker != null) {
      myRootTracker.dispose();
      myRootTracker = null;
    }
    if (myVFSListener != null) {
      Disposer.dispose(myVFSListener);
      myVFSListener = null;
    }
    if (myGitIgnoreTracker != null) {
      myGitIgnoreTracker.dispose();
      myGitIgnoreTracker = null;
    }
    if (myConfigTracker != null) {
      myConfigTracker.dispose();
      myConfigTracker = null;
    }
    myIndexChangeListener.dispose();
    myReferenceTracker.deactivate();
    NewGitUsersComponent.getInstance(myProject).deactivate();
    GitProjectLogManager.getInstance(myProject).deactivate();

    removeGitReferenceListener(myBranches);
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    if (statusBar != null && myCurrentBranchWidget != null) {
      statusBar.removeWidget(myCurrentBranchWidget.ID());
      myCurrentBranchWidget = null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  @Override
  public synchronized Configurable getConfigurable() {
    return myConfigurable;
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  public ChangeProvider getChangeProvider() {
    return myChangeProvider;
  }

  /**
   * Show errors as popup and as messages in vcs view.
   *
   * @param list   a list of errors
   * @param action an action
   */
  public void showErrors(@NotNull List<VcsException> list, @NotNull String action) {
    if (list.size() > 0) {
      StringBuffer buffer = new StringBuffer();
      buffer.append("\n");
      buffer.append(GitBundle.message("error.list.title", action));
      for (final VcsException exception : list) {
        buffer.append("\n");
        buffer.append(exception.getMessage());
      }
      final String msg = buffer.toString();
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          Messages.showErrorDialog(myProject, msg, GitBundle.getString("error.dialog.title"));
        }
      });
    }
  }

  /**
   * Show a plain message in vcs view
   *
   * @param message a message to show
   */
  public void showMessages(@NotNull String message) {
    if (message.length() == 0) return;
    showMessage(message, ConsoleViewContentType.NORMAL_OUTPUT.getAttributes());
  }

  /**
   * @return vcs settings for the current project
   */
  @NotNull
  public GitVcsApplicationSettings getAppSettings() {
    return myAppSettings;
  }

  /**
   * Show message in the VCS view
   *
   * @param message a message to show
   * @param style   a style to use
   */
  private void showMessage(@NotNull String message, final TextAttributes style) {
    myVcsManager.addMessageToConsoleWindow(message, style);
  }

  /**
   * Check version and report problem
   */
  public void checkVersion() {
    final String executable = myAppSettings.getPathToGit();
    synchronized (myCheckingVersion) {
      if (myVersion != null && myVersionCheckExcecutable.equals(executable)) {
        return;
      }
      myVersionCheckExcecutable = executable;
      // this assignment is done to prevent recursive version check
      myVersion = GitVersion.INVALID;
      final String version;
      try {
        version = version(myProject).trim();
      }
      catch (VcsException e) {
        String reason = (e.getCause() != null ? e.getCause() : e).getMessage();
        if (!myProject.isDefault()) {
          showMessage(GitBundle.message("vcs.unable.to.run.git", executable, reason), ConsoleViewContentType.SYSTEM_OUTPUT.getAttributes());
        }
        return;
      }
      myVersion = GitVersion.parse(version);
      if (!GitVersion.parse(version).isSupported() && !myProject.isDefault()) {
        showMessage(GitBundle.message("vcs.unsupported.version", version, GitVersion.MIN),
                    ConsoleViewContentType.SYSTEM_OUTPUT.getAttributes());
      }
    }
  }

  /**
   * @return the configured version of git
   */
  public GitVersion version() {
    checkVersion();
    return myVersion;
  }

  /**
   * Get the version of configured git
   *
   * @param project the project
   * @return a version of configured git
   * @throws VcsException an error if there is a problem with running git
   */
  public static String version(Project project) throws VcsException {
    final String s;
    GitSimpleHandler h = new GitSimpleHandler(project, new File("."), GitCommand.VERSION);
    h.setNoSSH(true);
    h.setSilent(true);
    s = h.run();
    return s;
  }

  /**
   * Show command line
   *
   * @param cmdLine a command line text
   */
  public void showCommandLine(final String cmdLine) {
    SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss.SSS");
    showMessage(f.format(new Date()) + ": " + cmdLine, ConsoleViewContentType.SYSTEM_OUTPUT.getAttributes());
  }

  /**
   * The error line
   *
   * @param line a line to show
   */
  public void showErrorMessages(final String line) {
    showMessage(line, ConsoleViewContentType.ERROR_OUTPUT.getAttributes());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean allowsNestedRoots() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <S> List<S> filterUniqueRoots(final List<S> in, final Convertor<S, VirtualFile> convertor) {
    Collections.sort(in, new ComparatorDelegate<S, VirtualFile>(convertor, FilePathComparator.getInstance()));

    for (int i = 1; i < in.size(); i++) {
      final S sChild = in.get(i);
      final VirtualFile child = convertor.convert(sChild);
      final VirtualFile childRoot = GitUtil.gitRootOrNull(child);
      if (childRoot == null) {
        // non-git file actually, skip it
        continue;
      }
      for (int j = i - 1; j >= 0; --j) {
        final S sParent = in.get(j);
        final VirtualFile parent = convertor.convert(sParent);
        // the method check both that parent is an ancestor of the child and that they share common git root
        if (VfsUtil.isAncestor(parent, child, false) && VfsUtil.isAncestor(childRoot, parent, false)) {
          in.remove(i);
          //noinspection AssignmentToForLoopParameter
          --i;
          break;
        }
      }
    }
    return in;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RootsConvertor getCustomConvertor() {
    return GitRootConverter.INSTANCE;
  }

  public static VcsKey getKey() {
    return ourKey;
  }

  @Override
  public VcsType getType() {
    return VcsType.distibuted;
  }

  private final VcsOutgoingChangesProvider<CommittedChangeList> myOutgoingChangesProvider;

  @Override
  protected VcsOutgoingChangesProvider<CommittedChangeList> getOutgoingProviderImpl() {
    return myOutgoingChangesProvider;
  }

  @Override
  public RemoteDifferenceStrategy getRemoteDifferenceStrategy() {
    return RemoteDifferenceStrategy.ASK_TREE_PROVIDER;
  }

  @Override
  protected TreeDiffProvider getTreeDiffProviderImpl() {
    return myTreeDiffProvider;
  }

  @Override
  public List<CommitExecutor> getCommitExecutors() {
    return Collections.<CommitExecutor>singletonList(myCommitAndPushExecutor);
  }

  @Override
  public CommittedChangeList getRevisionChanges(final VcsFileRevision revision, final VirtualFile file) throws VcsException {
    final Project project = getProject();
    final VirtualFile vcsRoot = GitUtil.getGitRoot(file);
    return GitChangeUtils.getRevisionChanges(project, vcsRoot, revision.getRevisionNumber().asString(), false);
  }

  /**
   * @return true if vcs was activated
   */
  public boolean isActivated() {
    return isActivated;
  }

  public GitExecutableValidator getExecutableValidator() {
    return myExecutableValidator;
  }

  public RepositoryChangeListener getIndexChangeListener() {
    return myIndexChangeListener;
  }

}
