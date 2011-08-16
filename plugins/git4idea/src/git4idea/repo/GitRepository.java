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
package git4idea.repo;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import git4idea.GitBranch;
import git4idea.branch.GitBranchesCollection;
import git4idea.status.GitUntrackedFilesHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * <p>
 *   GitRepository is a representation of the Git repository stored under the specified directory.
 *   Its stores the information about the repository, which is frequently requested by other plugin components,
 *   thus all get-methods (like {@link #getCurrentRevision()}) are just getters of the correspondent fields.
 * </p>
 * <p>
 *   The GitRepository is updated "externally" by the {@link git4idea.repo.GitRepositoryUpdater}, when correspondent .git service files
 *   change. To force asynchronous update, it is enough to call {@link VirtualFile#refresh(boolean, boolean) refresh} on the root directory.
 * </p>
 * <p>
 *   To make a synchronous update of the repository call {@link #update(TrackedTopic...)} and specify
 *   which topics should be updated. Updating requires reading from disk, so updating {@link GitRepository.TrackedTopic.ALL} may take some time.
 * </p>
 * <p>
 *   Other components may subscribe to GitRepository changes via the {@link #GIT_REPO_CHANGE} {@link Topic}
 *   or preferably via {@link GitRepositoryManager#addListenerToAllRepositories(GitRepositoryChangeListener)}
 * </p>
 *
 * @author Kirill Likhodedov
 */
public final class GitRepository implements Disposable {

  public static final Topic<GitRepositoryChangeListener> GIT_REPO_CHANGE = Topic.create("GitRepository change", GitRepositoryChangeListener.class);

  private final Project myProject;
  private final VirtualFile myRootDir;
  private final GitRepositoryReader myReader;
  private final VirtualFile myGitDir;
  private final MessageBus myMessageBus;
  private final GitUntrackedFilesHolder myUntrackedFilesHolder;

  private volatile State myState;
  private volatile String myCurrentRevision;
  private volatile GitBranch myCurrentBranch;
  private volatile GitBranchesCollection myBranches = GitBranchesCollection.EMPTY;

  private final ReadWriteLock STATE_LOCK = new ReentrantReadWriteLock();
  private final ReadWriteLock CUR_REV_LOCK = new ReentrantReadWriteLock();
  private final ReadWriteLock CUR_BRANCH_LOCK = new ReentrantReadWriteLock();
  private final ReadWriteLock BRANCHES_LOCK = new ReentrantReadWriteLock();

  /**
   * Current state of the repository.
   * NORMAL   - HEAD is on branch, no merge process is in progress.
   * MERGING  - during merge (for instance, merge failed with conflicts that weren't immediately resolved).
   * REBASING - during rebase.
   * DETACHED - detached HEAD state, but not during rebase.
   */
  public enum State {
    NORMAL,
    MERGING {
      @Override public String toString() {
        return "Merging";
      }
    },
    REBASING {
      @Override public String toString() {
        return "Rebasing";
      }
    },
    DETACHED
  }

  /**
   * GitRepository tracks the updates of some information about Git repository, caches this information and provides methods to access to
   * it. The pieces of this information are called Topics. They can be used to update the repository.
   */
  public enum TrackedTopic {
    STATE {
      @Override void update(GitRepository repository) {
        repository.updateState();
      }
    },
    CURRENT_REVISION {
      @Override void update(GitRepository repository) {
        repository.updateCurrentRevision();
      }
    },
    CURRENT_BRANCH {
      @Override void update(GitRepository repository) {
        repository.updateCurrentBranch();
      }
    },
    BRANCHES {
      @Override void update(GitRepository repository) {
        repository.updateBranchList();        
      }
    },
    ALL_CURRENT {
      @Override void update(GitRepository repository) {
        STATE.update(repository);
        CURRENT_REVISION.update(repository);
        CURRENT_BRANCH.update(repository);
      }
    },
    ALL {
      @Override void update(GitRepository repository) {
        ALL_CURRENT.update(repository);
        BRANCHES.update(repository);
      }
    };

    abstract void update(GitRepository repository);
  }

  /**
   * Don't use this constructor - get the GitRepository instance from the {@link GitRepositoryManager}.
   */
  GitRepository(@NotNull VirtualFile rootDir, @NotNull Project project) {
    myRootDir = rootDir;
    myProject = project;
    myReader = new GitRepositoryReader(this);
    GitRepositoryUpdater updater = new GitRepositoryUpdater(this);
    Disposer.register(this, updater);

    myGitDir = myRootDir.findChild(".git");
    assert myGitDir != null : ".git directory wasn't found under " + rootDir.getPresentableUrl();
    
    myUntrackedFilesHolder = new GitUntrackedFilesHolder(rootDir, project);
    Disposer.register(this, myUntrackedFilesHolder);

    myMessageBus = project.getMessageBus();
    update(TrackedTopic.ALL);
  }

  @Override
  public void dispose() {
  }

  @NotNull
  public VirtualFile getRoot() {
    return myRootDir;
  }

  public Project getProject() {
    return myProject;
  }

  public GitUntrackedFilesHolder getUntrackedFilesHolder() {
    return myUntrackedFilesHolder;
  }

  @NotNull
  public State getState() {
    try {
      STATE_LOCK.readLock().lock();
      return myState;
    }
    finally {
      STATE_LOCK.readLock().unlock();
    }
  }

  /**
   * Returns the hash of the revision, which HEAD currently points to.
   * Returns null only in the case of a fresh repository, when no commit have been made.
   */
  @Nullable
  public String getCurrentRevision() {
    try {
      CUR_REV_LOCK.readLock().lock();
      return myCurrentRevision;
    }
    finally {
      CUR_REV_LOCK.readLock().unlock();
    }
  }

  /**
   * Returns the current branch of this Git repository.
   * If the repository is being rebased, then the current branch is the branch being rebased (which was current before the rebase
   * operation has started).
   * Returns null, if the repository is not on a branch and not in the REBASING state.
   */
  @Nullable
  public GitBranch getCurrentBranch() {
    try {
      CUR_BRANCH_LOCK.readLock().lock();
      return myCurrentBranch;
    }
    finally {
      CUR_BRANCH_LOCK.readLock().unlock();
    }
  }

  public boolean isMergeInProgress() {
    return getState() == State.MERGING;
  }

  public boolean isRebaseInProgress() {
    return getState() == State.REBASING;
  }

  public boolean isOnBranch() {
    return getState() != State.DETACHED && getState() != State.REBASING;
  }
  
  @NotNull
  public GitBranchesCollection getBranches() {
    try {
      BRANCHES_LOCK.readLock().lock();
      return myBranches;
    }
    finally {
      BRANCHES_LOCK.readLock().unlock();
    }
  }

  public void addListener(GitRepositoryChangeListener listener) {
    MessageBusConnection connection = myMessageBus.connect();
    Disposer.register(this, connection);
    connection.subscribe(GIT_REPO_CHANGE, listener);
  }

  /**
   * Synchronously updates the GitRepository by reading information from the specified topics.
   */
  public void update(TrackedTopic... topics) {
    for (TrackedTopic topic : topics) {
      topic.update(this);
    }
  }

  /**
   * Reads current state and notifies listeners about the change.
   */
  private void updateState() {
    try {
      STATE_LOCK.writeLock().lock();
      myState = myReader.readState();
    }
    finally {
      STATE_LOCK.writeLock().unlock();
    }

    notifyListeners();
  }

  /**
   * Reads current revision and notifies listeners about the change.
   */
  private void updateCurrentRevision() {
    try {
      CUR_REV_LOCK.writeLock().lock();
      myCurrentRevision = myReader.readCurrentRevision();
    }
    finally {
      CUR_REV_LOCK.writeLock().unlock();
    }

    notifyListeners();
  }

  /**
   * Reads current branch and notifies listeners about the change.
   */
  private void updateCurrentBranch() {
    try {
      CUR_BRANCH_LOCK.writeLock().lock();
      myCurrentBranch = myReader.readCurrentBranch();
    }
    finally {
      CUR_BRANCH_LOCK.writeLock().unlock();
    }

    notifyListeners();
  }
  
  private void updateBranchList() {
    GitBranchesCollection branches = myReader.readBranches();
    try {
      BRANCHES_LOCK.writeLock().lock();
      myBranches = branches;
    }
    finally {
      BRANCHES_LOCK.writeLock().unlock();
    }
    notifyListeners();
  }

  private void notifyListeners() {
    if (!Disposer.isDisposed(this)) {
      myMessageBus.syncPublisher(GIT_REPO_CHANGE).repositoryChanged();
    }
  }

}
