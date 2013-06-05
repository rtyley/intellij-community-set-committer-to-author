/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.zmlx.hg4idea.repo;

import com.intellij.dvcs.repo.RepoStateException;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.RepositoryUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads information about the Hg repository from Hg service files located in the {@code .hg} folder.
 * NB: works with {@link java.io.File}, i.e. reads from disk. Consider using caching.
 * Throws a {@link RepoStateException} in the case of incorrect Hg file format.
 *
 * @author Nadya Zabrodina
 */
public class HgRepositoryReader {

  private static Pattern BRANCH_PATTERN = Pattern.compile("\\s*(.+)\\s+(.+)");

  @NotNull private final File myHgDir;            // .hg
  @NotNull private final File myBranchHeadsFile;  // .hg/cache/branchheads (does not exist before first commit)
  @NotNull private final File myCurrentBranch;    // .hg/branch

  public HgRepositoryReader(@NotNull File hgDir) {
    myHgDir = hgDir;
    RepositoryUtil.assertFileExists(myHgDir, ".hg directory not found in " + myHgDir);
    File branchesFile = new File(new File(myHgDir, "cache"), "branchheads-served");  //branchheads-served exist after mercurial 2.5,
    //before 2.5 only branchheads exist
    myBranchHeadsFile = branchesFile.exists() ? branchesFile : new File(new File(myHgDir, "cache"), "branchheads");
    myCurrentBranch = new File(myHgDir, "branch");
  }

  /**
   * Finds current revision value.
   *
   * @return The current revision hash, or <b>{@code null}</b> if current revision is unknown - it is the initial repository state.
   */
  @Nullable
  public String readCurrentRevision() {
    if (checkIsFresh()) return null;
    String[] branchesWithHeads = RepositoryUtil.tryLoadFile(myBranchHeadsFile).split("\n");
    String head = branchesWithHeads[0];
    Matcher matcher = BRANCH_PATTERN.matcher(head);
    if (matcher.matches()) {
      return (matcher.group(1));
    }
    return null;
  }

  /**
   * Return current branch
   */
  @NotNull
  public String readCurrentBranch() {
    return branchExist() ? RepositoryUtil.tryLoadFile(myCurrentBranch) : HgRepository.DEFAULT_BRANCH;
  }

  @NotNull
  public List<String> readBranches() {
    List<String> branches = new ArrayList<String>();
    if (!checkIsFresh()) {
      String[] branchesWithHeads = RepositoryUtil.tryLoadFile(myBranchHeadsFile).split("\n");
      // first one - is a head revision: head hash + head number;
      for (int i = 1; i < branchesWithHeads.length; ++i) {
        Matcher matcher = BRANCH_PATTERN.matcher(branchesWithHeads[i]);
        if (matcher.matches()) {
          branches.add(matcher.group(2));
        }
      }
    }
    return branches;
  }

  public boolean isMergeInProgress() {
    return new File(myHgDir, "merge").exists();
  }

  @NotNull
  public Repository.State readState() {
    return isMergeInProgress() ? Repository.State.MERGING : Repository.State.NORMAL;
  }

  public boolean checkIsFresh() {
    return !myBranchHeadsFile.exists();
  }

  public boolean branchExist() {
    return myCurrentBranch.exists();
  }
}
