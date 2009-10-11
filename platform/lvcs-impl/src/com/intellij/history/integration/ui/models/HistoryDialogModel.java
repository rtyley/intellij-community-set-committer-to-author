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

package com.intellij.history.integration.ui.models;

import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.patches.PatchCreator;
import com.intellij.history.integration.revertion.ChangeReverter;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class HistoryDialogModel {
  protected LocalVcs myVcs;
  protected VirtualFile myFile;
  protected IdeaGateway myGateway;
  private List<Revision> myRevisionsCache;
  private boolean myShowChangesOnly;
  private int myRightRevisionIndex;
  private int myLeftRevisionIndex;
  private boolean myIsChangesSelected = false;

  public HistoryDialogModel(IdeaGateway gw, LocalVcs vcs, VirtualFile f) {
    myVcs = vcs;
    myFile = f;
    myGateway = gw;
  }

  public List<Revision> getRevisions() {
    if (myRevisionsCache == null) initRevisionsCache();
    return myRevisionsCache;
  }

  private void initRevisionsCache() {
    myGateway.registerUnsavedDocuments(myVcs);
    myRevisionsCache = getRevisionsCache();
  }

  protected List<Revision> getRevisionsCache() {
    List<Revision> all = myVcs.getRevisionsFor(myFile.getPath());
    if (!myShowChangesOnly) return all;

    List<Revision> result = new ArrayList<Revision>();
    for (Revision r : all) {
      if (r.isImportant()) result.add(r);
    }

    if (result.isEmpty()) result.add(all.get(0));

    return result;
  }

  protected Revision getLeftRevision() {
    return getRevisions().get(myLeftRevisionIndex);
  }

  protected Revision getRightRevision() {
    return getRevisions().get(myRightRevisionIndex);
  }

  protected Entry getLeftEntry() {
    return getLeftRevision().getEntry();
  }

  protected Entry getRightEntry() {
    return getRightRevision().getEntry();
  }

  public void selectRevisions(int first, int second) {
    doSelect(first, second);
    myIsChangesSelected = false;
  }

  public void selectChanges(int first, int second) {
    doSelect(first, second + 1);
    myIsChangesSelected = true;
  }

  private void doSelect(int first, int second) {
    if (first == second) {
      myRightRevisionIndex = 0;
      myLeftRevisionIndex = first == -1 ? 0 : first;
    }
    else {
      myRightRevisionIndex = first;
      myLeftRevisionIndex = second;
    }
  }

  public void showChangesOnly(boolean value) {
    myShowChangesOnly = value;
    initRevisionsCache();
    resetSelection();
  }

  protected void resetSelection() {
    selectRevisions(0, 0);
  }

  public boolean doesShowChangesOnly() {
    return myShowChangesOnly;
  }

  protected boolean isCurrentRevisionSelected() {
    return myRightRevisionIndex == 0;
  }

  public List<Change> getChanges() {
    List<Difference> dd = getLeftRevision().getDifferencesWith(getRightRevision());

    List<Change> result = new ArrayList<Change>();
    for (Difference d : dd) {
      result.add(createChange(d));
    }

    return result;
  }

  protected Change createChange(Difference d) {
    return new Change(d.getLeftContentRevision(myGateway), d.getRightContentRevision(myGateway));
  }

  public void createPatch(String path, boolean isReverse) throws VcsException, IOException {
    PatchCreator.create(myGateway, getChanges(), path, isReverse);
  }

  public Reverter createReverter() {
    if (myIsChangesSelected) return createChangeReverter();
    return createRevisionReverter();
  }

  protected Reverter createChangeReverter() {
    return new ChangeReverter(myVcs, myGateway, getRightRevision().getCauseChange());
  }

  protected abstract Reverter createRevisionReverter();

  public boolean isRevertEnabled() {
    if (myIsChangesSelected) return isCorrectChangeSelection();
    return isCurrentRevisionSelected() && myLeftRevisionIndex > 0;
  }

  public boolean isCreatePatchEnabled() {
    return isCorrectSelectionForPatchCreation();
  }

  private boolean isCorrectSelectionForPatchCreation() {
    if (myIsChangesSelected) return isCorrectChangeSelection();
    return myLeftRevisionIndex > 0;
  }

  private boolean isCorrectChangeSelection() {
    return myLeftRevisionIndex - myRightRevisionIndex == 1;
  }

  public boolean canPerformCreatePatch() {
    return !getLeftEntry().hasUnavailableContent() && !getRightEntry().hasUnavailableContent();
  }
}
