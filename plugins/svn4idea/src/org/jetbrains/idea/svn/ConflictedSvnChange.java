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
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import org.tmatesoft.svn.core.internal.wc.SVNTreeConflictUtil;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;

import javax.swing.*;

public class ConflictedSvnChange extends Change {
  private final ConflictState myConflictState;
  // also used if not move/rename
  private SVNTreeConflictDescription myBeforeDescription;
  private SVNTreeConflictDescription myAfterDescription;
  // +-
  private final FilePath myTreeConflictMarkHolder;
  private boolean myIsPhantom;

  public ConflictedSvnChange(ContentRevision beforeRevision, ContentRevision afterRevision, final ConflictState conflictState,
                             final FilePath treeConflictMarkHolder) {
    super(beforeRevision, afterRevision);
    myConflictState = conflictState;
    myTreeConflictMarkHolder = treeConflictMarkHolder;
  }

  public ConflictedSvnChange(ContentRevision beforeRevision, ContentRevision afterRevision, FileStatus fileStatus,
                             final ConflictState conflictState,
                             final FilePath treeConflictMarkHolder) {
    super(beforeRevision, afterRevision, fileStatus);
    myConflictState = conflictState;
    myTreeConflictMarkHolder = treeConflictMarkHolder;
  }

  public ConflictState getConflictState() {
    return myConflictState;
  }

  public void setIsPhantom(boolean isPhantom) {
    myIsPhantom = isPhantom;
  }

  @Override
  public boolean isTreeConflict() {
    return myConflictState.isTree();
  }

  @Override
  public boolean isPhantom() {
    return myIsPhantom;
  }

  public SVNTreeConflictDescription getBeforeDescription() {
    return myBeforeDescription;
  }

  public void setBeforeDescription(SVNTreeConflictDescription beforeDescription) {
    myBeforeDescription = beforeDescription;
  }

  public SVNTreeConflictDescription getAfterDescription() {
    return myAfterDescription;
  }

  public void setAfterDescription(SVNTreeConflictDescription afterDescription) {
    myAfterDescription = afterDescription;
  }

  @Override
  public Icon getAdditionalIcon() {
    return myConflictState.getIcon();
  }

  @Override
  public String getDescription() {
    final String description = myConflictState.getDescription();
    if (description != null) {
      final StringBuilder sb = new StringBuilder(SvnBundle.message("svn.changeview.item.in.conflict.text", description));
      if (myBeforeDescription != null) {
        sb.append('\n');
        if (myAfterDescription != null) {
          sb.append("before: ");
        }
        sb.append(SVNTreeConflictUtil.getHumanReadableConflictDescription(myBeforeDescription));
      }
      if (myAfterDescription != null) {
        sb.append('\n');
        if (myBeforeDescription != null) {
          sb.append("after: ");
        }
        sb.append(SVNTreeConflictUtil.getHumanReadableConflictDescription(myAfterDescription));
      }
      return sb.toString();
    }
    return description;
  }

  public FilePath getTreeConflictMarkHolder() {
    return myTreeConflictMarkHolder;
  }
}
