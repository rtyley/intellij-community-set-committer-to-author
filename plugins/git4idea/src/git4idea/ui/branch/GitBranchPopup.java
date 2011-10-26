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
package git4idea.ui.branch;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import git4idea.GitBranch;
import git4idea.process.GitBranchOperationsProcessor;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.validators.GitRefNameValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * <p>
 *   The popup which allows to quickly switch and control Git branches.
 * </p>
 * <p>
 *   Use {@link #asListPopup()} to achieve the {@link ListPopup} itself.
 * </p>
 * 
 * @author Kirill Likhodedov
 */
class GitBranchPopup  {

  private static final Logger LOG = Logger.getInstance(GitBranchPopup.class);
  
  private final Project myProject;
  private final GitRepository myRepository;
  private final ListPopup myPopup;

  ListPopup asListPopup() {
    return myPopup;
  }

  static GitBranchPopup getInstance(Project project, GitRepository repository) {
    return new GitBranchPopup(project, repository);
  }

  private GitBranchPopup(Project project, GitRepository repository) {
    myProject = project;
    myRepository = repository;

    String rootPostFix = GitRepositoryManager.getInstance(project).moreThanOneRoot() ? " on [" + repository.getRoot().getName() + "]" : "";
    String title = "Git Branches" + rootPostFix;

    myPopup = JBPopupFactory.getInstance().createActionGroupPopup(
      title, createActions(),
      SimpleDataContext.getProjectContext(project),
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);
  }

  private ActionGroup createActions() {
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);

    popupGroup.addAction(new CurrentBranchAction(myRepository));
    popupGroup.addAction(new NewBranchAction(myProject, myRepository));
    popupGroup.addAction(new CheckoutRevisionActions(myProject, myRepository));

    popupGroup.addSeparator("Local Branches");
    List<GitBranch> localBranches = new ArrayList<GitBranch>(myRepository.getBranches().getLocalBranches());
    Collections.sort(localBranches);
    for (GitBranch localBranch : localBranches) {
      if (!localBranch.equals(myRepository.getCurrentBranch())) { // don't show current branch in the list
        popupGroup.add(new LocalBranchActions(myProject, myRepository, localBranch.getName()));
      }
    }

    popupGroup.addSeparator("Remote Branches");
    List<GitBranch> remoteBranches = new ArrayList<GitBranch>(myRepository.getBranches().getRemoteBranches());
    Collections.sort(remoteBranches);
    for (GitBranch remoteBranch : remoteBranches) {
      popupGroup.add(new RemoteBranchActions(myProject, myRepository, remoteBranch.getName()));
    }
    
    popupGroup.addSeparator();
    popupGroup.addAction(new ConfigureAction());
    return popupGroup;
  }

  /**
   * "Current branch:" item which is disabled and is just a label to display the current branch.
   */
  private static class CurrentBranchAction extends DumbAwareAction {
    public CurrentBranchAction(GitRepository repository) {
      super("", String.format("Current branch [%s] in root [%s]", getBranchText(repository), repository.getRoot().getName()), null);
      getTemplatePresentation().setText("Current Branch: " + getBranchText(repository), false); // no mnemonics
    }

    private static String getBranchText(GitRepository repository) {
      return GitBranchUiUtil.getDisplayableBranchText(repository);
    }

    @Override public void actionPerformed(AnActionEvent e) {
    }

    @Override public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(false);         // this action works as a label
    }
  }

  private static class NewBranchAction extends DumbAwareAction {
    private final Project myProject;
    private final GitRepository myRepository;

    NewBranchAction(Project project, GitRepository repository) {
      super("New Branch", "Create and checkout new branch", IconLoader.getIcon("/general/add.png"));
      myProject = project;
      myRepository = repository;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final String name = Messages.showInputDialog(myProject, "Enter name of new branch", "Checkout New Branch", Messages.getQuestionIcon(),
                                             "", GitNewBranchNameValidator.newInstance(myRepository));
      if (name != null) {
        new GitBranchOperationsProcessor(myProject, myRepository).checkoutNewBranch(name);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      if (myRepository.isFresh()) {
        e.getPresentation().setEnabled(false);
        e.getPresentation().setDescription("Checkout of a new branch is not possible before the first commit.");
      }
    }
  }

  /**
   * Checkout manually entered tag or revision number.
   */
  private static class CheckoutRevisionActions extends DumbAwareAction {
    private final Project myProject;
    private final GitRepository myRepository;

    public CheckoutRevisionActions(Project project, GitRepository repository) {
      super("Checkout Tag or Revision");
      myProject = project;
      myRepository = repository;
    }

    @Override public void actionPerformed(AnActionEvent e) {
      // TODO autocomplete branches, tags.
      // on type check ref validity, on OK check ref existence.
      String reference = Messages.showInputDialog(myProject, "Enter reference (branch, tag) name or commit hash", "Checkout", Messages.getQuestionIcon());
      if (reference != null) {
        new GitBranchOperationsProcessor(myProject, myRepository).checkout(reference, null);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      if (myRepository.isFresh()) {
        e.getPresentation().setEnabled(false);
        e.getPresentation().setDescription("Checkout is not possible before the first commit.");
      }
    }
  }

  /**
   * Actions available for local branches.
   */
  private static class LocalBranchActions extends ActionGroup {

    private final Project myProject;
    private final GitRepository myRepository;
    private String myBranchName;

    LocalBranchActions(Project project, GitRepository repository, String branchName) {
      super("", true);
      myProject = project;
      myRepository = repository;
      myBranchName = branchName;
      getTemplatePresentation().setText(myBranchName, false); // no mnemonics
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[] {
        new CheckoutAction(myProject, myRepository, myBranchName),
        new CompareAction(myProject, myRepository, myBranchName),
        //new StashAndCheckoutAction(myProject, myRepository, myBranchName),
        new DeleteAction(myProject, myRepository, myBranchName)
      };
    }

    private static class CheckoutAction extends DumbAwareAction {
      private final Project myProject;
      private final GitRepository myRepository;
      private final String myBranchName;

      public CheckoutAction(Project project, GitRepository repository, String branchName) {
        super("Checkout");
        myProject = project;
        myRepository = repository;
        myBranchName = branchName;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        new GitBranchOperationsProcessor(myProject, myRepository).checkout(myBranchName, null);
      }

    }

    private static class StashAndCheckoutAction extends DumbAwareAction {
      public StashAndCheckoutAction(Project project, GitRepository repository, String branchName) {
        super("Stash && checkout");
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
      }

    }

    /**
     * Action to delete a branch.
     */
    private static class DeleteAction extends DumbAwareAction {
      private final Project myProject;
      private final GitRepository myRepository;
      private final String myBranchName;

      public DeleteAction(Project project, GitRepository repository, String branchName) {
        super("Delete");
        myProject = project;
        myRepository = repository;
        myBranchName = branchName;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        new GitBranchOperationsProcessor(myProject, myRepository).deleteBranch(myBranchName);
      }
    }
  }

  /**
   * Actions available for remote branches
   */
  private static class RemoteBranchActions extends ActionGroup {

    private final Project myProject;
    private final GitRepository myRepository;
    private String myBranchName;

    RemoteBranchActions(Project project, GitRepository repository, String branchName) {
      super("", true);
      myProject = project;
      myRepository = repository;
      myBranchName = branchName;
      getTemplatePresentation().setText(myBranchName, false); // no mnemonics
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[] {
        new CheckoutRemoteBranchAction(myProject, myRepository, myBranchName),
        new CompareAction(myProject, myRepository, myBranchName),
      };
    }

    private static class CheckoutRemoteBranchAction extends DumbAwareAction {
      private final Project myProject;
      private final GitRepository myRepository;
      private final String myRemoteBranchName;

      public CheckoutRemoteBranchAction(Project project, GitRepository repository, String remoteBranchName) {
        super("Checkout as new local branch");
        myProject = project;
        myRepository = repository;
        myRemoteBranchName = remoteBranchName;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        final String name = Messages.showInputDialog(myProject, "Enter name of new branch", "Checkout Remote Branch", Messages.getQuestionIcon(),
                                               guessBranchName(), GitNewBranchNameValidator.newInstance(myRepository));
        if (name != null) {
          new GitBranchOperationsProcessor(myProject, myRepository).checkoutNewTrackingBranch(name, myRemoteBranchName);
        }
      }

      private String guessBranchName() {
        // TODO: check if we already have a branch with that name; check if that branch tracks this remote branch. Show different messages
        int slashPosition = myRemoteBranchName.indexOf("/");
        LOG.assertTrue(slashPosition > 0, "Remote branch name should have a slash separator: [" + myRemoteBranchName + "]");
        return myRemoteBranchName.substring(slashPosition+1);
      }
    }

  }
  
  private static class CompareAction extends DumbAwareAction {

    private final Project myProject;
    private final GitRepository myRepository;
    private final String myBranchName;

    public CompareAction(Project project, GitRepository repository, String branchName) {
      super("Compare");
      myProject = project;
      myRepository = repository;
      this.myBranchName = branchName;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      new GitBranchOperationsProcessor(myProject, myRepository).compare(myBranchName);
    }

  }

  /**
   * "Configure" opens a dialog to configure branches in the repository, i.e. set up tracked branches, fetch/push branches, etc.
   */
  private static class ConfigureAction extends DumbAwareAction {
    public ConfigureAction() {
      super("Configure", null, IconLoader.getIcon("/general/ideOptions.png")); // TODO description
    }

    @Override public void actionPerformed(AnActionEvent e) {
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setVisible(false);
    }
  }


  /**
   * In addition to {@link GitRefNameValidator} checks that the entered branch name doesn't conflict with any existing local
   * or remote branch.
   */
  private static class GitNewBranchNameValidator implements InputValidatorEx {
    
    private final GitRepository myRepository;
    private String myErrorText;
    private Set<String> myLocalNames = new HashSet<String>();
    private Set<String> myRemoteNames = new HashSet<String>();

    private GitNewBranchNameValidator(GitRepository repository) {
      myRepository = repository;
      cacheBranchNames();
    }

    private void cacheBranchNames() {
      for (GitBranch branch : myRepository.getBranches().getLocalBranches()) {
        myLocalNames.add(branch.getName());
      }
      for (GitBranch branch : myRepository.getBranches().getRemoteBranches()) {
        myRemoteNames.add(branch.getName());
      }
    }

    static GitNewBranchNameValidator newInstance(GitRepository repository) {
      return new GitNewBranchNameValidator(repository);
    }

    @Override
    public boolean checkInput(String inputString) {
      if (!GitRefNameValidator.getInstance().checkInput(inputString)){
        myErrorText = "Invalid name for branch";
        return false;
      } 
      return checkBranchConflict(inputString); 
    }

    private boolean checkBranchConflict(String inputString) {
      if (myLocalNames.contains(inputString)) {
        myErrorText = "Branch " + inputString + " already exists";
        return false;
      }
      if (myRemoteNames.contains(inputString)) {
        myErrorText = "Branch name " + inputString + " clashes with remote branch with the same name";
        return false;
      }
      myErrorText = null;
      return true;
    }

    @Override
    public boolean canClose(String inputString) {
      return checkInput(inputString);
    }

    @Override
    public String getErrorText(String inputString) {
      return myErrorText;
    }
  }

}
