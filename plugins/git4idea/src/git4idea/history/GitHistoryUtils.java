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
package git4idea.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.StringTokenizer;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.*;
import git4idea.commands.GitHandler;
import git4idea.commands.GitSimpleHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * History utilities for Git
 */
public class GitHistoryUtils {
  /**
   * The logger for the utilities
   */
  private final static Logger LOG = Logger.getInstance("#git4idea.history.GitHistoryUtils");

  /**
   * A private constructor
   */
  private GitHistoryUtils() {
  }

  /**
   * Get current revision for the file under git
   *
   * @param project  a project
   * @param filePath a file path
   * @return a revision number or null if the file is unversioned or new
   * @throws VcsException if there is problem with running git
   */
  @Nullable
  public static VcsRevisionNumber getCurrentRevision(final Project project, FilePath filePath) throws VcsException {
    filePath = getLastCommitName(project, filePath);
    GitSimpleHandler h = new GitSimpleHandler(project, GitUtil.getGitRoot(filePath), GitHandler.LOG);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("-n1", "--pretty=format:%H%n%ct%n");
    h.endOptions();
    h.addRelativePaths(filePath);
    String result = h.run();
    if (result.length() == 0) {
      return null;
    }
    String[] lines = result.split("\n");
    String hash = lines[0];
    Date commitDate = GitUtil.parseTimestamp(lines[1]);
    return new GitRevisionNumber(hash, commitDate);
  }

  /**
   * Get current revision for the file under git
   *
   * @param project  a project
   * @param filePath a file path
   * @return a revision number or null if the file is unversioned or new
   * @throws VcsException if there is problem with running git
   */
  @Nullable
  public static ItemLatestState getLastRevision(final Project project, FilePath filePath) throws VcsException {
    VirtualFile root = GitUtil.getGitRoot(filePath);
    GitBranch c = GitBranch.current(project, root);
    GitBranch t = c == null ? null : c.tracked(project, root);
    if (t == null) {
      return new ItemLatestState(getCurrentRevision(project, filePath), true);
    }
    filePath = getLastCommitName(project, filePath);
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitHandler.LOG);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("-n1", "--pretty=format:%H%n%ct", "--name-status", t.getFullName());
    h.endOptions();
    h.addRelativePaths(filePath);
    String result = h.run();
    if (result.length() == 0) {
      return null;
    }
    String[] lines = result.split("\n");
    if (lines.length < 3) {
      LOG.error("Unsupported result format: " + result);
      return null;
    }
    String hash = lines[0];
    Date commitDate = GitUtil.parseTimestamp(lines[1]);
    return new ItemLatestState(new GitRevisionNumber(hash, commitDate), lines[2].charAt(0) != 'D');
  }


  /**
   * Get history for the file
   *
   * @param project the context project
   * @param path    the file path
   * @return the list of the revisions
   * @throws VcsException if there is problem with running git
   */
  public static List<VcsFileRevision> history(Project project, FilePath path) throws VcsException {
    // adjust path using change manager
    path = getLastCommitName(project, path);
    final VirtualFile root = GitUtil.getGitRoot(path);
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitHandler.LOG);
    h.setNoSSH(true);
    h.setStdoutSuppressed(true);
    h.addParameters("-M", "--follow", "--name-only",
                    "--pretty=format:%H%x00%ct%x00%an%x20%x3C%ae%x3E%x00%cn%x20%x3C%ce%x3E%x00%s%n%n%b%x00", "--encoding=UTF-8");
    h.endOptions();
    h.addRelativePaths(path);
    String output = h.run();
    List<VcsFileRevision> rc = new ArrayList<VcsFileRevision>();
    StringTokenizer tk = new StringTokenizer(output, "\u0000\n", false);
    String prefix = root.getPath() + "/";
    while (tk.hasMoreTokens()) {
      final GitRevisionNumber revision = new GitRevisionNumber(tk.nextToken("\u0000\n"), GitUtil.parseTimestamp(tk.nextToken("\u0000")));
      final String author = GitUtil.adjustAuthorName(tk.nextToken("\u0000"), tk.nextToken("\u0000"));
      final String message = tk.nextToken("\u0000").trim();
      final FilePath revisionPath = VcsUtil.getFilePathForDeletedFile(prefix + GitUtil.unescapePath(tk.nextToken("\u0000\n")), false);
      rc.add(new GitFileRevision(project, revisionPath, revision, author, message, null));
    }
    return rc;
  }

  /**
   * Get name of the file in the last commit. If file was renamed, returns the previous name.
   *
   * @param project the context project
   * @param path    the path to check
   * @return the name of file in the last commit or argument
   */
  public static FilePath getLastCommitName(final Project project, FilePath path) {
    final ChangeListManager changeManager = ChangeListManager.getInstance(project);
    final Change change = changeManager.getChange(path);
    if (change != null && change.getType() == Change.Type.MOVED) {
      GitContentRevision r = (GitContentRevision)change.getBeforeRevision();
      assert r != null : "Move change always have beforeRevision";
      path = r.getFile();
    }
    return path;
  }
}
