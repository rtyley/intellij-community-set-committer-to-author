// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.action;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.command.HgTagBranch;
import org.zmlx.hg4idea.command.HgTagBranchCommand;
import org.zmlx.hg4idea.util.HgUtil;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

abstract class HgAbstractGlobalAction extends AnAction {
  protected HgAbstractGlobalAction(Icon icon) {
    super(icon);
  }

  protected HgAbstractGlobalAction() {
  }

  private static final Logger LOG = Logger.getInstance(HgAbstractGlobalAction.class.getName());

  public void actionPerformed(AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    execute(project, HgUtil.getHgRepositories(project));
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    Presentation presentation = e.getPresentation();
    final DataContext dataContext = e.getDataContext();

    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
    }
  }

  protected abstract void execute(Project project, Collection<VirtualFile> repositories);

  protected static void handleException(Project project, Exception e) {
    LOG.info(e);
    new HgCommandResultNotifier(project).notifyError(null, "Error", e.getMessage());
  }

  protected void markDirtyAndHandleErrors(Project project, VirtualFile repository) {
    try {
      HgUtil.markDirectoryDirty(project, repository);
    }
    catch (InvocationTargetException e) {
      handleException(project, e);
    }
    catch (InterruptedException e) {
      handleException(project, e);
    }
  }

  protected void loadBranchesInBackgroundableAndExecuteAction(final Project project, final Collection<VirtualFile> repos) {
    final Map<VirtualFile, List<HgTagBranch>> branchesForRepos = new HashMap<VirtualFile, List<HgTagBranch>>();
    new Task.Backgroundable(project, "Update branches...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (final VirtualFile repo : repos) {
          new HgTagBranchCommand(project, repo).listBranches(new Consumer<List<HgTagBranch>>() {
            @Override
            public void consume(final List<HgTagBranch> branches) {
              branchesForRepos.put(repo, branches);
            }
          });
        }
      }

      @Override
      public void onSuccess() {
        showDialogAndExecute(project, repos, branchesForRepos);
      }
    }.queue();
  }

  protected void showDialogAndExecute(Project project,
                                      Collection<VirtualFile> repos,
                                      Map<VirtualFile, List<HgTagBranch>> loadedBranches) {
  }
}
