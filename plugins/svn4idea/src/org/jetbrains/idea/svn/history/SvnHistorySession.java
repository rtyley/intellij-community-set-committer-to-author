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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.util.List;

/**
* Created with IntelliJ IDEA.
* User: Irina.Chernushina
* Date: 4/27/12
* Time: 12:24 PM
* To change this template use File | Settings | File Templates.
*/
public class SvnHistorySession extends VcsAbstractHistorySession {
  private final SvnVcs myVcs;
  private final FilePath myCommittedPath;
  private final boolean mySupports15;

  public SvnHistorySession(SvnVcs vcs, final List<VcsFileRevision> revisions, final FilePath committedPath, final boolean supports15,
                    @Nullable final VcsRevisionNumber currentRevision, boolean skipRefreshOnStart) {
    super(revisions, currentRevision);
    myVcs = vcs;
    myCommittedPath = committedPath;
    mySupports15 = supports15;
    if (!skipRefreshOnStart) {
      shouldBeRefreshed();
    }
  }

  public HistoryAsTreeProvider getHistoryAsTreeProvider() {
    return null;
  }

  @Nullable
  public VcsRevisionNumber calcCurrentRevisionNumber() {
    if (myCommittedPath == null) {
      return null;
    }
    if (myCommittedPath.isNonLocal()) {
      // technically, it does not make sense, since there's no "current" revision for non-local history (if look how it's used)
      // but ok, lets keep it for now
      return new SvnRevisionNumber(SVNRevision.HEAD);
    }
    return getCurrentCommittedRevision(myVcs, new File(myCommittedPath.getPath()));
  }

  public static VcsRevisionNumber getCurrentCommittedRevision(final SvnVcs vcs, final File file) {
    try {
      SVNWCClient wcClient = vcs.createWCClient();
      SVNInfo info = wcClient.doInfo(file, SVNRevision.UNDEFINED);
      if (info != null) {
        return new SvnRevisionNumber(info.getCommittedRevision());
      }
      else {
        return null;
      }
    }
    catch (SVNException e) {
      return null;
    }
  }

  public FilePath getCommittedPath() {
    return myCommittedPath;
  }

  @Override
  public boolean isContentAvailable(final VcsFileRevision revision) {
    return !myCommittedPath.isDirectory();
  }

  public boolean isSupports15() {
    return mySupports15;
  }

  @Override
  public VcsHistorySession copy() {
    return new SvnHistorySession(myVcs, getRevisionList(), myCommittedPath, mySupports15, getCurrentRevisionNumber(), true);
  }
}
