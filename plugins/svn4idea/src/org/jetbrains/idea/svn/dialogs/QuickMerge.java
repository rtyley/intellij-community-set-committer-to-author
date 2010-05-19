/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.openapi.vcs.changes.committed.RunBackgroundable;
import com.intellij.openapi.vcs.changes.ui.ChangesViewBalloonProblemNotifier;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.AsynchConsumer;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.ChangeListsMergerFactory;
import org.jetbrains.idea.svn.history.CopyData;
import org.jetbrains.idea.svn.history.FirstInBranch;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;
import org.jetbrains.idea.svn.integrate.IMerger;
import org.jetbrains.idea.svn.integrate.MergerFactory;
import org.jetbrains.idea.svn.integrate.SvnIntegrateChangesTask;
import org.jetbrains.idea.svn.integrate.WorkingCopyInfo;
import org.jetbrains.idea.svn.mergeinfo.BranchInfo;
import org.jetbrains.idea.svn.mergeinfo.SvnMergeInfoCache;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class QuickMerge {
  private final Project myProject;
  private final String myBranchName;
  private final WCInfo myWcInfo;
  private final String mySourceUrl;
  private SvnVcs myVcs;

  public QuickMerge(Project project, String sourceUrl, WCInfo wcInfo, final String branchName) {
    myProject = project;
    myBranchName = branchName;
    myVcs = SvnVcs.getInstance(project);
    mySourceUrl = sourceUrl;
    myWcInfo = wcInfo;
  }
  
  @CalledInAwt
  public void execute() {
    if (mySourceUrl.equals(myWcInfo.getRootUrl())) {
      showErrorBalloon("Cannot merge from self");
      return;
    }

    if (! myWcInfo.getFormat().supportsMergeInfo()) {
      mergeAll();
      return;
    }

    final int result = Messages.showDialog(myProject, "Merge all?", "Merge from " + myBranchName,
                        new String[]{"Merge &all", "&Select revisions to merge", "Cancel"}, 0, Messages.getQuestionIcon());
    if (result == 2) return;
    if (result == 0) {
      mergeAll();
      return;
    }

    ProgressManager.getInstance().run(new MergeCalculator(myProject, myWcInfo, mySourceUrl, myBranchName));
  }

  @CalledInAny
  private void showErrorBalloon(final String s) {
    ChangesViewBalloonProblemNotifier.showMe(myProject, s, MessageType.ERROR);
  }

  // continuation... continuation.. hidden continuation..
  private void mergeAll() {
    // suppose we're in branch
    myVcs.getSvnBranchPointsCalculator().getFirstCopyPoint(myWcInfo.getRepositoryRoot(), mySourceUrl, myWcInfo.getRootUrl(),
      new Consumer<SvnBranchPointsCalculator.WrapperInvertor<SvnBranchPointsCalculator.BranchCopyData>>() {
        public void consume(SvnBranchPointsCalculator.WrapperInvertor<SvnBranchPointsCalculator.BranchCopyData> result) {
          if (result == null) {
            showErrorBalloon("Merge start wasn't found");
            return;
          }
          final boolean reintegrate = result.isInvertedSense();
          final MergerFactory mergerFactory = new MergerFactory() {
            public IMerger createMerger(SvnVcs vcs, File target, UpdateEventHandler handler, SVNURL currentBranchUrl) {
              return new BranchMerger(vcs, currentBranchUrl, myWcInfo.getUrl(), myWcInfo.getPath(), handler, reintegrate, myBranchName);
            }
          };

          final String title = "Merging all from " + myBranchName + (reintegrate ? " (reintegrate)" : "");
          doMerge(mergerFactory, title);
        }
      });
  }

  @CalledInAny
  private void doMerge(final MergerFactory factory, final String mergeTitle) {
    final SVNURL sourceUrlUrl;
    try {
      sourceUrlUrl = SVNURL.parseURIEncoded(mySourceUrl);
    } catch (SVNException e) {
      showErrorBalloon("Cannot merge: " + e.getMessage());
      return;
    }
    final SvnIntegrateChangesTask task = new SvnIntegrateChangesTask(SvnVcs.getInstance(myProject),
                                             new WorkingCopyInfo(myWcInfo.getPath(), true), factory, sourceUrlUrl, mergeTitle, false);
    RunBackgroundable.run(task);
  }

  private class MergeCalculator extends Task.Backgroundable {
    private final WCInfo myWcInfo;
    private final String mySourceUrl;
    private final String myBranchName;
    private boolean myIsReintegrate;

    private final List<CommittedChangeList> myNotMerged;
    private String myMergeTitle;
    private BranchInfo myBranchInfo;

    private MergeCalculator(Project project, WCInfo wcInfo, String sourceUrl, String branchName) {
      super(project, "Calculating not merged revisions", true, BackgroundFromStartOption.getInstance());
      myWcInfo = wcInfo;
      mySourceUrl = sourceUrl;
      myBranchName = branchName;
      myNotMerged = new LinkedList<CommittedChangeList>();
      myMergeTitle = "Merge from " + branchName;
    }

    public void run(@NotNull ProgressIndicator indicator) {
      // branch is info holder
      new FirstInBranch(myVcs, myWcInfo.getRepositoryRoot(), myWcInfo.getRootUrl(), mySourceUrl,
                        new Consumer<CopyData>() {
                          public void consume(CopyData copyData) {
          if (copyData == null) {
            showErrorBalloon("Merge start wasn't found");
            return;
          }

          myIsReintegrate = ! copyData.isTrunkSupposedCorrect();
          if (! myWcInfo.getFormat().supportsMergeInfo()) return;
          final long localLatest = Math.max(copyData.getCopyTargetRevision(), copyData.getCopySourceRevision());
          myBranchInfo = new BranchInfo(myVcs, myWcInfo.getRepositoryRoot(), myWcInfo.getRootUrl(), mySourceUrl, mySourceUrl, myVcs.createWCClient());

          final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider =
            myVcs.getCommittedChangesProvider();
          final ChangeBrowserSettings settings = new ChangeBrowserSettings();
          settings.CHANGE_AFTER = Long.toString(localLatest);
          try {
            committedChangesProvider.loadCommittedChanges(settings, new SvnRepositoryLocation(mySourceUrl),
                                          committedChangesProvider.getUnlimitedCountValue(), new AsynchConsumer<CommittedChangeList>() {
                public void finished() {
                }

                public void consume(CommittedChangeList committedChangeList) {
                  final SvnChangeList svnList = (SvnChangeList)committedChangeList;
                  if (localLatest >= svnList.getNumber()) return;

                  final SvnMergeInfoCache.MergeCheckResult checkResult =
                    myBranchInfo.checkList(svnList, myWcInfo.getPath());
                  if (SvnMergeInfoCache.MergeCheckResult.NOT_MERGED.equals(checkResult)) {
                    myNotMerged.add(svnList);
                  }
                }
              });
          }
          catch (VcsException e) {
            AbstractVcsHelper.getInstance(myProject).showErrors(Collections.singletonList(e), "Checking revisions for merge fault");
          }
        }
      }).run();
    }

    @Override
    public void onCancel() {
      onSuccess();
    }

    @Nullable
    private MergerFactory askParameters() {
      final MergerFactory factory;
      final ToBeMergedDialog dialog = new ToBeMergedDialog(myProject, myNotMerged, myMergeTitle, myBranchInfo);
      dialog.show();
      if (dialog.getExitCode() == DialogWrapper.CANCEL_EXIT_CODE) {
        return null;
      }
      final List<CommittedChangeList> lists = dialog.getSelected();
      if (lists.isEmpty()) return null;
      factory = new ChangeListsMergerFactory(lists);
      return factory;
    }

    @Override
    public void onSuccess() {
      if (myNotMerged.isEmpty()) {
        ChangesViewBalloonProblemNotifier.showMe(myProject, "Everything is up-to-date", MessageType.WARNING);
        return;
      }
      final MergerFactory factory = askParameters();
      if (factory == null) return;
      doMerge(factory, myMergeTitle);
    }
  }
}
