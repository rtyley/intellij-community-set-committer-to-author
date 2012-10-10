/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.branch;

import com.intellij.notification.NotificationListener;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.history.browser.GitCommit;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * <p>Handles UI interaction during various operations on branches: shows notifications, proposes to rollback, shows dialogs, messages, etc.
 * Some methods return the choice selected by user to the calling code, if it is needed.</p>
 * <p>The purpose of this class is to separate UI interaction from the main code, which would in particular simplify testing.</p>
 *
 * @author Kirill Likhodedov
 */
interface GitBranchUiHandler {

  @NotNull
  ProgressIndicator getProgressIndicator();

  /**
   * Shows a notification about successful branch operation. The title is empty.
   */
  void notifySuccess(@NotNull String message);

  void notifySuccess(@NotNull String title, @NotNull String message);

  void notifySuccess(@NotNull String title, @NotNull String description, @Nullable NotificationListener listener);

  void notifyError(@NotNull String title, @NotNull String message);

  boolean notifyErrorWithRollbackProposal(@NotNull String title, @NotNull String message, @NotNull String rollbackProposal);

  /**
   * Shows notification about unmerged files preventing checkout, merge, etc.
   * @param operationName
   * @param repositories
   */
  void showUnmergedFilesNotification(@NotNull String operationName, @NotNull Collection<GitRepository> repositories);

  /**
   * Shows a modal notification about unmerged files preventing an operation, with "Rollback" button.
   * Pressing "Rollback" would should the operation which has already successfully executed on other repositories.
   *
   * @return true if user has agreed to rollback, false if user denied the rollback proposal.
   * @param operationName
   * @param rollbackProposal
   */
  boolean showUnmergedFilesMessageWithRollback(@NotNull String operationName, @NotNull String rollbackProposal);

  /**
   * Show notification about "untracked files would be overwritten by merge/checkout".
   * @param untrackedFiles
   */
  void showUntrackedFilesNotification(@NotNull String operationName, @NotNull Collection<VirtualFile> untrackedFiles);

  boolean showUntrackedFilesDialogWithRollback(@NotNull String operationName, @NotNull String rollbackProposal,
                                               @NotNull Collection<VirtualFile> untrackedFiles);

  /**
   * Shows the dialog proposing to execute the operation (checkout or merge) smartly, i.e. stash-execute-unstash.
   * @param project
   * @param changes   local changes that would be overwritten by checkout or merge.
   * @param operation operation name
   * @param force     can the operation be executed force (force checkout is possible, force merge - not).
   * @return the code of the decision.
   */
  int showSmartOperationDialog(@NotNull Project project, @NotNull List<Change> changes, @NotNull String operation, boolean force);

  boolean showBranchIsNotFullyMergedDialog(@NotNull Project project, @NotNull Map<GitRepository, List<GitCommit>> history,
                                           @NotNull String unmergedBranch, @NotNull List<String> mergedToBranches,
                                           @NotNull String baseBranch);

}
