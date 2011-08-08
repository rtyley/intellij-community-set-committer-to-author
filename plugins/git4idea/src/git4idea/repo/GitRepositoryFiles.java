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

import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitFileUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Stores paths to Git service files (from .git/ directory) that are used by IDEA, and provides test-methods to check if a file
 * matches once of them.
 *
 * @author Kirill Likhodedov
 */
public class GitRepositoryFiles {
  
  private final String myHeadFilePath;
  private final String myIndexFilePath;
  private final String myMergeHeadPath;
  private final String myRebaseApplyPath;
  private final String myRebaseMergePath;
  private final String myPackedRefsPath;
  private final String myRefsHeadsDirPath;
  private final String myRefsRemotesDirPath;

  public static GitRepositoryFiles getInstance(@NotNull VirtualFile root) {
    // maybe will be cached later to store a single GitRepositoryFiles for a root. 
    return new GitRepositoryFiles(root);
  }

  private GitRepositoryFiles(@NotNull VirtualFile root) {
    // add .git/ and .git/refs/heads to the VFS
    VirtualFile gitDir = root.findChild(".git");
    assert gitDir != null;

    // save paths of the files, that we will watch
    String gitDirPath = GitFileUtils.stripFileProtocolPrefix(gitDir.getPath());
    myHeadFilePath = gitDirPath + "/HEAD";
    myIndexFilePath = gitDirPath + "/index";
    myMergeHeadPath = gitDirPath + "/MERGE_HEAD";
    myRebaseApplyPath = gitDirPath + "/rebase-apply";
    myRebaseMergePath = gitDirPath + "/rebase-merge";
    myPackedRefsPath = gitDirPath + "/packed-refs";
    myRefsHeadsDirPath = gitDirPath + "/refs/heads";
    myRefsRemotesDirPath = gitDirPath + "/refs/remotes";
  }

  /**
   * .git/index
   */
  public boolean isIndexFile(String filePath) {
    return filePath.equals(myIndexFilePath);
  }

  /**
   * .git/HEAD
   */
  public boolean isHeadFile(String file) {
    return file.equals(myHeadFilePath);
  }

  /**
   * Any file in .git/refs/heads, i.e. a branch reference file.
   */
  public boolean isBranchFile(String filePath) {
    return filePath.startsWith(myRefsHeadsDirPath);
  }

  /**
   * Any file in .git/refs/remotes, i.e. a remote branch reference file.
   */
  public boolean isRemoteBranchFile(String filePath) {
    return filePath.startsWith(myRefsRemotesDirPath);
  }

  /**
   * .git/rebase-merge or .git/rebase-apply
   */
  public boolean isRebaseFile(String path) {
    return path.equals(myRebaseApplyPath) || path.equals(myRebaseMergePath);
  }

  /**
   * .git/MERGE_HEAD
   */
  public boolean isMergeFile(String file) {
    return file.equals(myMergeHeadPath);
  }

  /**
   * .git/packed-refs
   */
  public boolean isPackedRefs(String file) {
    return file.equals(myPackedRefsPath);
  }

 
}
