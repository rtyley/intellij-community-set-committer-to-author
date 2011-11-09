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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import git4idea.push.GitPushDialog;
import git4idea.push.GitPusher;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Git "push" action
 */
public class GitPush extends GitRepositoryAction {

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("push.action.name");
  }

  /**
   * {@inheritDoc}
   */
  protected void perform(@NotNull final Project project,
                         @NotNull final List<VirtualFile> gitRoots,
                         @NotNull final VirtualFile defaultRoot,
                         final Set<VirtualFile> affectedRoots,
                         final List<VcsException> exceptions) throws VcsException {

    Collection<GitRepository> repositories = GitRepositoryManager.getInstance(project).getRepositories();
    final GitPushDialog dialog = new GitPushDialog(project);
    dialog.show();
    if (dialog.isOK()) {
      Task.Backgroundable task = new Task.Backgroundable(project, GitPusher.INDICATOR_TEXT, false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          new GitPusher(project, indicator).push(dialog.getPushInfo());
        }
      };
      GitVcs.runInBackground(task);
    }
  }

}