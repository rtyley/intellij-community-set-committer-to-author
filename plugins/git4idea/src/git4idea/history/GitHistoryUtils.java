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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CollectConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.text.StringTokenizer;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.*;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitLineHandlerAdapter;
import git4idea.commands.GitSimpleHandler;
import git4idea.history.browser.GitCommit;
import git4idea.history.browser.SHAHash;
import git4idea.history.wholeTree.CommitHashPlusParents;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A collection of methods for retrieving history information from native Git.
 */
public class GitHistoryUtils {
  private final static Logger LOG = Logger.getInstance("#git4idea.history.GitHistoryUtils");

  private GitHistoryUtils() {
  }

  /**
   * Get current revision for the file under git
   *
   * @param project  a project
   * @param filePath file path to the file which revision is to be retrieved.
   * @return revision number or null if the file is unversioned or new.
   * @throws VcsException if there is a problem with running git.
   */
  @Nullable
  public static VcsRevisionNumber getCurrentRevision(final Project project, FilePath filePath) throws VcsException {
    filePath = getLastCommitName(project, filePath);
    GitSimpleHandler h = new GitSimpleHandler(project, GitUtil.getGitRoot(filePath), GitCommand.LOG);
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
      return new ItemLatestState(getCurrentRevision(project, filePath), true, false);
    }
    filePath = getLastCommitName(project, filePath);
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
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
    String hash = lines[0];
    boolean exists = lines.length < 3 || lines[2].charAt(0) != 'D';
    Date commitDate = GitUtil.parseTimestamp(lines[1]);
    return new ItemLatestState(new GitRevisionNumber(hash, commitDate), exists, false);
  }

  public static void history(final Project project, FilePath path, final Consumer<GitFileRevision> consumer,
                             final Consumer<VcsException> exceptionConsumer) throws VcsException {
    // adjust path using change manager
    path = getLastCommitName(project, path);
    final VirtualFile root = GitUtil.getGitRoot(path);
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.LOG);
    h.setNoSSH(true);
    h.setStdoutSuppressed(true);
    h.addParameters("-M", "--follow", "--name-only",
                    "--pretty=tformat:%x03%x01%x03%H%x03%ct%x03%an%x20%x3C%ae%x3E%x03%cn%x20%x3C%ce%x3E%x03%x02%x03%s%x03%b%x03%x02%x01",
                    "--encoding=UTF-8");
    h.endOptions();
    h.addRelativePaths(path);

    final String prefix = root.getPath() + "/";
    final MyTokenAccumulator accumulator = new MyTokenAccumulator(6);

    final Consumer<LowLevelRevisionDetails> resultAdapter = new Consumer<LowLevelRevisionDetails>() {
      public void consume(LowLevelRevisionDetails revisionDetails) {
        if (revisionDetails == null) {
          exceptionConsumer.consume(new VcsException("revision details are null."));
          return;
        }
        if (revisionDetails.missesRequiredFields()) {
          exceptionConsumer.consume(new VcsException("revision misses hash or timestamp data."));
          return;
        }
        final GitRevisionNumber revision = new GitRevisionNumber(revisionDetails.hash, GitUtil.parseTimestamp(revisionDetails.timestamp));
        final String author = GitUtil.adjustAuthorName(revisionDetails.getAuthor(), revisionDetails.getCommitter());
        final String message = revisionDetails.getComment();

        String path = revisionDetails.getPath();
        try {
          if (!path.isEmpty()) {
            path = GitUtil.unescapePath(path);
          }
        } catch (VcsException e) {
          exceptionConsumer.consume(e);
        }
        final FilePath revisionPath = VcsUtil.getFilePathForDeletedFile(prefix + path, false);
        consumer.consume(new GitFileRevision(project, revisionPath, revision, author, message, null));
      }
    };

    final Semaphore semaphore = new Semaphore();
    h.addLineListener(new GitLineHandlerAdapter() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        final LowLevelRevisionDetails result = accumulator.acceptLine(line);
        if (result != null && !result.missesRequiredFields()) {
          resultAdapter.consume(result);
        }
      }

      @Override
      public void startFailed(Throwable exception) {
        //noinspection ThrowableInstanceNeverThrown
        exceptionConsumer.consume(new VcsException(exception));
      }

      @Override
      public void processTerminated(int exitCode) {
        super.processTerminated(exitCode);
        final LowLevelRevisionDetails result = accumulator.processLast();
        if (result != null && !result.missesRequiredFields()) {
          resultAdapter.consume(result);
        }
        for (VcsException e : accumulator.exceptions) {
          exceptionConsumer.consume(e);
        }
        semaphore.up();
      }
    });
    semaphore.down();
    h.start();
    semaphore.waitFor();
  }

  /**
   * Container for revision details retrieved from the git log.
   */
  private static class LowLevelRevisionDetails {
    private String hash;
    private String timestamp;
    private String author;
    private String committer;
    private String comment;
    private String path;

    /**
     * Fills details from the given list of Strings. The order of details is assumed to be the following:
     * hash, timestamp, author, committer, comment, path.
     * If less than 6 details are given, the rest instance variables remain null.
     * If more than 6 details are supplied, the rest are ignored and the message is printed to the LOG.
     * @param details List of details about revision information.
     * @return LowLevelRevisionDetails with all or some details filled.
     */
    static LowLevelRevisionDetails fillDetails(List<String> details) {
      final LowLevelRevisionDetails result = new LowLevelRevisionDetails();
      final int size = details.size();
      switch (size) {
        default: LOG.info("Unexpectedly received more than 6 revision details: " + details);
        case 6: result.path = details.get(5);
        case 5: result.comment = details.get(4);
        case 4: result.committer = details.get(3);
        case 3: result.author = details.get(2);
        case 2: result.timestamp = details.get(1);
        case 1: result.hash = details.get(0);
        case 0: break;
      }
      return result;
    }

    /**
     * Hash and timestamps are required. Revision is completely invalid if missing those and will be treated respectively.
     * @return True if hash or timestamp is not set in this details.
     */
    public boolean missesRequiredFields() {
      return hash == null || timestamp == null;
    }

    /*
     * Following 4 getter methods return the field value if it not null.
     * If it is null, an empty String is returned, and a message is printed to the LOG. 
     */

    public String getAuthor() {
      if (author != null) {
        return author;
      }
      LOG.info("revision details misses author data, using empty: " + this);
      return "";
    }

    public String getCommitter() {
      if (committer != null) {
        return committer;
      }
      LOG.info("revision details misses committer data, using empty: " + this);
      return "";
    }

    public String getComment() {
      if (comment != null) {
        return comment;
      }
      LOG.info("revision details misses comment message, using empty: " + this);
      return "";
    }

    public String getPath() {
      if (path != null) {
        return path;
      }
      LOG.info("revision details misses path to file, using empty: " + this);
      return "";
    }

    @Override
    public String toString() {
      return String.format("LowLevelRevisionDetails{path=%s, comment='%s', committer=%s, author=%s, timestamp=%s, hash=%s}", path,
                comment, committer, author, timestamp, hash);
    }
  }

  private static class MyTokenAccumulator {
    // %x03%x02%x03%s%x03%x02%x01%b%x03%x01%x03
    private final static String ourCommentStartMark = "\u0003\u0002\u0003";
    private final static String ourCommentEndMark = "\u0003\u0002\u0001";
    private final static String ourLineEndMark = "\u0003\u0001\u0003";
    private static final String TOKEN_DELIMITER = "\u0003";
    private static final String LINE_DELIMITER = "\u0002";

    private final StringBuilder myBuffer = new StringBuilder();
    private final int myMax;
    private final Collection<VcsException> exceptions = new ArrayList<VcsException>(1);

    private boolean myNotStarted = true;

    private MyTokenAccumulator(final int max) {
      myMax = max;
    }

    @Nullable
    public LowLevelRevisionDetails acceptLine(String s) {
      final boolean lineEnd = s.startsWith(ourLineEndMark);
      if (lineEnd && (!myNotStarted)) {
        final String line = myBuffer.toString();
        myBuffer.setLength(0);
        myBuffer.append(s.substring(ourLineEndMark.length()));

        return processResult(line);
      }
      else {
        myBuffer.append(lineEnd ? s.substring(ourLineEndMark.length()) : s);
        myBuffer.append(LINE_DELIMITER);
      }
      myNotStarted = false;

      return null;
    }

    public LowLevelRevisionDetails processLast() {
      return processResult(myBuffer.toString());
    }

    private LowLevelRevisionDetails processResult(final String line) {
      final int commentStartIdx = line.indexOf(ourCommentStartMark);
      if (commentStartIdx == -1) {
        LOG.info("Git history: no comment mark in line: '" + line + "'");
        // todo remove this when clarifyed
        java.util.StringTokenizer tk = new java.util.StringTokenizer(line, TOKEN_DELIMITER, false);
        final List<String> result = new ArrayList<String>();
        while (tk.hasMoreElements()) {
          final String token = tk.nextToken();
          result.add(token);
        }
        return LowLevelRevisionDetails.fillDetails(result);
      }

      final String start = line.substring(0, commentStartIdx);
      final java.util.StringTokenizer tk = new java.util.StringTokenizer(start, TOKEN_DELIMITER, false);
      final List<String> result = new ArrayList<String>();
      while (tk.hasMoreElements()) {
        final String token = tk.nextToken();
        result.add(token);
      }
      final LowLevelRevisionDetails revisionDetails = LowLevelRevisionDetails.fillDetails(result);
      
      final String commentAndPath = line.substring(commentStartIdx + ourCommentStartMark.length());
      final int commentEndIdx = commentAndPath.indexOf(ourCommentEndMark);
      if (commentEndIdx > -1) {
        revisionDetails.comment = replaceDelimitersByNewlines(commentAndPath.substring(0, commentEndIdx));
        revisionDetails.path = replaceDelimitersByNewlines(commentAndPath.substring(commentEndIdx + ourCommentEndMark.length()));
      } else {
        exceptions.add(new VcsException("git log output is uncomplete"));
        revisionDetails.comment = replaceDelimitersByNewlines(commentAndPath);
        revisionDetails.path = "";   // empty path
      }
      return revisionDetails;
    }

    private static String replaceDelimitersByNewlines(String s) {
      return s.replace(TOKEN_DELIMITER, "\n").replace(LINE_DELIMITER, "\n").trim();
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
  public static List<VcsFileRevision> history(Project project, FilePath path) throws VcsException {
    // adjust path using change manager
    path = getLastCommitName(project, path);
    final VirtualFile root = GitUtil.getGitRoot(path);
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    h.setNoSSH(true);
    h.setStdoutSuppressed(true);
    h.addParameters("-M", "--follow", "--name-only",
                    "--pretty=format:%H%x03%ct%x03%an%x20%x3C%ae%x3E%x03%cn%x20%x3C%ce%x3E%x03%s%n%n%b%x03", "--encoding=UTF-8");
    h.endOptions();
    h.addRelativePaths(path);
    String output = h.run();
    List<VcsFileRevision> rc = new ArrayList<VcsFileRevision>();
    StringTokenizer tk = new StringTokenizer(output, "\u0003\n", false);
    String prefix = root.getPath() + "/";
    while (tk.hasMoreTokens()) {
      final GitRevisionNumber revision = new GitRevisionNumber(tk.nextToken("\u0003\n"), GitUtil.parseTimestamp(tk.nextToken("\u0003")));
      final String author = GitUtil.adjustAuthorName(tk.nextToken("\u0003"), tk.nextToken("\u0003"));
      final String message = tk.nextToken("\u0003").trim();
      final FilePath revisionPath = VcsUtil.getFilePathForDeletedFile(prefix + GitUtil.unescapePath(tk.nextToken("\u0003\n")), false);
      rc.add(new GitFileRevision(project, revisionPath, revision, author, message, null));
    }
    return rc;
  }

  public static List<Pair<SHAHash, Date>> onlyHashesHistory(Project project, FilePath path, final String... parameters)
    throws VcsException {
    // adjust path using change manager
    path = getLastCommitName(project, path);
    final VirtualFile root = GitUtil.getGitRoot(path);
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    h.setNoSSH(true);
    h.setStdoutSuppressed(true);
    h.addParameters(parameters);
    h.addParameters("--name-only", "--pretty=format:%x03%H%x03%ct%x03", "--encoding=UTF-8");
    h.endOptions();
    h.addRelativePaths(path);
    String output = h.run();
    final List<Pair<SHAHash, Date>> rc = new ArrayList<Pair<SHAHash, Date>>();
    StringTokenizer tk = new StringTokenizer(output, "\u0003", false);

    while (tk.hasMoreTokens()) {
      final String line = tk.nextToken();
      final StringTokenizer tk2 = new StringTokenizer(line, "\u0003\n", false);
      final String hash = tk2.nextToken("\u0003\n");
      final String dateString = tk2.nextToken("\u0003");
      final Date date = GitUtil.parseTimestamp(dateString);
      rc.add(new Pair<SHAHash, Date>(new SHAHash(hash), date));
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
    h.setNoSSH(true);
    h.setStdoutSuppressed(true);
    // todo think of name+email linkage in a single field
    h.addParameters(parameters);
    /*h.addParameters("-M", "--follow", "--name-only",
                    "--pretty=format:%x03%H%x03%ct%x03%an%x03%ae%x03%cn%x03%ce%x03[%P]%x03[%d]%x03%s%n%n%b%x03", "--encoding=UTF-8");*/
    h.addParameters("--name-only",
                    "--pretty=format:%x03%h%x03%H%x03%ct%x03%an%x03%ae%x03%cn%x03%ce%x03[%p]%x03[%d]%x03%s%n%n%b%x03", "--encoding=UTF-8");

    h.endOptions();
    h.addRelativePaths(path);
    String output = h.run();
    return parseCommitsLoadOutput(allBranchesSet, root, output);
  }

  public static List<GitCommit> commitsDetails(Project project,
                                                 FilePath path, Set<String> allBranchesSet,
                                                 final Collection<String> commitsIds) throws VcsException {
    // adjust path using change manager
    path = getLastCommitName(project, path);
    final VirtualFile root = GitUtil.getGitRoot(path);
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.SHOW);
    h.setNoSSH(true);
    h.setStdoutSuppressed(true);
    h.addParameters("--name-only",
                    "--pretty=format:%x03%h%x03%H%x03%ct%x03%an%x03%ae%x03%cn%x03%ce%x03[%p]%x03[%d]%x03%s%n%n%b%x03", "--encoding=UTF-8");
    h.addParameters(new ArrayList<String>(commitsIds));

    h.endOptions();
    h.addRelativePaths(path);
    String output = h.run();
    return parseCommitsLoadOutput(allBranchesSet, root, output);
  }

  private static List<GitCommit> parseCommitsLoadOutput(Set<String> allBranchesSet, VirtualFile root, String output) throws VcsException {
    final List<GitCommit> rc = new ArrayList<GitCommit>();
    StringTokenizer tk = new StringTokenizer(output, "\u0003", false);
    final String prefix = root.getPath() + "/";

    while (tk.hasMoreTokens()) {
      final String line = tk.nextToken();
      final StringTokenizer tk2 = new StringTokenizer(line, "\u0003\n", false);
      //while (tk2.hasMoreTokens()) {
      final String shortHash = tk2.nextToken("\u0003");
      final String hash = tk2.nextToken("\u0003\n");
      final String dateString = tk2.nextToken("\u0003");
      final Date date = GitUtil.parseTimestamp(dateString);
      final String authorName = tk2.nextToken("\u0003");
      final String authorEmail = tk2.nextToken("\u0003");
      final String committerName = tk2.nextToken("\u0003");
      final String committerEmail = tk2.nextToken("\u0003");
      // parent hashes
      final String parents = removeSquareBraces(tk2.nextToken("\u0003"));
      final Set<String> parentsHashes;
      if (!StringUtil.isEmptyOrSpaces(parents)) {
        final String[] parentsSplit = parents.split(" "); // todo if parent = 000000
        parentsHashes = new HashSet<String>();
        for (String s : parentsSplit) {
          parentsHashes.add(s);
        }
      }
      else {
        parentsHashes = Collections.emptySet();
      }
      // decorate
      final String decorate = tk2.nextToken("\u0003");
      final String[] refNames = parseRefNames(decorate);
      final List<String> tags = refNames.length > 0 ? new LinkedList<String>() : Collections.<String>emptyList();
      final List<String> branches = refNames.length > 0 ? new LinkedList<String>() : Collections.<String>emptyList();
      for (String refName : refNames) {
        if (allBranchesSet.contains(refName)) {
        // also some gits can return ref name twice (like (HEAD, HEAD), so check we will show it only once)
          if (! branches.contains(refName)) {
            branches.add(refName);
          }
        }
        else {
          if (! tags.contains(refName)) {
            tags.add(refName);
          }
        }
      }

      final String message = tk2.nextToken("\u0003").trim();

      final List<FilePath> pathsList = new LinkedList<FilePath>();
      if (tk2.hasMoreTokens()) {
        final String paths = tk2.nextToken();
        StringTokenizer tkPaths = new StringTokenizer(paths, "\n", false);
        while (tkPaths.hasMoreTokens()) {
          final String subPath = GitUtil.unescapePath(tkPaths.nextToken());
          final FilePath revisionPath = VcsUtil.getFilePathForDeletedFile(prefix + subPath, false);
          pathsList.add(revisionPath);
        }
      }
      // todo parse revisions... patches?
      rc.add(new GitCommit(shortHash, new SHAHash(hash), authorName, committerName, date, message, parentsHashes, pathsList, authorEmail,
                           committerEmail, tags, branches));
      //}
    }
    return rc;
  }

  public static List<CommitHashPlusParents> hashesWithParents(Project project, FilePath path, final String... parameters) throws VcsException {
    final CollectConsumer<CommitHashPlusParents> consumer = new CollectConsumer<CommitHashPlusParents>();
    hashesWithParents(project, path, consumer, parameters);
    return (List<CommitHashPlusParents>) consumer.getResult();
  }

  public static void hashesWithParents(Project project, FilePath path, final Consumer<CommitHashPlusParents> consumer, final String... parameters) throws VcsException {
    // adjust path using change manager
    path = getLastCommitName(project, path);
    final VirtualFile root = GitUtil.getGitRoot(path);
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    h.setNoSSH(true);
    h.setStdoutSuppressed(true);
    h.addParameters(parameters);
    h.addParameters("--name-only", "--pretty=format:%x01%h%x03%ct%x03%p", "--encoding=UTF-8");

    h.endOptions();
    h.addRelativePaths(path);
    String output = h.run();
    final List<CommitHashPlusParents> rc = new ArrayList<CommitHashPlusParents>();
    StringTokenizer tk = new StringTokenizer(output, "\u0001", false);

    while (tk.hasMoreTokens()) {
      final String line = tk.nextToken();
      final String[] subLines = line.split("\n");
      final StringTokenizer tk2 = new StringTokenizer(subLines[0], "\u0003", false);

      final String hash = tk2.nextToken();
      final long time;
      if (tk2.hasMoreTokens()) {
        final String dateString = tk2.nextToken();
        time = Long.parseLong(dateString.trim());
      } else {
        time = 0;
      }
      final String[] parents;
      if (tk2.hasMoreTokens()) {
        parents = tk2.nextToken().split(" ");
      } else {
        parents = ArrayUtil.EMPTY_STRING_ARRAY;
      }
      consumer.consume(new CommitHashPlusParents(hash, parents, time));
    }
  }

  @Nullable
  private static String removeSquareBraces(final String s) {
    final int startSquare = s.indexOf("[");
    final int endSquare = s.indexOf("]");
    if ((startSquare == -1) || (endSquare == -1)) return null;
    return s.substring(startSquare + 1, endSquare);
  }

  private static String[] parseRefNames(final String decorate) {
    if (removeSquareBraces(decorate) == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    /*final int startSquare = decorate.indexOf("[");
    final int endSquare = decorate.indexOf("]");
    if ((startSquare == -1) || (endSquare == -1)) return ArrayUtil.EMPTY_STRING_ARRAY;*/

    final int startParentheses = decorate.indexOf("(");
    final int endParentheses = decorate.indexOf(")");
    if ((startParentheses == -1) || (endParentheses == -1)) return ArrayUtil.EMPTY_STRING_ARRAY;

    final String refs = decorate.substring(startParentheses + 1, endParentheses);
    return refs.split(", ");
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
