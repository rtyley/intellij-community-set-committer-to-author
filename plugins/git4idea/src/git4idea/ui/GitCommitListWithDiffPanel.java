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
package git4idea.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.util.Consumer;
import git4idea.history.browser.GitHeavyCommit;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * List of commits at the left, the {@link ChangesBrowser} at the right.
 * Select a commit to shows its changes in the changes browser.
 *
 * @author Kirill Likhodedov
 */
public class GitCommitListWithDiffPanel extends JPanel {

  private final ChangesBrowser myChangesBrowser;
  private final GitCommitListPanel myCommitListPanel;

  public GitCommitListWithDiffPanel(@NotNull Project project, @NotNull List<GitHeavyCommit> commits) {
    super(new BorderLayout());

    myCommitListPanel = new GitCommitListPanel(commits, null);
    myCommitListPanel.addListSelectionListener(new Consumer<GitHeavyCommit>() {
      @Override public void consume(GitHeavyCommit commit) {
        myChangesBrowser.setChangesToDisplay(commit.getChanges());
      }
    });

    myChangesBrowser = new ChangesBrowser(project, null, Collections.<Change>emptyList(), null, false, true, null, ChangesBrowser.MyUseCase.LOCAL_CHANGES, null);
    myCommitListPanel.registerDiffAction(myChangesBrowser.getDiffAction());

    Splitter splitter = new Splitter(false, 0.7f);
    splitter.setHonorComponentsMinimumSize(false);
    splitter.setFirstComponent(myCommitListPanel);
    splitter.setSecondComponent(myChangesBrowser);
    add(splitter);
  }

  @NotNull
  public JComponent getPreferredFocusComponent() {
    return myCommitListPanel.getPreferredFocusComponent();
  }
  
  public void setCommits(@NotNull List<GitHeavyCommit> commits) {
    myCommitListPanel.setCommits(commits);
  }

}
