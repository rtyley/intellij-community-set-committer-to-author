/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.treeConflict;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
* Created with IntelliJ IDEA.
* User: Irina.Chernushina
* Date: 5/2/12
* Time: 1:03 PM
*/
public class SvnTreeConflictResolver {
  private final SvnVcs myVcs;
  private final FilePath myPath;
  private VcsRevisionNumber myCommittedRevision;
  private final FilePath myRevertPath;
  private final VcsDirtyScopeManager myDirtyScopeManager;

  public SvnTreeConflictResolver(SvnVcs vcs, FilePath path, VcsRevisionNumber committedRevision, final @Nullable FilePath revertPath) {
    myVcs = vcs;
    myPath = path;
    myCommittedRevision = committedRevision;
    myRevertPath = revertPath;
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myVcs.getProject());
  }

  public void resolveSelectTheirsFull(SVNTreeConflictDescription d) throws VcsException {
    updatetoTheirsFull();
    pathDirty(myPath);
    revertAdditional();
  }

  private void pathDirty(final FilePath path) {
    if (path.isDirectory()) {
      myDirtyScopeManager.dirDirtyRecursively(path);
    }
    else {
      myDirtyScopeManager.fileDirty(path);
    }
  }

  private void revertAdditional() throws VcsException {
    if (myRevertPath == null) return;
    final File ioFile = myRevertPath.getIOFile();
    SVNWCClient client = myVcs.createWCClient();
    try {
      client.doRevert(new File[]{ioFile}, SVNDepth.INFINITY, null);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
    pathDirty(myRevertPath);
  }

  public void resolveSelectMineFull(SVNTreeConflictDescription d) throws VcsException {
    SVNWCClient client = myVcs.createWCClient();
    try {
      client.doResolve(myPath.getIOFile(), SVNDepth.INFINITY, SVNConflictChoice.MERGED);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
    pathDirty(myPath);
    revertAdditional();
  }

  private void updatetoTheirsFull() throws VcsException {
    try {
      final File ioFile = myPath.getIOFile();
      SVNWCClient client = myVcs.createWCClient();
      SVNStatusClient statusClient = myVcs.createStatusClient();
      SVNStatus status = statusClient.doStatus(ioFile, false);
      if (myCommittedRevision == null) {
        myCommittedRevision = new SvnRevisionNumber(status.getCommittedRevision());
      }
      if (status == null || SVNStatusType.STATUS_UNVERSIONED.equals(status.getNodeStatus())) {
        client.doRevert(new File[]{ioFile}, SVNDepth.INFINITY, null);
        //updateIoFile(ioFile, SVNRevision.HEAD);
        return;
//        FileUtil.delete(ioFile);
      } else if (SVNStatusType.STATUS_ADDED.equals(status.getNodeStatus())) {
        client.doRevert(new File[]{ioFile}, SVNDepth.INFINITY, null);
        //updateIoFile(ioFile, SVNRevision.HEAD);
        /*client.doRevert(new File[]{ioFile}, SVNDepth.INFINITY, null);
        FileUtil.delete(ioFile);*/
        return;
      } else {
        final Set<File> usedToBeAdded = new HashSet<File>();
        if (myPath.isDirectory()) {
          statusClient.doStatus(ioFile, SVNRevision.UNDEFINED, SVNDepth.INFINITY, false, false, false, false,
                                new ISVNStatusHandler() {
                                  @Override
                                  public void handleStatus(SVNStatus status) throws SVNException {
                                    if (status != null && SVNStatusType.STATUS_ADDED.equals(status.getNodeStatus())) {
                                      usedToBeAdded.add(status.getFile());
                                    }
                                  }
                                }, null);
        }
        client.doRevert(new File[]{ioFile}, SVNDepth.INFINITY, null);
      }

      /*if (myPath.isDirectory()) {
        if (myCommittedRevision != null) {
          updateIoFile(ioFile, ((SvnRevisionNumber) myCommittedRevision).getRevision());
        }
        updateIoFile(ioFile, SVNRevision.HEAD);
      }*/
    }
    catch (SVNException e1) {
      throw new VcsException(e1);
    }
  }

  private void updateIoFile(File ioFile, final SVNRevision revision) throws SVNException {
    if (! ioFile.exists()) {
      myVcs.createUpdateClient().doUpdate(ioFile.getParentFile(), revision, SVNDepth.INFINITY, true, false);
    } else {
      myVcs.createUpdateClient().doUpdate(ioFile, revision, SVNDepth.INFINITY, false, false);
    }
  }
}
