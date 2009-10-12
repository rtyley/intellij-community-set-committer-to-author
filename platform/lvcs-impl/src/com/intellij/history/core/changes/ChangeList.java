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

package com.intellij.history.core.changes;

import com.intellij.history.Clock;
import com.intellij.history.core.IdPath;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.utils.LocalHistoryLog;
import com.intellij.history.utils.Reversed;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class ChangeList {
  private List<Change> myChanges = new ArrayList<Change>();
  private ChangeSet myCurrentChangeSet;
  private int myChangeSetDepth;

  public ChangeList() {
  }

  public ChangeList(Stream s) throws IOException {
    int count = s.readInteger();
    while (count-- > 0) {
      myChanges.add(s.readChange());
    }
  }

  public void write(Stream s) throws IOException {
    s.writeInteger(myChanges.size());
    for (Change c : myChanges) {
      s.writeChange(c);
    }
  }

  public void addChange(Change c) {
    if (myChangeSetDepth == 0) {
      myChanges.add(c);
    }
    else {
      myCurrentChangeSet.addChange(c);
    }
  }

  public void beginChangeSet() {
    myChangeSetDepth++;
    if (myChangeSetDepth == 1) {
      myCurrentChangeSet = new ChangeSet(Clock.getCurrentTimestamp());
      myChanges.add(myCurrentChangeSet);
    }
  }

  public void endChangeSet(String name) {
    if (myChangeSetDepth <= 0) {
      LocalHistoryLog.LOG.warn("Depth is invalid: " + myChangeSetDepth + "\n" +
                               "ChangeSet's Name: " + name + "\n" +
                               "Current ChangeSet: " + (myCurrentChangeSet == null ? null : myCurrentChangeSet.getChanges().size()));
      myChangeSetDepth = 0;
      return;
    }

    myChangeSetDepth--;
    if (myChangeSetDepth == 0) {
      if (myCurrentChangeSet.getChanges().isEmpty()) {
        myChanges.remove(myChanges.size() - 1);
      }
      else {
        myCurrentChangeSet.setName(name);
      }
      myCurrentChangeSet = null;
    }
  }

  public List<Change> getChanges() {
    List<Change> result = new ArrayList<Change>(myChanges);
    Collections.reverse(result);
    return result;
  }

  public boolean isBefore(Change before, Change after, boolean canBeEqual) {
    int beforeIndex = myChanges.indexOf(before);
    int afterIndex = myChanges.indexOf(after);

    return beforeIndex < afterIndex || (canBeEqual && beforeIndex == afterIndex);
  }

  public List<Change> getChain(Change initialChange) {
    List<Change> result = new ArrayList<Change>();
    for (Change c : myChanges) {
      if (c == initialChange) {
        result.add(c);
        continue;
      }
      if (c.affectsSameAs(result)) result.add(c);
    }
    return result;
  }

  public AcceptFun getAcceptFunc(Entry root, ChangeVisitor v, boolean copyChangeList) throws IOException {
    return new AcceptFun(v, root.copy(), copyChangeList ? new ArrayList<Change>(myChanges) : myChanges);
  }

  public static class AcceptFun {
    private final ChangeVisitor myVisitor;
    private final Entry myRoot;
    private final List<Change> myChanges;

    public AcceptFun(ChangeVisitor visitor, Entry root, List<Change> changes) {
      myVisitor = visitor;
      myRoot = root;
      myChanges = changes;
    }

    public void doAccept() throws IOException {
      myVisitor.started(myRoot);
      try {
        for (Change change : Reversed.list(myChanges)) {
          change.accept(myVisitor);
        }
      }
      catch (ChangeVisitor.StopVisitingException e) {
      }
      myVisitor.finished();
    }
  }

  public List<Change> getChangesFor(Entry root, String path) {
    try {
      ChangeCollectingVisitor v = new ChangeCollectingVisitor(path);
      getAcceptFunc(root, v, false).doAccept();
      return v.getResult();
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public void revertUpTo(Entry r, Change target, boolean revertTargetChange) {
    for (Change c : Reversed.list(myChanges)) {
      if (!c.revertOnUpTo(r, target, revertTargetChange)) return;
    }
  }

  public List<Content> purgeObsolete(long period) {
    List<Change> newChanges = new ArrayList<Change>();
    List<Content> contentsToPurge = new ArrayList<Content>();

    int index = getIndexOfLastObsoleteChange(period);

    for (int i = index + 1; i < myChanges.size(); i++) {
      newChanges.add(myChanges.get(i));
    }

    for (int i = 0; i <= index; i++) {
      contentsToPurge.addAll(myChanges.get(i).getContentsToPurge());
    }

    myChanges = newChanges;
    return contentsToPurge;
  }

  private int getIndexOfLastObsoleteChange(long period) {
    long prevTimestamp = 0;
    long length = 0;

    for (int i = myChanges.size() - 1; i >= 0; i--) {
      Change c = myChanges.get(i);
      if (prevTimestamp == 0) prevTimestamp = c.getTimestamp();

      long delta = prevTimestamp - c.getTimestamp();
      prevTimestamp = c.getTimestamp();

      length += delta < getIntervalBetweenActivities() ? delta : 1;

      if (length >= period) return i;
    }

    return -1;
  }

  protected long getIntervalBetweenActivities() {
    return 12 * 60 * 60 * 1000;
  }

  private static class ChangeCollectingVisitor extends ChangeVisitor {
    private final String myPath;
    private Entry myEntry;
    private IdPath myIdPath;
    private Change myChangeToAdd;
    private boolean myExists = true;
    private boolean myDoNotAddAnythingElseFromCurrentChangeSet = false;
    private final List<Change> myResult = new ArrayList<Change>();

    public ChangeCollectingVisitor(String path) {
      myPath = path;
    }

    @Override
    public void started(Entry root) throws IOException {
      super.started(root);
      myEntry = myRoot.getEntry(myPath);
      myIdPath = myEntry.getIdPath();
    }

    public List<Change> getResult() {
      return new ArrayList<Change>(new LinkedHashSet<Change>(myResult));
    }

    @Override
    public void begin(ChangeSet c) {
      myChangeToAdd = c;
    }

    @Override
    public void end(ChangeSet c) {
      myChangeToAdd = null;
      myDoNotAddAnythingElseFromCurrentChangeSet = false;
    }

    @Override
    public void visit(PutLabelChange c) {
      if (myChangeToAdd == null) {
        myChangeToAdd = c;
        doVisit(c);
        myChangeToAdd = null;
      }
      else {
        doVisit(c);
      }
    }

    @Override
    public void visit(StructuralChange c) {
      doVisit(c);
    }

    private void doVisit(Change c) {
      if (skippedDueToNonexistence(c)) return;
      addIfAffectsAndRevert(c);

      myIdPath = myEntry.getIdPath();
    }

    @Override
    public void visit(CreateEntryChange c) {
      if (skippedDueToNonexistence(c)) return;
      addIfAffectsAndRevert(c);
      if (c.isCreationalFor(myIdPath)) myExists = false;
    }

    @Override
    public void visit(DeleteChange c) {
      if (skippedDueToNonexistence(c)) {
        if (c.isDeletionOf(myIdPath)) {
          Entry e = myRoot.findEntry(myIdPath);
          if (e != null) {
            myEntry = e;
            myExists = true;
            myDoNotAddAnythingElseFromCurrentChangeSet = true;
          }
        }
        return;
      }

      addIfAffectsAndRevert(c);
      myIdPath = myEntry.getIdPath();
    }

    private void addIfAffectsAndRevert(Change c) {
      if (!myDoNotAddAnythingElseFromCurrentChangeSet && c.affects(myIdPath)) {
        myResult.add(myChangeToAdd);
      }
      c.revertOn(myRoot);
    }

    private boolean skippedDueToNonexistence(Change c) {
      if (myExists) return false;

      c.revertOn(myRoot);
      return true;
    }
  }
}
