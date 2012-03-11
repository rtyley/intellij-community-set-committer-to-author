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
package git4idea.update;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import git4idea.GitRevisionNumber;
import git4idea.GitVcs;
import git4idea.branch.GitBranchPair;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.config.GitConfigUtil;
import git4idea.config.GitVcsSettings;
import git4idea.merge.MergeChangeCollector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Map;

/**
 * Updates a single repository via merge or rebase.
 * @see GitRebaseUpdater
 * @see GitMergeUpdater
 */
public abstract class GitUpdater {
  private static final Logger LOG = Logger.getInstance(GitUpdater.class);

  protected final @NotNull Project myProject;
  protected final @NotNull VirtualFile myRoot;
  protected final @NotNull Map<VirtualFile, GitBranchPair> myTrackedBranches;
  protected final @NotNull ProgressIndicator myProgressIndicator;
  protected final @NotNull UpdatedFiles myUpdatedFiles;
  protected final @NotNull AbstractVcsHelper myVcsHelper;
  protected final GitVcs myVcs;

  protected GitRevisionNumber myBefore; // The revision that was before update

  protected GitUpdater(@NotNull Project project, @NotNull VirtualFile root, @NotNull Map<VirtualFile, GitBranchPair> trackedBranches,
                       @NotNull ProgressIndicator progressIndicator, @NotNull UpdatedFiles updatedFiles) {
    myProject = project;
    myRoot = root;
    myTrackedBranches = trackedBranches;
    myProgressIndicator = progressIndicator;
    myUpdatedFiles = updatedFiles;
    myVcsHelper = AbstractVcsHelper.getInstance(project);
    myVcs = GitVcs.getInstance(project);
  }

  /**
   * Returns proper updater based on the update policy (merge or rebase) selected by user or stored in his .git/config
   * @return {@link GitMergeUpdater} or {@link GitRebaseUpdater}.
   */
  @NotNull
  public static GitUpdater getUpdater(@NotNull Project project, @NotNull Map<VirtualFile, GitBranchPair> trackedBranches,
                                      @NotNull VirtualFile root, @NotNull ProgressIndicator progressIndicator,
                                      @NotNull UpdatedFiles updatedFiles) {
    final GitVcsSettings settings = GitVcsSettings.getInstance(project);
    if (settings == null) {
      return getDefaultUpdaterForBranch(project, root, trackedBranches, progressIndicator, updatedFiles);
    }
    switch (settings.getUpdateType()) {
      case REBASE:
        return new GitRebaseUpdater(project, root, trackedBranches, progressIndicator, updatedFiles);
      case MERGE:
        return new GitMergeUpdater(project, root, trackedBranches, progressIndicator, updatedFiles);
      case BRANCH_DEFAULT:
        // use default for the branch
        return getDefaultUpdaterForBranch(project, root, trackedBranches, progressIndicator, updatedFiles);
    }
    return getDefaultUpdaterForBranch(project, root, trackedBranches, progressIndicator, updatedFiles);
  }

  @NotNull
  private static GitUpdater getDefaultUpdaterForBranch(@NotNull Project project, @NotNull VirtualFile root,
                                                       @NotNull Map<VirtualFile, GitBranchPair> trackedBranches,
                                                       @NotNull ProgressIndicator progressIndicator, @NotNull UpdatedFiles updatedFiles) {
    try {
      final GitBranch branchName = GitBranch.current(project, root);
      final String rebase = GitConfigUtil.getValue(project, root, "branch." + branchName + ".rebase");
      if (rebase != null && rebase.equalsIgnoreCase("true")) {
        return new GitRebaseUpdater(project, root, trackedBranches, progressIndicator, updatedFiles);
      }
    } catch (VcsException e) {
      LOG.info("getDefaultUpdaterForBranch branch", e);
    }
    return new GitMergeUpdater(project, root, trackedBranches, progressIndicator, updatedFiles);
  }

  @NotNull
  public GitUpdateResult update() throws VcsException {
    markStart(myRoot);
    try {
      return doUpdate();
    } finally {
      markEnd(myRoot);
    }
  }

  /**
   * Checks the repository if local changes need to be saved before update.
   * For rebase local changes need to be saved always, 
   * for merge - only in the case if merge affects the same files or there is something in the index.
   * @return true if local changes from this root need to be saved, false if not.
   */
  public abstract boolean isSaveNeeded();

  /**
   * Checks if update is needed, i.e. if there are remote changes that weren't merged into the current branch.
   * @return true if update is needed, false otherwise.
   */
  public boolean isUpdateNeeded() throws VcsException {
    GitBranchPair gitBranchPair = myTrackedBranches.get(myRoot);
    String currentBranch = gitBranchPair.getBranch().getName();
    GitBranch dest = gitBranchPair.getDest();
    assert dest != null;
    String remoteBranch = dest.getName();
    if (! hasRemoteChanges(currentBranch, remoteBranch)) {
      LOG.info("isSaveNeeded No remote changes, save is not needed");
      return false;
    }
    return true;
  }

  /**
   * Performs update (via rebase or merge - depending on the implementing classes).
   */
  protected abstract GitUpdateResult doUpdate();

  protected void markStart(VirtualFile root) throws VcsException {
    // remember the current position
    myBefore = GitRevisionNumber.resolve(myProject, root, "HEAD");
  }

  protected void markEnd(VirtualFile root) throws VcsException {
    // find out what have changed, this is done even if the process was cancelled.
    final MergeChangeCollector collector = new MergeChangeCollector(myProject, root, myBefore);
    final ArrayList<VcsException> exceptions = new ArrayList<VcsException>();
    collector.collect(myUpdatedFiles, exceptions);
    if (!exceptions.isEmpty()) {
      throw exceptions.get(0);
    }
  }

  protected boolean hasRemoteChanges(@NotNull String currentBranch, @NotNull String remoteBranch) throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(myProject, myRoot, GitCommand.REV_LIST);
    handler.setNoSSH(true);
    handler.addParameters("-1");
    handler.addParameters(currentBranch + ".." + remoteBranch);
    String output = handler.run();
    return output != null && !output.isEmpty();
  }
}
