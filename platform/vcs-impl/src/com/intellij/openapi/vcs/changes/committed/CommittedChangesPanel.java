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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 05.12.2006
 * Time: 19:39:22
 */
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.FilterComponent;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommittedChangesPanel extends JPanel implements TypeSafeDataProvider, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.committed.CommittedChangesPanel");

  private final CommittedChangesTreeBrowser myBrowser;
  private final Project myProject;
  private CommittedChangesProvider myProvider;
  private ChangeBrowserSettings mySettings;
  private final RepositoryLocation myLocation;
  private int myMaxCount = 0;
  private final FilterComponent myFilterComponent = new MyFilterComponent();
  private List<CommittedChangeList> myChangesFromProvider;
  private final JLabel myErrorLabel = new JLabel();
  private final List<Runnable> myShouldBeCalledOnDispose;

  public CommittedChangesPanel(Project project, final CommittedChangesProvider provider, final ChangeBrowserSettings settings,
                               @Nullable final RepositoryLocation location, @Nullable ActionGroup extraActions) {
    super(new BorderLayout());
    mySettings = settings;
    myProject = project;
    myProvider = provider;
    myLocation = location;
    myShouldBeCalledOnDispose = new ArrayList<Runnable>();
    myBrowser = new CommittedChangesTreeBrowser(project, new ArrayList<CommittedChangeList>());
    add(myBrowser, BorderLayout.CENTER);

    myErrorLabel.setForeground(Color.red);
    add(myErrorLabel, BorderLayout.SOUTH);

    final VcsCommittedViewAuxiliary auxiliary = provider.createActions(myBrowser, location);

    JPanel toolbarPanel = new JPanel(new BorderLayout());
    ActionGroup group = (ActionGroup) ActionManager.getInstance().getAction("CommittedChangesToolbar");

    ActionToolbar toolBar = myBrowser.createGroupFilterToolbar(project, group, extraActions,
                                                               auxiliary != null ? auxiliary.getToolbarActions() : Collections.<AnAction>emptyList());
    toolbarPanel.add(toolBar.getComponent(), BorderLayout.WEST);

    toolbarPanel.add(myFilterComponent, BorderLayout.EAST);
    myBrowser.addToolBar(toolbarPanel);

    if (auxiliary != null) {
      myShouldBeCalledOnDispose.add(auxiliary.getCalledOnViewDispose());
      myBrowser.setTableContextMenu(group, (auxiliary.getPopupActions() == null) ? Collections.<AnAction>emptyList() : auxiliary.getPopupActions());
    } else {
      myBrowser.setTableContextMenu(group, Collections.<AnAction>emptyList());
    }
    
    final AnAction anAction = ActionManager.getInstance().getAction("CommittedChanges.Refresh");
    anAction.registerCustomShortcutSet(CommonShortcuts.getRerun(), this);
  }

  public RepositoryLocation getRepositoryLocation() {
    return myLocation;
  }

  public void setMaxCount(final int maxCount) {
    myMaxCount = maxCount;
  }

  public void setProvider(final CommittedChangesProvider provider) {
    if (myProvider != provider) {
      myProvider = provider;
      mySettings = provider.createDefaultSettings(); 
    }
  }

  public void refreshChanges(final boolean cacheOnly) {
    if (myLocation != null) {
      refreshChangesFromLocation();
    }
    else {
      refreshChangesFromCache(cacheOnly);
    }
  }

  private void refreshChangesFromLocation() {
    final Ref<VcsException> refEx = new Ref<VcsException>();
    final Ref<List<CommittedChangeList>> changes = new Ref<List<CommittedChangeList>>();
    boolean completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        try {
          changes.set(myProvider.getCommittedChanges(mySettings, myLocation, myMaxCount));
        }
        catch (VcsException ex) {
          refEx.set(ex);
        }
      }
    }, "Loading changes", true, myProject);
    if (!refEx.isNull()) {
      LOG.info(refEx.get());
      Messages.showErrorDialog(myProject, "Error refreshing view: " + StringUtil.join(refEx.get().getMessages(), "\n"), "Committed Changes");
    }
    else if (completed) {
      myChangesFromProvider = changes.get();
      updateFilteredModel(false);
    }
  }

  private void refreshChangesFromCache(final boolean cacheOnly) {
    final CommittedChangesCache cache = CommittedChangesCache.getInstance(myProject);
    if (!cache.hasCachesForAnyRoot()) {
      if (cacheOnly) {
        myBrowser.setEmptyText(VcsBundle.message("committed.changes.not.loaded.message"));
        return;
      }
      if (!CacheSettingsDialog.showSettingsDialog(myProject)) return;
    }
    cache.getProjectChangesAsync(mySettings, myMaxCount, cacheOnly,
                                 new Consumer<List<CommittedChangeList>>() {
                                   public void consume(final List<CommittedChangeList> committedChangeLists) {
                                     myChangesFromProvider = committedChangeLists;
                                     updateFilteredModel(false);
                                     }
                                   },
                                 new Consumer<List<VcsException>>() {
                                   public void consume(final List<VcsException> vcsExceptions) {
                                     AbstractVcsHelper.getInstance(myProject).showErrors(vcsExceptions, "Error refreshing VCS history");
                                   }
                                 });
  }

  private void updateFilteredModel(final boolean keepFilter) {
    if (myChangesFromProvider == null) {
      return;
    }
    myBrowser.setEmptyText(VcsBundle.message("committed.changes.empty.message"));
    if (StringUtil.isEmpty(myFilterComponent.getFilter())) {
      myBrowser.setItems(myChangesFromProvider, keepFilter, CommittedChangesBrowserUseCase.COMMITTED);
    }
    else {
      final String[] strings = myFilterComponent.getFilter().split(" ");
      for(int i=0; i<strings.length; i++) {
        strings [i] = strings [i].toLowerCase();
      }
      List<CommittedChangeList> filteredChanges = new ArrayList<CommittedChangeList>();
      for(CommittedChangeList changeList: myChangesFromProvider) {
        if (changeListMatches(changeList, strings)) {
          filteredChanges.add(changeList);
        }
      }
      myBrowser.setItems(filteredChanges, keepFilter, CommittedChangesBrowserUseCase.COMMITTED);
    }
  }

  private static boolean changeListMatches(@NotNull final CommittedChangeList changeList, final String[] filterWords) {
    for(String word: filterWords) {
      final String comment = changeList.getComment();
      final String committer = changeList.getCommitterName();
      if ((comment != null && comment.toLowerCase().indexOf(word) >= 0) ||
          (committer != null && committer.toLowerCase().indexOf(word) >= 0) ||
          Long.toString(changeList.getNumber()).indexOf(word) >= 0) {
        return true;
      }
    }
    return false;
  }

  public void setChangesFilter() {
    CommittedChangesFilterDialog filterDialog = new CommittedChangesFilterDialog(myProject, myProvider.createFilterUI(true), mySettings);
    filterDialog.show();
    if (filterDialog.isOK()) {
      mySettings = filterDialog.getSettings();
      refreshChanges(false);
    }
  }

  public void calcData(DataKey key, DataSink sink) {
    if (key.equals(VcsDataKeys.CHANGES) || key.equals(VcsDataKeys.CHANGE_LISTS)) {
      myBrowser.calcData(key, sink);
    }
  }

  public void dispose() {
    myBrowser.dispose();
    for (Runnable runnable : myShouldBeCalledOnDispose) {
      runnable.run();
    }
  }

  public void setErrorText(String text) {
    myErrorLabel.setText(text);
  }

  private class MyFilterComponent extends FilterComponent {
    public MyFilterComponent() {
      super("COMMITTED_CHANGES_FILTER_HISTORY", 20);
    }

    public void filter() {
      updateFilteredModel(true);
    }
  }

  public void passCachedListsToListener(final VcsConfigurationChangeListener.DetailedNotification notification,
                                        final Project project, final VirtualFile root) {
    if ((myChangesFromProvider != null) && (! myChangesFromProvider.isEmpty())) {
      notification.execute(project, root, myChangesFromProvider);
    }
  }
}
