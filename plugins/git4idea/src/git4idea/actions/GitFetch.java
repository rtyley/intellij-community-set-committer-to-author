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
package git4idea.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitStandardProgressAnalyzer;
import git4idea.commands.GitTask;
import git4idea.commands.GitTaskResultHandlerAdapter;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.GitFetchDialog;
import git4idea.ui.GitUIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Git "fetch" action
 */
public class GitFetch extends GitRepositoryAction {
  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("fetch.action.name");
  }

  protected void perform(@NotNull final Project project,
                         @NotNull final List<VirtualFile> gitRoots,
                         @NotNull final VirtualFile defaultRoot,
                         final Set<VirtualFile> affectedRoots,
                         final List<VcsException> exceptions) throws VcsException {
    final GitFetchDialog d = new GitFetchDialog(project, gitRoots, defaultRoot);
    d.show();
    if (!d.isOK()) {
      return;
    }
    final GitLineHandler h = d.fetchHandler();
    GitTask fetchTask = new GitTask(project, h, GitBundle.message("fetching.title", d.getRemote()));
    fetchTask.setProgressAnalyzer(new GitStandardProgressAnalyzer());
    fetchTask.executeAsync(new GitTaskResultHandlerAdapter() {
      @Override
      protected void onSuccess() {
        GitUIUtil.notifySuccess(project, "Fetched successfully", "Fetched " + d.getRemote());
        GitRepositoryManager.getInstance(project).updateRepository(d.getGitRoot(), GitRepository.TrackedTopic.ALL);
      }

      @Override
      protected void onFailure() {
        GitUIUtil.notifyGitErrors(project, "Error fetching " + d.getRemote(), "", h.errors());
        GitRepositoryManager.getInstance(project).updateRepository(d.getGitRoot(), GitRepository.TrackedTopic.ALL);
      }
    });
  }

}
