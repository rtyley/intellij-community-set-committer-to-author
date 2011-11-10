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
package com.intellij.openapi.command.impl;

import com.intellij.CommonBundle;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class UndoableGroup {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.command.impl.UndoableGroup");

  private final String myCommandName;
  private final boolean myGlobal;
  private final int myCommandTimestamp;
  private final boolean myTransparent;
  private final List<UndoableAction> myActions;
  private EditorAndState myStateBefore;
  private EditorAndState myStateAfter;
  private final Project myProject;
  private final UndoConfirmationPolicy myConfirmationPolicy;

  private boolean myValid;
  private boolean myMerged4Undo;
  private boolean myMerged4Redo;

  public UndoableGroup(String commandName,
                       boolean isGlobal,
                       Project project,
                       EditorAndState stateBefore,
                       EditorAndState stateAfter,
                       List<UndoableAction> actions,
                       int commandTimestamp,
                       UndoConfirmationPolicy confirmationPolicy,
                       boolean transparent,
                       boolean valid) {
    myCommandName = commandName;
    myGlobal = isGlobal;
    myCommandTimestamp = commandTimestamp;
    myActions = actions;
    myProject = project;
    myStateBefore = stateBefore;
    myStateAfter = stateAfter;
    myConfirmationPolicy = confirmationPolicy;
    myTransparent = transparent;
    myValid = valid;
    for (UndoableAction action : actions) {
      if (action instanceof FinishMarkAction) {
        ((FinishMarkAction)action).setGlobal(isGlobal);
        ((FinishMarkAction)action).setCommandName(commandName);
        myMerged4Undo = true;
        break;
      }
    }
  }

  public boolean isGlobal() {
    return myGlobal;
  }

  public boolean isTransparent() {
    return myTransparent;
  }

  public boolean isUndoable() {
    for (UndoableAction action : myActions) {
      if (action instanceof NonUndoableAction) return false;
    }
    return true;
  }

  public void undo() {
    undoOrRedo(true);
  }

  public void redo() {
    undoOrRedo(false);
  }

  public boolean containsAction(UndoableAction action) {
    return myActions.contains(action);
  }

  private void undoOrRedo(boolean isUndo) {
    LocalHistoryAction action;
    if (myProject != null && isGlobal()) {
      String actionName = CommonBundle.message(isUndo ? "local.vcs.action.name.undo.command" : "local.vcs.action.name.redo.command", myCommandName);
      action = LocalHistory.getInstance().startAction(actionName);
    }
    else {
      action = LocalHistoryAction.NULL;
    }

    try {
      doUndoOrRedo(isUndo);
    }
    finally {
      action.finish();
    }
  }

  private void doUndoOrRedo(final boolean isUndo) {
    final boolean wrapInBulkUpdate = myActions.size() > 50;
    // perform undo action by action, setting bulk update flag if possible
    // if multiple consecutive actions share a document, then set the bulk flag only once
    final Set<DocumentEx> bulkDocuments = new THashSet<DocumentEx>();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          for (final UndoableAction action : isUndo ? ContainerUtil.iterateBackward(myActions) : myActions) {
            final Collection<DocumentEx> newDocuments;
            if (wrapInBulkUpdate) {
              newDocuments = new THashSet<DocumentEx>();
              Set<DocumentEx> documentsToRemoveFromBulk = new THashSet<DocumentEx>(bulkDocuments);
              DocumentReference[] affectedDocuments = action.getAffectedDocuments();
              if (affectedDocuments != null) {
                for (DocumentReference affectedDocument : affectedDocuments) {
                  VirtualFile file = affectedDocument.getFile();
                  if (file != null && !file.isValid()) continue;
                  DocumentEx document = (DocumentEx)affectedDocument.getDocument();
                  if (document == null) continue;
                  documentsToRemoveFromBulk.remove(document);
                  if (bulkDocuments.contains(document)) continue;
                  newDocuments.add(document);
                  document.setInBulkUpdate(true);
                }
              }
              for (DocumentEx document : documentsToRemoveFromBulk) {
                document.setInBulkUpdate(false);
              }
              bulkDocuments.removeAll(documentsToRemoveFromBulk);
              bulkDocuments.addAll(newDocuments);
            }
            else {
              newDocuments = Collections.emptyList();
            }

            if (isUndo) {
              action.undo();
            }
            else {
              action.redo();
            }
          }
          for (DocumentEx bulkDocument : bulkDocuments) {
            bulkDocument.setInBulkUpdate(false);
          }
        }
        catch (UnexpectedUndoException e) {
          reportUndoProblem(e, isUndo);
        }
      }
    });
    commitAllDocuments();
  }

  public void setMerged4Undo() {
    myMerged4Undo = true;
  }

  public boolean isMerged4Undo() {
    return myMerged4Undo;
  }

  public boolean isMerged4Redo() {
    return myMerged4Redo;
  }

  public void setMerged4Redo() {
    myMerged4Redo = true;
  }

  private static void commitAllDocuments() {
    for (Project p : ProjectManager.getInstance().getOpenProjects()) {
      PsiDocumentManager.getInstance(p).commitAllDocuments();
    }
  }

  private void reportUndoProblem(UnexpectedUndoException e, boolean isUndo) {
    String title;
    String message;

    if (isUndo) {
      title = CommonBundle.message("cannot.undo.dialog.title");
      message = CommonBundle.message("cannot.undo.message");
    }
    else {
      title = CommonBundle.message("cannot.redo.dialog.title");
      message = CommonBundle.message("cannot.redo.message");
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      if (e.getMessage() != null) {
        message += ".\n" + e.getMessage();
      }
      Messages.showMessageDialog(myProject, message, title, Messages.getErrorIcon());
    }
    else {
      LOG.error(e);
    }
  }

  public Collection<DocumentReference> getAffectedDocuments() {
    Set<DocumentReference> result = new THashSet<DocumentReference>();
    for (UndoableAction action : myActions) {
      DocumentReference[] refs = action.getAffectedDocuments();
      if (refs != null) Collections.addAll(result, refs);
    }
    return result;
  }

  public EditorAndState getStateBefore() {
    return myStateBefore;
  }

  public EditorAndState getStateAfter() {
    return myStateAfter;
  }

  public void setStateBefore(EditorAndState stateBefore) {
    myStateBefore = stateBefore;
  }

  public void setStateAfter(EditorAndState stateAfter) {
    myStateAfter = stateAfter;
  }

  public String getCommandName() {
    for (UndoableAction action : myActions) {
      if (action instanceof StartMarkAction) {
        String commandName = ((StartMarkAction)action).getCommandName();
        if (commandName != null) return commandName;
      }
    }
    return myCommandName;
  }

  public int getCommandTimestamp() {
    return myCommandTimestamp;
  }

  public boolean shouldAskConfirmation(boolean redo) {
    if (redo) {
      if (myMerged4Redo) {
        for (UndoableAction action : myActions) {
          if (action instanceof StartMarkAction) {
            if (action.isGlobal()) return true;
            break;
          }
        }
      }

      if (myMerged4Undo) {
        for (UndoableAction action : myActions) {
          if (action instanceof FinishMarkAction) {
            return false;
          }
        }
      }
    }
    return myConfirmationPolicy == UndoConfirmationPolicy.REQUEST_CONFIRMATION ||
           myConfirmationPolicy != UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION && myGlobal;
  }

  public void invalidateActionsFor(DocumentReference ref) {
    if (getAffectedDocuments().contains(ref)) {
      myValid = false;
    }
  }

  public boolean isValid() {
    return myValid;
  }

  public String toString() {
    StringBuilder result = new StringBuilder("UndoableGroup[");
    final boolean multiline = myActions.size() > 1;

    if (multiline) result.append("\n");

    result.append(StringUtil.join(myActions, new Function<UndoableAction, String>() {
      @Override
      public String fun(UndoableAction each) {
        return (multiline ? "  " : "") + each.toString();
      }
    }, ",\n"));

    if (multiline) result.append("\n");
    result.append("]");
    return result.toString();
  }
}
