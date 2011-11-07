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
package git4idea.push;

import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.tree.TreeUtil;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.branch.GitBranchPair;
import git4idea.history.browser.GitCommit;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * The list of commits from multiple repositories and branches, with diff panel at the right.
 *
 * @author Kirill Likhodedov
 */
class GitPushLog extends JPanel implements TypeSafeDataProvider {

  private final Project myProject;
  private final ChangesBrowser myChangesBrowser;
  private final Tree myTree;
  private final DefaultTreeModel myTreeModel;
  private final CheckedTreeNode myRootNode;
  private final Map<GitRepository, Boolean> mySelectedRepositories = new HashMap<GitRepository, Boolean>();

  GitPushLog(@NotNull Project project) {
    myProject = project;
    for (GitRepository repository : GitRepositoryManager.getInstance(project).getRepositories()) {
      mySelectedRepositories.put(repository, true);
    }

    myRootNode = new CheckedTreeNode(null);
    myTreeModel = new DefaultTreeModel(myRootNode);
    myTree = new CheckboxTree(new MyTreeCellRenderer(), myRootNode);
    myTree.setRootVisible(false);
    TreeUtil.expandAll(myTree);

    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) myTree.getLastSelectedPathComponent();
        if (node != null) {
          Object nodeInfo = node.getUserObject();
          if (nodeInfo instanceof GitCommit) {
            myChangesBrowser.setChangesToDisplay(((GitCommit)nodeInfo).getChanges());
            return;
          }
        }
        myChangesBrowser.setChangesToDisplay(Collections.<Change>emptyList());
      }
    });
    
    myChangesBrowser = new ChangesBrowser(project, null, Collections.<Change>emptyList(), null, false, true, null, ChangesBrowser.MyUseCase.LOCAL_CHANGES, null);
    myChangesBrowser.getDiffAction().registerCustomShortcutSet(CommonShortcuts.getDiff(), myTree);

    Splitter splitter = new Splitter(false, 0.7f);
    splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree));
    splitter.setSecondComponent(myChangesBrowser);
    
    setLayout(new BorderLayout());
    add(splitter);
  }

  private void createNodes(@NotNull GitCommitsByRepoAndBranch commits) {
    for (GitRepository repository : sortRepos(commits.getRepositories())) {
      GitCommitsByBranch commitsByBranch = commits.get(repository);
      createRepoNode(repository, commitsByBranch, myRootNode);
    }
  }

  /**
   * Creates the node with subnodes for a repository and adds it to the rootNode.
   * If there is only one repo in the project, doesn't create a node for the repository, and adds subnodes directly to the rootNode.
   */
  private void createRepoNode(@NotNull GitRepository repository, @NotNull GitCommitsByBranch commitsByBranch, @NotNull DefaultMutableTreeNode rootNode) {
    DefaultMutableTreeNode parentNode;
    if (GitUtil.justOneGitRepository(myProject)) {
      parentNode = rootNode;
    } else {
      parentNode = new CheckedTreeNode(repository);
      rootNode.add(parentNode);
    }

    for (GitBranch branch : sortBranches(commitsByBranch.getBranches())) {
      DefaultMutableTreeNode branchNode = createBranchNode(branch, commitsByBranch.get(branch));
      parentNode.add(branchNode);
    }
  }

  private static List<GitRepository> sortRepos(@NotNull Collection<GitRepository> repositories) {
    List<GitRepository> repos = new ArrayList<GitRepository>(repositories);
    Collections.sort(repos, new Comparator<GitRepository>() {
      @Override public int compare(GitRepository o1, GitRepository o2) {
        return o1.getPresentableUrl().compareTo(o2.getPresentableUrl());
      }
    });
    return repos;
  }

  private static List<GitBranch> sortBranches(@NotNull Collection<GitBranch> branches) {
    List<GitBranch> sortedBranches = new ArrayList<GitBranch>(branches);
    Collections.sort(sortedBranches, new Comparator<GitBranch>() {
      @Override public int compare(GitBranch o1, GitBranch o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    return sortedBranches;
  }

  private static DefaultMutableTreeNode createBranchNode(@NotNull GitBranch branch, @NotNull GitPushBranchInfo branchInfo) {
    DefaultMutableTreeNode branchNode = new CheckedTreeNode(new GitBranchPair(branch, branchInfo.getDestBranch()));
    for (GitCommit commit : branchInfo.getCommits()) {
      branchNode.add(new DefaultMutableTreeNode(commit));
    }
    return branchNode;
  }

  // Make changes available for diff action
  @Override
  public void calcData(DataKey key, DataSink sink) {
    if (VcsDataKeys.CHANGES.equals(key)) {
      DefaultMutableTreeNode[] selectedNodes = myTree.getSelectedNodes(DefaultMutableTreeNode.class, null);
      if (selectedNodes.length == 0) {
        return;
      }
      Object object = selectedNodes[0].getUserObject();
      if (object instanceof GitCommit) {
        sink.put(key, ArrayUtil.toObjectArray(((GitCommit)object).getChanges(), Change.class));
      }
    }
  }

  @NotNull
  public JComponent getPreferredFocusComponent() {
    return myTree;
  }

  void setCommits(@NotNull GitCommitsByRepoAndBranch commits) {
    createNodes(commits);
    myTreeModel.reload(myRootNode);
    TreeUtil.expandAll(myTree);
    repaint();
  }

  /**
   * @return repositories selected (via checkboxes) to be pushed.
   */
  Collection<GitRepository> getSelectedRepositories() {
    Collection<GitRepository> repositories = new ArrayList<GitRepository>(mySelectedRepositories.size());
    for (Map.Entry<GitRepository, Boolean> entry : mySelectedRepositories.entrySet()) {
      if (entry.getValue()) {
        repositories.add(entry.getKey());
      }
    }
    return repositories;
  }

  private static class MyTreeCellRenderer extends CheckboxTree.CheckboxTreeCellRenderer implements TreeCellRenderer {

    @Override
    public void customizeRenderer(final JTree tree, final Object value, final boolean selected, final boolean expanded, final boolean leaf, final int row, final boolean hasFocus) {
      Object userObject;
      if (value instanceof CheckedTreeNode) {
        userObject = ((CheckedTreeNode)value).getUserObject();
      } else if (value instanceof DefaultMutableTreeNode) {
        userObject = ((DefaultMutableTreeNode)value).getUserObject();
      } else {
        return;
      }

      if (userObject instanceof GitCommit) {
        ColoredTreeCellRenderer renderer = getTextRenderer();
        GitCommit commit = (GitCommit)userObject;

        Font font = EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN);
        renderer.setFont(font);
        SimpleTextAttributes small = new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, renderer.getForeground());
        renderer.append(commit.getShortHash().toString(), small);
        renderer.append(String.format("%15s  ", DateFormatUtil.formatPrettyDateTime(commit.getAuthorTime())), small);
        renderer.append(commit.getSubject(), small);
      }
      else if (userObject instanceof GitRepository) {
        getTextRenderer().append(((GitRepository)userObject).getPresentableUrl());
      }
      else if (userObject instanceof GitBranchPair) {
        GitBranchPair branchPair = (GitBranchPair) userObject;
        GitBranch fromBranch = branchPair.getBranch();
        GitBranch dest = branchPair.getDest();
        assert dest != null : "Destination branch can't be null for branch " + fromBranch;

        SimpleTextAttributes attrs = fromBranch.isActive() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
        getTextRenderer().append(fromBranch.getName() + " -> " + dest.getName(), attrs);
      }
    }
  }

}
