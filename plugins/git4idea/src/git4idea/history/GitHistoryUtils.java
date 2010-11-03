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

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.Semaphore;
import git4idea.*;
import git4idea.commands.*;
import git4idea.history.browser.GitCommit;
import git4idea.history.browser.SHAHash;
import git4idea.history.wholeTree.CommitHashPlusParents;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static git4idea.history.GitLogParser.GitLogOption.*;

/**
 * A collection of methods for retrieving history information from native Git.
 */
public class GitHistoryUtils {
  private final static Logger LOG = Logger.getInstance("#git4idea.history.GitHistoryUtils");

  private GitHistoryUtils() {
  }

  /**
   * Get current revision for the file under git in the current or specified branch.
   * 
   * @param project  a project
   * @param filePath file path to the file which revision is to be retrieved.
   * @param branch   name of branch or null if current branch wanted.
   * @return revision number or null if the file is unversioned or new.
   * @throws VcsException if there is a problem with running git.
   */
  @Nullable
  public static VcsRevisionNumber getCurrentRevision(final Project project, FilePath filePath, @Nullable String branch) throws VcsException {
    final VirtualFile root = GitUtil.getGitRoot(filePath);
    filePath = getLastCommitName(project, filePath);
    GitSimpleHandler h = new GitSimpleHandler(project, GitUtil.getGitRoot(filePath), GitCommand.LOG);
    GitLogParser parser = new GitLogParser(HASH, COMMIT_TIME);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("-n1", parser.getPretty());
    if (branch != null && !branch.isEmpty()) {
      h.addParameters(branch);
    }
    h.endOptions();
    h.addRelativePaths(filePath);
    String result = h.run();
    if (result.length() == 0) {
      return null;
    }
    final GitLogRecord record = parser.parseOneRecord(result);
    return new GitRevisionNumber(record.getHash(), record.getDate());
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
      return new ItemLatestState(getCurrentRevision(project, filePath, null), true, false);
    }
    filePath = getLastCommitName(project, filePath);
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    GitLogParser parser = new GitLogParser(HASH, COMMIT_TIME);
    parser.setNameInOutput(true);
    h.setNoSSH(true);
    h.setSilent(true);
    h.addParameters("-n1", parser.getPretty(), "--name-status", t.getFullName());
    h.endOptions();
    h.addRelativePaths(filePath);
    String result = h.run();
    if (result.length() == 0) {
      return null;
    }
    GitLogRecord record = parser.parseOneRecord(result);
    boolean exists = record.getNameStatus() != 'D';
    return new ItemLatestState(new GitRevisionNumber(record.getHash(), record.getDate()), exists, false);
  }

  /*
   === Smart full log with renames ===
   'git log --follow' does detect renames, but it has a bug - merge commits aren't handled properly: they just dissapear from the history.
   See http://kerneltrap.org/mailarchive/git/2009/1/30/4861054 and the whole thread about that: --follow is buggy, but maybe it won't be fixed.
   To get the whole history through renames we do the following:
   1. 'git log <file>' - and we get the history since the first rename, if there was one.
   2. 'git show -M --follow --name-status <first_commit_id> -- <file>'
      where <first_commit_id> is the hash of the first commit in the history we got in #1.
      With this command we get the rename-detection-friendly information about the first commit of the given file history.
      (by specifying the <file> we filter out other changes in that commit; but in that case rename detection requires '--follow' to work,
      that's safe for one commit though)
      If the first commit was ADDING the file, then there were no renames with this file, we have the full history.
      But if the first commit was RENAMING the file, we are going to query for the history before rename.
      Now we have the previous name of the file:

        ~/sandbox/git # git show --oneline --name-status -M 4185b97
        4185b97 renamed a to b
        R100    a       b

   3. 'git log <rename_commit_id> -- <previous_file_name>' - get the history of a before the given commit.
      We need to specify <rename_commit_id> here, because <previous_file_name> could have some new history, which has nothing common with our <file>.
      Then we repeat 2 and 3 until the first commit is ADDING the file, not RENAMING it.

    TODO: handle multiple repositories configuration: a file can be moved from one repo to another
   */
  public static void history(final Project project, FilePath path, final Consumer<GitFileRevision> consumer,
                             final Consumer<VcsException> exceptionConsumer) throws VcsException {
    // adjust path using change manager
    path = getLastCommitName(project, path);
    final VirtualFile root = GitUtil.getGitRoot(path);
    final GitLogParser logParser = new GitLogParser(HASH, COMMIT_TIME, AUTHOR_NAME, AUTHOR_EMAIL, COMMITTER_NAME, COMMITTER_EMAIL, PARENTS, SUBJECT, BODY);
    logParser.setNameInOutput(false);

    final AtomicReference<String> firstCommit = new AtomicReference<String>("HEAD");
    final AtomicReference<String> firstCommitParent = new AtomicReference<String>("HEAD");
    final AtomicReference<FilePath> currentPath = new AtomicReference<FilePath>(path);

    final Consumer<GitLogRecord> resultAdapter = new Consumer<GitLogRecord>() {
      public void consume(GitLogRecord record) {
        if (record == null) {
          exceptionConsumer.consume(new VcsException("revision details are null."));
          return;
        }
        final GitRevisionNumber revision = new GitRevisionNumber(record.getHash(), record.getDate());
        firstCommit.set(record.getHash());
        final String[] parentHashes = record.getParentsHashes();
        if (parentHashes == null || parentHashes.length < 1) {
          firstCommitParent.set(null);
        } else {
          firstCommitParent.set(parentHashes[0]);
        }
        final String message = record.getFullMessage();

        FilePath revisionPath = new FilePathImpl(root);
        try {
          final List<FilePath> paths = record.getFilePaths(root);
          if (paths.size() > 0) {
            revisionPath = paths.get(0);
          } else {
            // no paths are shown for merge commits, so we're using the saved path we're inspecting now
            revisionPath = currentPath.get();
          }

          final Pair<String, String> authorPair = Pair.create(record.getAuthorName(), record.getAuthorEmail());
          final Pair<String, String> committerPair = record.getCommitterName() == null ? null : Pair.create(record.getCommitterName(), record.getCommitterEmail());
          consumer.consume(new GitFileRevision(project, revisionPath, revision, Pair.create(authorPair, committerPair), message, null));
        } catch (VcsException e) {
          exceptionConsumer.consume(e);
        }
      }
    };

    while (currentPath.get() != null && firstCommitParent.get() != null) {
      GitLineHandler logHandler = getLogHandler(project, root, logParser, currentPath.get(), firstCommitParent.get());
      final MyTokenAccumulator accumulator = new MyTokenAccumulator(logParser);
      final Semaphore semaphore = new Semaphore();

      logHandler.addLineListener(new GitLineHandlerAdapter() {
        @Override
        public void onLineAvailable(String line, Key outputType) {
          final GitLogRecord record = accumulator.acceptLine(line);
          if (record != null) {
            resultAdapter.consume(record);
          }
        }

        @Override
        public void startFailed(Throwable exception) {
          //noinspection ThrowableInstanceNeverThrown
          exceptionConsumer.consume(new VcsException(exception));
          semaphore.up();
        }

        @Override
        public void processTerminated(int exitCode) {
          super.processTerminated(exitCode);
          final GitLogRecord record = accumulator.processLast();
          if (record != null) {
            resultAdapter.consume(record);
          }
          semaphore.up();
        }
      });
      semaphore.down();
      logHandler.start();
      semaphore.waitFor();

      currentPath.set(getFirstCommitRenamePath(project, root, firstCommit.get(), currentPath.get()));
    }

  }

  private static GitLineHandler getLogHandler(Project project, VirtualFile root, GitLogParser parser, FilePath path, String lastCommit) {
    final GitLineHandler h = new GitLineHandler(project, root, GitCommand.LOG);
    h.setNoSSH(true);
    h.setStdoutSuppressed(true);
    h.addParameters("--name-only", parser.getPretty(), "--encoding=UTF-8", lastCommit);
    h.endOptions();
    h.addRelativePaths(path);
    return h;
  }

  /**
   * Gets info of the given commit and checks if it was RENAME. If yes, returns the old file path, which file was renamed from.
   * If it's not a rename, returns null.
   */
  @Nullable
  private static FilePath getFirstCommitRenamePath(Project project, VirtualFile root, String commit, FilePath filePath) throws VcsException {
    final GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.SHOW);
    final GitLogParser parser = new GitLogParser(HASH);
    h.setNoSSH(true);
    h.setStdoutSuppressed(true);

    h.addParameters("-M", "--follow", "--name-status", parser.getPretty(), "--encoding=UTF-8", commit);
    h.endOptions();
    h.addRelativePaths(filePath);
    parser.setNameInOutput(true);
    final String output = h.run();
    final GitLogRecord record = parser.parseOneRecord(output);
    if (record.getNameStatus() == 'R') {
      final List<FilePath> paths = record.getFilePaths(root);
      final String message = "Rename commit should have 2 paths. Commit: " + commit;
      if (!LOG.assertTrue(paths.size() == 2, message + " Output: [" + output + "]")) {
        throw new VcsException(message);
      }
      return paths.get(0);
    }
    return null;
  }

  private static class MyTokenAccumulator {
    private final StringBuilder myBuffer = new StringBuilder();

    private boolean myNotStarted = true;
    private GitLogParser myParser;

    public MyTokenAccumulator(GitLogParser parser) {
      myParser = parser;
    }

    @Nullable
    public GitLogRecord acceptLine(String s) {
      final boolean lineEnd = s.startsWith(GitLogParser.RECORD_START);
      if (lineEnd && (!myNotStarted)) {
        final String line = myBuffer.toString();
        myBuffer.setLength(0);
        myBuffer.append(s.substring(GitLogParser.RECORD_START.length()));

        return processResult(line);
      }
      else {
        myBuffer.append(lineEnd ? s.substring(GitLogParser.RECORD_START.length()) : s);
        myBuffer.append("\n");
      }
      myNotStarted = false;

      return null;
    }

    public GitLogRecord processLast() {
      return processResult(myBuffer.toString());
    }

    private GitLogRecord processResult(final String line) {
      return myParser.parseOneRecord(line);
    }

  }

  /**
   * Get history for the file
   *
   * @param project the context project
   * @param path    the file path
   * @return the list of the revisions
   * @throws VcsException if there is problem with running git
   */
  public static List<VcsFileRevision> history(final Project project, final FilePath path) throws VcsException {
    final VirtualFile root = GitUtil.getGitRoot(path);
    return history(project, path, root);
  }

  /**
   * Get history for the file
   *
   * @param project the context project
   * @param path    the file path
   * @return the list of the revisions
   * @throws VcsException if there is problem with running git
   */
  public static List<VcsFileRevision> history(final Project project, FilePath path, final VirtualFile root, final String... parameters) throws VcsException {
    // adjust path using change manager
    path = getLastCommitName(project, path);
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    GitLogParser parser = new GitLogParser(HASH, COMMIT_TIME, AUTHOR_NAME, AUTHOR_EMAIL, COMMITTER_NAME, COMMITTER_EMAIL, SUBJECT, BODY);
    h.setNoSSH(true);
    h.setStdoutSuppressed(true);
    h.addParameters("-M", "--follow", "--name-only", parser.getPretty(), "--encoding=UTF-8");
    parser.setNameInOutput(false);
    if (parameters != null && parameters.length > 0) {
      h.addParameters(parameters);
    }
    h.endOptions();
    h.addRelativePaths(path);
    String output = h.run();

    final List<GitLogRecord> result = parser.parse(output);
    final List<VcsFileRevision> rc = new ArrayList<VcsFileRevision>();
    for (GitLogRecord record : result) {
      final GitRevisionNumber revision = new GitRevisionNumber(record.getHash(), record.getDate());
      final String message = record.getFullMessage();
      final FilePath revisionPath = record.getFilePaths(root).get(0);
      final Pair<String, String> authorPair = Pair.create(record.getAuthorName(), record.getAuthorEmail());
      final Pair<String, String> committerPair = record.getCommitterName() == null ? null : Pair.create(record.getCommitterName(), record.getCommitterEmail());
      rc.add(new GitFileRevision(project, revisionPath, revision, Pair.create(authorPair, committerPair), message, null));
    }
    return rc;
  }

  public static List<Pair<SHAHash, Date>> onlyHashesHistory(Project project, FilePath path, final String... parameters)
    throws VcsException {
    final VirtualFile root = GitUtil.getGitRoot(path);
    return onlyHashesHistory(project, path, root, parameters);
  }

  public static List<Pair<SHAHash, Date>> onlyHashesHistory(Project project, FilePath path, final VirtualFile root, final String... parameters)
    throws VcsException {
    // adjust path using change manager
    path = getLastCommitName(project, path);
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    GitLogParser parser = new GitLogParser(HASH, COMMIT_TIME);
    h.setNoSSH(true);
    h.setStdoutSuppressed(true);
    h.addParameters(parameters);
    h.addParameters(parser.getPretty(), "--encoding=UTF-8");
    h.endOptions();
    h.addRelativePaths(path);
    String output = h.run();

    final List<Pair<SHAHash, Date>> rc = new ArrayList<Pair<SHAHash, Date>>();
    for (GitLogRecord record : parser.parse(output)) {
      rc.add(new Pair<SHAHash, Date>(new SHAHash(record.getHash()), record.getDate()));
    }
    return rc;
  }

  public static List<GitCommit> historyWithLinks(Project project,
                                                 FilePath path,
                                                 final Set<String> allBranchesSet,
                                                 final String... parameters) throws VcsException {
    // adjust path using change manager
    path = getLastCommitName(project, path);
    final VirtualFile root = GitUtil.getGitRoot(path);
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    GitLogParser parser = new GitLogParser(SHORT_HASH, HASH, COMMIT_TIME, AUTHOR_NAME, AUTHOR_EMAIL, COMMITTER_NAME, COMMITTER_EMAIL, SHORT_PARENTS, REF_NAMES, SUBJECT, BODY);
    h.setNoSSH(true);
    h.setStdoutSuppressed(true);
    h.addParameters(parameters);
    h.addParameters("--name-only", parser.getPretty(), "--encoding=UTF-8");
    parser.setNameInOutput(false);
    h.endOptions();
    h.addRelativePaths(path);
    String output = h.run();

    final List<GitCommit> rc = new ArrayList<GitCommit>();
    for (GitLogRecord record : parser.parse(output)) {
      final Pair<List<String>, List<String>> tagsAndBranches = record.getTagsAndBranches(allBranchesSet);
      rc.add(new GitCommit(record.getShortHash(), new SHAHash(record.getHash()), record.getAuthorName(), record.getCommitterName(),
                           record.getDate(), record.getFullMessage(), new HashSet<String>(Arrays.asList(record.getParentsShortHashes())), record.getFilePaths(root), record.getAuthorEmail(),
                             record.getCommitterEmail(), tagsAndBranches.first, tagsAndBranches.second));
    }
    return rc;
  }

  public static List<GitCommit> commitsDetails(Project project,
                                                 FilePath path, Set<String> allBranchesSet,
                                                 final Collection<String> commitsIds) throws VcsException {
    // adjust path using change manager
    path = getLastCommitName(project, path);
    final VirtualFile root = GitUtil.getGitRoot(path);
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.SHOW);
    GitLogParser parser = new GitLogParser(SHORT_HASH, HASH, COMMIT_TIME, AUTHOR_NAME, AUTHOR_EMAIL, COMMITTER_NAME, COMMITTER_EMAIL, SHORT_PARENTS, REF_NAMES, SUBJECT, BODY);
    h.setNoSSH(true);
    h.setStdoutSuppressed(true);
    h.addParameters("--name-only", parser.getPretty(), "--encoding=UTF-8");
    parser.setNameInOutput(false);
    h.addParameters(new ArrayList<String>(commitsIds));

    h.endOptions();
    h.addRelativePaths(path);
    String output = h.run();

    final List<GitCommit> rc = new ArrayList<GitCommit>();
    for (GitLogRecord record : parser.parse(output)) {
      final Pair<List<String>, List<String>> tagsAndBranches = record.getTagsAndBranches(allBranchesSet);
      rc.add(new GitCommit(record.getShortHash(), new SHAHash(record.getHash()), record.getAuthorName(), record.getCommitterName(),
                           record.getDate(), record.getFullMessage(), new HashSet<String>(Arrays.asList(record.getParentsShortHashes())), record.getFilePaths(root), record.getAuthorEmail(),
                             record.getCommitterEmail(), tagsAndBranches.first, tagsAndBranches.second));
    }
    return rc;
  }

  public static Runnable hashesWithParents(Project project, FilePath path, final Consumer<CommitHashPlusParents> consumer, final String... parameters) throws VcsException {
    // adjust path using change manager
    path = getLastCommitName(project, path);
    final VirtualFile root = GitUtil.getGitRoot(path);
    final GitLineHandler h = new GitLineHandler(project, root, GitCommand.LOG);
    final GitLogParser parser = new GitLogParser(SHORT_HASH, COMMIT_TIME, SHORT_PARENTS);
    h.setNoSSH(true);
    h.setStdoutSuppressed(true);
    h.addParameters(parameters);
    h.addParameters(parser.getPretty(), "--encoding=UTF-8");

    h.endOptions();
    h.addRelativePaths(path);

    final Semaphore semaphore = new Semaphore();
    h.addLineListener(new GitLineHandlerListener() {
      @Override
      public void onLineAvailable(final String line, final Key outputType) {
        if (ProcessOutputTypes.STDOUT.equals(outputType)) {
          GitLogRecord record = parser.parseOneRecord(line);
          String hash = record.getShortHash();
          String[] parents = record.getParentsShortHashes();
          long time = record.getLongTimeStamp();
          consumer.consume(new CommitHashPlusParents(hash, parents, time));     // todo stop listen to output
        }
      }

      @Override
      public void processTerminated(int exitCode) {
        semaphore.up();
      }

      @Override
      public void startFailed(Throwable exception) {
        // todo
      }
    });
    semaphore.down();
    h.start();
    semaphore.waitFor();

    return new Runnable() {
      @Override
      public void run() {
        h.cancel();
      }
    };
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
