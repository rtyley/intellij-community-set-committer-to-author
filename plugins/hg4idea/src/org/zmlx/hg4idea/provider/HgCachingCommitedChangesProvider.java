// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.provider;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.DecoratorManager;
import com.intellij.openapi.vcs.changes.committed.VcsCommittedListsZipper;
import com.intellij.openapi.vcs.changes.committed.VcsCommittedViewAuxiliary;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.ChangesBrowserSettingsEditor;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.AsynchConsumer;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.*;
import org.zmlx.hg4idea.command.HgLogCommand;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class HgCachingCommitedChangesProvider implements CachingCommittedChangesProvider<CommittedChangeList, ChangeBrowserSettings> {

  private final Project project;
  private final HgVcs myVcs;

  public HgCachingCommitedChangesProvider(Project project, HgVcs vcs) {
    this.project = project;
    myVcs = vcs;
  }

  public int getFormatVersion() {
    return 0;
  }

  public CommittedChangeList readChangeList(RepositoryLocation repositoryLocation, DataInput dataInput) throws IOException {
    HgRevisionNumber revision = HgRevisionNumber.getInstance(dataInput.readUTF(), dataInput.readUTF());
    String committerName = dataInput.readUTF();
    String comment = dataInput.readUTF();
    Date commitDate = new Date(dataInput.readLong());
    int changesCount = dataInput.readInt();
    List<Change> changes = new ArrayList<Change>();
    for (int i = 0; i < changesCount; i++) {
      HgContentRevision beforeRevision = readRevision(repositoryLocation, dataInput);
      HgContentRevision afterRevision = readRevision(repositoryLocation, dataInput);
      changes.add(new Change(beforeRevision, afterRevision));
    }
    return new HgCommittedChangeList(myVcs, revision, comment, committerName, commitDate, changes);
  }

  public void writeChangeList(DataOutput dataOutput, CommittedChangeList committedChangeList) throws IOException {
    HgCommittedChangeList changeList = (HgCommittedChangeList)committedChangeList;
    writeRevisionNumber(dataOutput, changeList.getRevision());
    dataOutput.writeUTF(changeList.getCommitterName());
    dataOutput.writeUTF(changeList.getComment());
    dataOutput.writeLong(changeList.getCommitDate().getTime());
    dataOutput.writeInt(changeList.getChanges().size());
    for (Change change : changeList.getChanges()) {
      writeRevision(dataOutput, (HgContentRevision)change.getBeforeRevision());
      writeRevision(dataOutput, (HgContentRevision)change.getAfterRevision());
    }
  }

  private HgContentRevision readRevision(RepositoryLocation repositoryLocation, DataInput dataInput) throws IOException {
    String revisionPath = dataInput.readUTF();
    HgRevisionNumber revisionNumber = readRevisionNumber(dataInput);

    if (!StringUtil.isEmpty(revisionPath)) {
      VirtualFile root = ((HgRepositoryLocation)repositoryLocation).getRoot();
      return new HgContentRevision(project, new HgFile(root, new File(revisionPath)), revisionNumber);
    }
    else {
      return null;
    }
  }

  private void writeRevision(DataOutput dataOutput, HgContentRevision revision) throws IOException {
    if (revision == null) {
      dataOutput.writeUTF("");
      writeRevisionNumber(dataOutput, HgRevisionNumber.getInstance("", ""));
    }
    else {
      dataOutput.writeUTF(revision.getFile().getIOFile().toString());
      writeRevisionNumber(dataOutput, revision.getRevisionNumber());
    }
  }

  private HgRevisionNumber readRevisionNumber(DataInput dataInput) throws IOException {
    String revisionRevision = dataInput.readUTF();
    String revisionChangeset = dataInput.readUTF();
    return HgRevisionNumber.getInstance(revisionRevision, revisionChangeset);
  }

  private void writeRevisionNumber(DataOutput dataOutput, HgRevisionNumber revisionNumber) throws IOException {
    dataOutput.writeUTF(revisionNumber.getRevision());
    dataOutput.writeUTF(revisionNumber.getChangeset());
  }

  public boolean isMaxCountSupported() {
    return true;
  }

  public Collection<FilePath> getIncomingFiles(RepositoryLocation repositoryLocation) throws VcsException {
    return null;
  }

  public boolean refreshCacheByNumber() {
    return false;
  }

  @Nls
  public String getChangelistTitle() {
    return null;
  }

  public boolean isChangeLocallyAvailable(FilePath filePath,
                                          @Nullable VcsRevisionNumber localRevision,
                                          VcsRevisionNumber changeRevision,
                                          CommittedChangeList committedChangeList) {
    return localRevision != null && localRevision.compareTo(changeRevision) >= 0;
  }

  public boolean refreshIncomingWithCommitted() {
    return false;
  }

  public void loadCommittedChanges(ChangeBrowserSettings changeBrowserSettings,
                                   RepositoryLocation repositoryLocation,
                                   int i,
                                   AsynchConsumer<CommittedChangeList> committedChangeListAsynchConsumer) throws VcsException {
    throw new UnsupportedOperationException();  //TODO implement method
  }

  @NotNull
  public ChangeBrowserSettings createDefaultSettings() {
    return new ChangeBrowserSettings();
  }

  public ChangesBrowserSettingsEditor<ChangeBrowserSettings> createFilterUI(boolean b) {
    return null;
  }

  @Nullable
  public RepositoryLocation getLocationFor(FilePath filePath) {
    VirtualFile repo = VcsUtil.getVcsRootFor(project, filePath);
    if (repo == null) {
      return null;
    }
    return new HgRepositoryLocation(repo.getUrl(), repo);
  }

  public RepositoryLocation getLocationFor(FilePath root, String repositoryPath) {
    return getLocationFor(root);
  }

  @Nullable
  public VcsCommittedListsZipper getZipper() {
    return null;
  }

  public List<CommittedChangeList> getCommittedChanges(ChangeBrowserSettings changeBrowserSettings,
                                                       RepositoryLocation repositoryLocation,
                                                       int maxCount) throws VcsException {
    VirtualFile root = ((HgRepositoryLocation)repositoryLocation).getRoot();

    HgFile hgFile = new HgFile(root, VcsUtil.getFilePath(root.getPath()));

    List<CommittedChangeList> result = new LinkedList<CommittedChangeList>();
    HgLogCommand hgLogCommand = new HgLogCommand(project);
    hgLogCommand.setLogFile(false);
    List<String> args = new ArrayList<String>();
    args.add("--debug");
    List<HgFileRevision> localRevisions = hgLogCommand.execute(hgFile, maxCount == 0 ? -1 : maxCount, true, args); //can be zero

    Collections.reverse(localRevisions);

    for (HgFileRevision revision : localRevisions) {
      HgRevisionNumber vcsRevisionNumber = revision.getRevisionNumber();
      HgRevisionNumber firstParent = vcsRevisionNumber.getParents().get(0);
      List<Change> changes = new ArrayList<Change>();
      for (String file : revision.getModifiedFiles()) {
        changes.add(createChange(root, file, firstParent, file, vcsRevisionNumber, FileStatus.MODIFIED));
      }
      for (String file : revision.getAddedFiles()) {
        changes.add(createChange(root, null, null, file, vcsRevisionNumber, FileStatus.ADDED));
      }
      for (String file : revision.getDeletedFiles()) {
        changes.add(createChange(root, file, firstParent, null, vcsRevisionNumber, FileStatus.DELETED));
      }
      for (Map.Entry<String, String> copiedFile : revision.getCopiedFiles().entrySet()) {
        changes.add(createChange(root, copiedFile.getKey(), firstParent, copiedFile.getValue(), vcsRevisionNumber, FileStatus.ADDED));
      }

      result.add(new HgCommittedChangeList(myVcs, vcsRevisionNumber, revision.getCommitMessage(), revision.getAuthor(), revision.getRevisionDate(),
                                          changes));

    }
    Collections.reverse(result);
    return result;
  }

  private Change createChange(VirtualFile root,
                              @Nullable String fileBefore,
                              @Nullable HgRevisionNumber revisionBefore,
                              @Nullable String fileAfter,
                              HgRevisionNumber revisionAfter,
                              FileStatus aStatus) {

    HgContentRevision beforeRevision =
      fileBefore == null ? null : new HgContentRevision(project, new HgFile(root, new File(root.getPath(), fileBefore)), revisionBefore);
    HgContentRevision afterRevision =
      fileAfter == null ? null : new HgContentRevision(project, new HgFile(root, new File(root.getPath(), fileAfter)), revisionAfter);
    return new Change(beforeRevision, afterRevision, aStatus);
  }

  public ChangeListColumn[] getColumns() {
    return new ChangeListColumn[]{ChangeListColumn.NUMBER, ChangeListColumn.DATE, ChangeListColumn.DESCRIPTION, ChangeListColumn.NAME};
  }

  public VcsCommittedViewAuxiliary createActions(DecoratorManager decoratorManager, RepositoryLocation repositoryLocation) {
    return null;
  }

  public int getUnlimitedCountValue() {
    return -1;
  }

  @Override
  public Pair<CommittedChangeList, FilePath> getOneList(VirtualFile file, VcsRevisionNumber number) throws VcsException {
    final ChangeBrowserSettings settings = createDefaultSettings();
    settings.USE_CHANGE_AFTER_FILTER = true;
    settings.USE_CHANGE_BEFORE_FILTER = true;
    settings.CHANGE_AFTER = number.asString();
    settings.CHANGE_BEFORE = number.asString();
    // todo implement in proper way
    final FilePathImpl filePath = new FilePathImpl(HgUtil.convertToLocalVirtualFile(file));
    final List<CommittedChangeList> list = getCommittedChangesForRevision(getLocationFor(filePath), number.asString());
    if (list != null && list.size() == 1) {
      return new Pair<CommittedChangeList, FilePath>(list.get(0), filePath);
    }
    return null;
  }

  @Override
  public RepositoryLocation getForNonLocal(VirtualFile file) {
    return null;
  }

  @Override
  public boolean supportsIncomingChanges() {
    return false;
  }

  public List<CommittedChangeList> getCommittedChangesForRevision(@Nullable RepositoryLocation repositoryLocation, String revision) {
    if(repositoryLocation == null){
      return null;
    }
    VirtualFile root = ((HgRepositoryLocation)repositoryLocation).getRoot();
    HgFile hgFile = new HgFile(root, VcsUtil.getFilePath(root.getPath()));
    List<CommittedChangeList> result = new LinkedList<CommittedChangeList>();
    HgLogCommand hgLogCommand = new HgLogCommand(project);
    hgLogCommand.setLogFile(false);
    List<String> args = new ArrayList<String>();
    args.add("--debug");
    args.add("--rev");
    args.add(revision);
    HgFileRevision localRevision = hgLogCommand.execute(hgFile, 1, true, args).get(0);
    HgRevisionNumber vcsRevisionNumber = localRevision.getRevisionNumber();
    HgRevisionNumber firstParent = vcsRevisionNumber.getParents().get(0);
    List<Change> changes = new ArrayList<Change>();
    for (String file : localRevision.getModifiedFiles()) {
      changes.add(createChange(root, file, firstParent, file, vcsRevisionNumber, FileStatus.MODIFIED));
    }
    for (String file : localRevision.getAddedFiles()) {
      changes.add(createChange(root, null, null, file, vcsRevisionNumber, FileStatus.ADDED));
    }
    for (String file : localRevision.getDeletedFiles()) {
      changes.add(createChange(root, file, firstParent, null, vcsRevisionNumber, FileStatus.DELETED));
    }
    for (Map.Entry<String, String> copiedFile : localRevision.getCopiedFiles().entrySet()) {
      changes.add(createChange(root, copiedFile.getKey(), firstParent, copiedFile.getValue(), vcsRevisionNumber, FileStatus.ADDED));
    }

    result.add(new HgCommittedChangeList(myVcs, vcsRevisionNumber, localRevision.getCommitMessage(), localRevision.getAuthor(),
                                         localRevision.getRevisionDate(),
                                         changes));
    return result;
  }
}
