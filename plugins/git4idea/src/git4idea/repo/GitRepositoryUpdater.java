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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBusConnection;
import git4idea.commands.GitFileUtils;

import java.util.List;

/**
 * Listens to .git service files changes and updates {@link GitRepository} when needed.
 * @author Kirill Likhodedov
 */
final class GitRepositoryUpdater implements Disposable, BulkFileListener {

  private final GitRepository myRepository;
  private final GitRepositoryFiles myRepositoryFiles;
  private final MessageBusConnection myMessageBusConnection;

  GitRepositoryUpdater(GitRepository repository) {
    myRepository = repository;
    VirtualFile root = repository.getRoot();

    VirtualFile gitDir = root.findChild(".git");
    assert gitDir != null;
    LocalFileSystem.getInstance().addRootToWatch(gitDir.getPath(), true);

    myRepositoryFiles = GitRepositoryFiles.getInstance(root);
    myMessageBusConnection = repository.getProject().getMessageBus().connect();
    myMessageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, this);
  }

  @Override
  public void dispose() {
    myMessageBusConnection.disconnect();
  }

  @Override
  public void before(List<? extends VFileEvent> events) {
    // everything is handled in #after()
  }

  @Override
  public void after(List<? extends VFileEvent> events) {
    // which files in .git were changed
    boolean headChanged = false;
    boolean branchFileChanged = false;
    boolean packedRefsChanged = false;
    boolean rebaseFileChanged = false;
    boolean mergeFileChanged = false;
    for (VFileEvent event : events) {
      final VirtualFile file = event.getFile();
      if (file == null) {
        continue;
      }
      String filePath = GitFileUtils.stripFileProtocolPrefix(file.getPath());
      if (myRepositoryFiles.isHeadFile(filePath)) {
        headChanged = true;
      } else if (myRepositoryFiles.isBranchFile(filePath) || myRepositoryFiles.isRemoteBranchFile(filePath)) {
        branchFileChanged = true;
      } else if (myRepositoryFiles.isPackedRefs(filePath)) {
        packedRefsChanged = true;
      } else if (myRepositoryFiles.isRebaseFile(filePath)) {
        rebaseFileChanged = true;
      } else if (myRepositoryFiles.isMergeFile(filePath)) {
        mergeFileChanged = true;
      }
    }

    // what should be updated in GitRepository
    boolean updateCurrentBranch = false;
    boolean updateCurrentRevision = false;
    boolean updateState = false;
    boolean updateBranches = false;
    if (headChanged) {
      updateCurrentBranch = true;
      updateCurrentRevision = true;
      updateState = true;
    }
    if (branchFileChanged) {
      updateCurrentRevision = true;
      updateBranches = true;
    }
    if (rebaseFileChanged || mergeFileChanged) {
      updateState = true;
    }
    if (packedRefsChanged) {
      updateCurrentBranch = true;
      updateBranches = true;
    }

    // update GitRepository on pooled thread, because it requires reading from disk and parsing data.
    if (updateCurrentBranch) {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          myRepository.update(GitRepository.TrackedTopic.CURRENT_BRANCH);
        }
      });
    }
    if (updateCurrentRevision) {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          myRepository.update(GitRepository.TrackedTopic.CURRENT_REVISION);
        }
      });
    }
    if (updateState) {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          myRepository.update(GitRepository.TrackedTopic.STATE);
        }
      });
    }
    if (updateBranches) {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          myRepository.update(GitRepository.TrackedTopic.BRANCHES);
        }
      });
    }
  }

}
