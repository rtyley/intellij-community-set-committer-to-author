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
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CommandMerger {
  private final UndoManagerImpl myManager;
  private Object myLastGroupId = null;
  private boolean myGlobal = false;
  private boolean myHasUndoTransparentsOnly = true;
  private boolean myHasUndoTransparents = false;
  private String myCommandName = null;
  private List<UndoableAction> myCurrentActions = new ArrayList<UndoableAction>();
  private Set<DocumentReference> myAffectedDocuments = new THashSet<DocumentReference>();
  private EditorAndState myStateBefore;
  private EditorAndState myStateAfter;
  private UndoConfirmationPolicy myUndoConfirmationPolicy = UndoConfirmationPolicy.DEFAULT;

  public CommandMerger(@NotNull UndoManagerImpl manager) {
    myManager = manager;
  }

  public String getCommandName() {
    return myCommandName;
  }

  public void addAction(UndoableAction action, boolean isUndoTransparent) {
    if (!isUndoTransparent) myHasUndoTransparentsOnly = false;
    if (isUndoTransparent) myHasUndoTransparents = true;
    myCurrentActions.add(action);
    DocumentReference[] refs = action.getAffectedDocuments();
    if (refs != null) {
      Collections.addAll(myAffectedDocuments, refs);
    }
    myGlobal |= action.isGlobal() || !isUndoTransparent && affectsMultiplePhysicalDocs();
  }

  public void commandFinished(String commandName, Object groupId, CommandMerger nextCommandToMerge) {
    if (!shouldMerge(groupId, nextCommandToMerge)) {
      flushCurrentCommand();
      myManager.compact();
    }
    merge(nextCommandToMerge);

    // we do not want to spoil redo stack in situation, when some 'transparent' actions occurred right after undo.
    if (!nextCommandToMerge.isTransparent()) clearRedoStacks(nextCommandToMerge);

    myLastGroupId = groupId;
    if (myCommandName == null) myCommandName = commandName;
  }

  private boolean shouldMerge(Object groupId, CommandMerger nextCommandToMerge) {
    if (isTransparent() || nextCommandToMerge.isTransparent()) {
      return myAffectedDocuments.equals(nextCommandToMerge.myAffectedDocuments);
    }
    return !myGlobal && !nextCommandToMerge.isGlobal() && canMergeGroup(groupId, myLastGroupId);
  }

  public static boolean canMergeGroup(Object groupId, Object lastGroupId) {
    return groupId != null && Comparing.equal(lastGroupId, groupId);
  }

  private void merge(CommandMerger nextCommandToMerge) {
    setBeforeState(nextCommandToMerge.myStateBefore);
    myCurrentActions.addAll(nextCommandToMerge.myCurrentActions);
    myAffectedDocuments.addAll(nextCommandToMerge.myAffectedDocuments);
    myGlobal |= nextCommandToMerge.myGlobal;
    myHasUndoTransparentsOnly &= nextCommandToMerge.myHasUndoTransparentsOnly;
    myHasUndoTransparents |= nextCommandToMerge.myHasUndoTransparents;
    myStateAfter = nextCommandToMerge.myStateAfter;
    mergeUndoConfirmationPolicy(nextCommandToMerge.getUndoConfirmationPolicy());
  }

  public void flushCurrentCommand() {
    if (hasActions()) {
      // make sure group is global if was merged from several different changes.
      myGlobal |= !isTransparent() && affectsMultiplePhysicalDocs();
      UndoableGroup undoableGroup = new UndoableGroup(myCommandName,
                                                      myGlobal,
                                                      myManager.getProject(),
                                                      myStateBefore,
                                                      myStateAfter,
                                                      myCurrentActions,
                                                      myManager.nextCommandTimestamp(),
                                                      myUndoConfirmationPolicy,
                                                      isTransparent());
      myManager.getUndoStacksHolder().addToStacks(undoableGroup);
    }

    reset();
  }

  private void reset() {
    myCurrentActions = new ArrayList<UndoableAction>();
    myAffectedDocuments = new THashSet<DocumentReference>();
    myLastGroupId = null;
    myGlobal = false;
    myHasUndoTransparentsOnly = true;
    myHasUndoTransparents = false;
    myCommandName = null;
    myStateAfter = null;
    myStateBefore = null;
    myUndoConfirmationPolicy = UndoConfirmationPolicy.DEFAULT;
  }

  private void clearRedoStacks(CommandMerger nextMerger) {
    myManager.getRedoStacksHolder().clearStacks(myGlobal, nextMerger.myAffectedDocuments);
  }

  boolean isGlobal() {
    return myGlobal;
  }

  public void markAsGlobal() {
    myGlobal = true;
  }

  private boolean isTransparent() {
    return myHasUndoTransparentsOnly && myHasUndoTransparents;
  }

  private boolean affectsMultiplePhysicalDocs() {
    int count = 0;
    for (DocumentReference each : myAffectedDocuments) {
      VirtualFile file = each.getFile();
      if (file instanceof LightVirtualFile) continue;
      if (++count > 1) return true;
    }
    return false;
  }

  public void undoOrRedo(FileEditor editor, boolean isUndo) {
    flushCurrentCommand();

    // here we _undo_ (regardless 'isUndo' flag) and drop all 'transparent' actions made right after undoRedo/redo.
    // Such actions should not get into redo/undoRedo stacks.  Note that 'transparent' actions that have been merged with normal actions
    // are not dropped, since this means they did not occur after undo/redo
    UndoRedo undoRedo;
    while ((undoRedo = createUndoOrRedo(editor, true)) != null) {
      if (!undoRedo.isTransparentsOnly()) break;
      undoRedo.execute(true);
      if (!undoRedo.hasMoreActions()) break;
    }

    while ((undoRedo = createUndoOrRedo(editor, isUndo)) != null) {
      undoRedo.execute(false);
      boolean shouldRepeat = undoRedo.isTransparentsOnly() && undoRedo.hasMoreActions();
      if (!shouldRepeat) break;
    }
  }

  @Nullable
  private UndoRedo createUndoOrRedo(FileEditor editor, boolean isUndo) {
    if (!myManager.isUndoOrRedoAvailable(editor, isUndo)) return null;
    return isUndo ? new Undo(myManager, editor) : new Redo(myManager, editor);
  }

  public UndoConfirmationPolicy getUndoConfirmationPolicy() {
    return myUndoConfirmationPolicy;
  }

  public boolean hasActions() {
    return !myCurrentActions.isEmpty();
  }

  public Collection<DocumentReference> getAffectedDocuments() {
    return myAffectedDocuments;
  }

  public boolean isUndoAvailable(Collection<DocumentReference> refs) {
    if (hasNonUndoableActions()) return false;
    if (refs.isEmpty()) return myGlobal && hasActions();
    for (DocumentReference each : refs) {
      if (hasChangesOf(each)) return true;
    }
    return false;
  }

  private boolean hasNonUndoableActions() {
    for (UndoableAction each : myCurrentActions) {
      if (each instanceof NonUndoableAction) return true;
    }
    return false;
  }

  private boolean hasChangesOf(DocumentReference ref) {
    for (UndoableAction action : myCurrentActions) {
      DocumentReference[] refs = action.getAffectedDocuments();
      if (refs == null || ArrayUtil.contains(ref, refs)) return true;
    }
    return false;
  }

  public void setBeforeState(EditorAndState state) {
    if (myStateBefore == null || !hasActions()) {
      myStateBefore = state;
    }
  }

  public void setAfterState(EditorAndState state) {
    myStateAfter = state;
  }

  public void mergeUndoConfirmationPolicy(UndoConfirmationPolicy undoConfirmationPolicy) {
    if (myUndoConfirmationPolicy == UndoConfirmationPolicy.DEFAULT) {
      myUndoConfirmationPolicy = undoConfirmationPolicy;
    }
    else if (myUndoConfirmationPolicy == UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION) {
      if (undoConfirmationPolicy == UndoConfirmationPolicy.REQUEST_CONFIRMATION) {
        myUndoConfirmationPolicy = UndoConfirmationPolicy.REQUEST_CONFIRMATION;
      }
    }
  }
}
