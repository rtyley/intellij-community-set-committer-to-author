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
package git4idea.history.browser;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.RequestsMerger;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.containers.SLRUCache;
import git4idea.changes.GitChangeUtils;
import git4idea.config.GitConfigUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

class GitTreeController implements ManageGitTreeView {
  private final Project myProject;
  private final VirtualFile myRoot;
  private final GitTreeViewI myTreeView;

  private final LowLevelAccess myAccess;
  private volatile boolean myInitialized;

  // guarded by lock
  private final AtomicReference<List<String>> myTags;
  private final AtomicReference<List<String>> myBranches;
  private final AtomicReference<List<String>> myUsers;

  private final MyFiltersStateHolder myFilterHolder;
  private final MyFiltersStateHolder myHighlightingHolder;

  private final RequestsMerger myFilterRequestsMerger;
  //private final RequestsMerger myHighlightingRequestsMerger;

  private final MyUpdateStateInterceptor myFiltering;
  private final MyUpdateStateInterceptor myHighlighting;
  private Alarm myAlarm;
  private Portion myFiltered;

  private final SLRUCache<SHAHash, CommittedChangeList> myListsCache = new SLRUCache<SHAHash, CommittedChangeList>(16, 64) {
    @NotNull
    @Override
    public CommittedChangeList createValue(SHAHash key) {
      try {
        return GitChangeUtils.getRevisionChanges(myProject, myRoot, key.getValue(), true);
      }
      catch (VcsException e) {
        return new CommittedChangeListImpl(e.getMessage(), "", "", -1, null, Collections.<Change>emptyList());
      }
    }
  };

  GitTreeController(final Project project, final VirtualFile root, final GitTreeViewI treeView) {
    myProject = project;
    myRoot = root;
    myTreeView = treeView;
    myAccess = new LowLevelAccessImpl(project, root);

    myFilterHolder = new MyFiltersStateHolder();
    myHighlightingHolder = new MyFiltersStateHolder();

    myTags = new AtomicReference<List<String>>(Collections.<String>emptyList());
    myBranches = new AtomicReference<List<String>>(Collections.<String>emptyList());
    myUsers = new AtomicReference<List<String>>(Collections.<String>emptyList());

    // todo !!!!! dispose
    myAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD, project);
    myFilterRequestsMerger = new RequestsMerger(new Runnable() {
      public void run() {
        try {
          if (myFilterHolder.isDirty()) {
            final Portion filtered = loadPortion(myFilterHolder.getStartingPoints(), myFilterHolder.getCurrentPoint(), null,
                                                 myFilterHolder.getFilters(), PageSizes.LOAD_SIZE);
            if (filtered == null) return;

            final List<GitCommit> commitList = filtered.getXFrom(0, PageSizes.VISIBLE_PAGE_SIZE);
            myFiltered = filtered;
            myTreeView.refreshView(commitList, new TravelTicket(filtered.isStartFound(), filtered.getLast().getDate()));

            myFilterHolder.setDirty(false);
          }

          // highlighting
          if (myHighlightingHolder.isNothingSelected()) {
            myTreeView.clearHighlighted();
            return;
          }

          myTreeView.acceptHighlighted(loadIdsToHighlight());
        } finally {
          myTreeView.refreshFinished();
        }
      }
    }, new Consumer<Runnable>() {
      public void consume(Runnable runnable) {
        myTreeView.refreshStarted();
        myAlarm.addRequest(runnable, 50);
      }
    });
    /*myHighlightingRequestsMerger = new RequestsMerger(new Runnable() {
      public void run() {
        if (myHighlightingHolder.isNothingSelected()) {
          myTreeView.clearHighlighted();
          return;
        }

        myTreeView.acceptHighlighted(loadIdsToHighlight());
      }
    }, new Consumer<Runnable>() {
      public void consume(Runnable runnable) {
        myTreeView.refreshStarted();
        application.executeOnPooledThread(runnable);
        myTreeView.refreshFinished();
      }
    });*/

    myFiltering = new MyUpdateStateInterceptor(myFilterRequestsMerger, myFilterHolder, null);
    myHighlighting = new MyUpdateStateInterceptor(myFilterRequestsMerger, myHighlightingHolder, null);
    myFilterHolder.setDirty(true);
  }

  private Set<SHAHash> loadIdsToHighlight() {
    final Portion highlighted;
    if (myFiltered == null) {
      // todo rewrite when dates are introduced
      highlighted = loadPortion(myHighlightingHolder.getStartingPoints(), myFilterHolder.getCurrentPoint(),
                                              null, Collections.<ChangesFilter.Filter>emptyList(), -1);
    } else {
      highlighted = loadPortion(myHighlightingHolder.getStartingPoints(), myFilterHolder.getCurrentPoint(),
                                              myFiltered.getLast().getDate(), Collections.<ChangesFilter.Filter>emptyList(), -1);
    }

    final Collection<ChangesFilter.Filter> filters = myHighlightingHolder.getFilters();
    final List<ChangesFilter.MemoryFilter> combined = ChangesFilter.combineFilters(filters);

    final Set<SHAHash> highlightedIds = new HashSet<SHAHash>();
    highlighted.iterateFrom(0, new Processor<GitCommit>() {
      public boolean process(GitCommit gitCommit) {
        for (ChangesFilter.MemoryFilter filter : combined) {
          if (! filter.applyInMemory(gitCommit)) {
            return false;
          }
        }
        highlightedIds.add(gitCommit.getHash());
        return false;
      }
    });

    return highlightedIds;
  }

  // !!!! after point is included! (should be)
  @Nullable
  private Portion loadPortion(final List<String> startingPoints, final Date beforePoint, final Date afterPoint,
                              final Collection<ChangesFilter.Filter> filtersIn, int maxCnt) {
    try {
      final Collection<ChangesFilter.Filter> filters = new LinkedList<ChangesFilter.Filter>(filtersIn);
      if (beforePoint != null) {
        filters.add(new ChangesFilter.BeforeDate(new Date(beforePoint.getTime() - 1)));
      }
      if (afterPoint != null) {
        filters.add(new ChangesFilter.AfterDate(afterPoint));
      }

      final Portion portion = new Portion(null);
      myAccess.loadCommits(startingPoints, Collections.<String>emptyList(), filters, portion, myBranches.get(), maxCnt);
      return portion;
    }
    catch (VcsException e) {
      myTreeView.acceptError(e.getMessage());
      return null;
    }
  }

  // todo not-so-good
  /*@Nullable
  private Portion loadPortion(@Nullable final ChangesFilter.Filter filter, final boolean useMaxCnt, final MyFiltersStateHolder... holders) {
    try {
      final Collection<ChangesFilter.Filter> filters = new LinkedList<ChangesFilter.Filter>();
      final List<String> startingPoints = new LinkedList<String>();

      // continuation condition
      final Date point = myFilterHolder.getCurrentPoint();
      if (point != null) {
        filters.add(new ChangesFilter.BeforeDate(new Date(point.getTime() - 1)));
      }
      if (filter != null) {
        filters.add(filter);
      }

      for (MyFiltersStateHolder holder : holders) {
        startingPoints.addAll(holder.getStartingPoints());
        filters.addAll(holder.getFilters());
      }
      final Portion portion = new Portion(null);

      myAccess.loadCommits(startingPoints, Collections.<String>emptyList(), filters, portion, myBranches.get(), useMaxCnt);
      return portion;
    }
    catch (VcsException e) {
      myTreeView.acceptError(e.getMessage());
      return null;
    }
  }*/

  private String getStatusMessage() {
    // todo
    return "Showing";
  }

  private void loadTagsNBranches(final boolean loadBranches, final boolean loadTags, final boolean loadUsers) {
    final List<String> branches = new LinkedList<String>();
    final List<String> tags = new LinkedList<String>();

    try {
      if (loadBranches) {
        myAccess.loadAllBranches(branches);
        Collections.sort(branches);
        myBranches.set(branches);
      }
      if (loadTags) {
        myAccess.loadAllTags(tags);
        Collections.sort(tags);
        myTags.set(tags);
      }

      if (loadUsers) {
        final List<Pair<String,String>> value = GitConfigUtil.getAllValues(myProject, myRoot, "user.name");
        final String username = value.size() == 1 ? value.get(0).getSecond() : null;

        final Portion p = loadPortion(Collections.<String>emptyList(), null, null, Collections.<ChangesFilter.Filter>emptyList(), 500);
        final Set<String> users = new HashSet<String>();
        p.iterateFrom(0, new Processor<GitCommit>() {
          public boolean process(GitCommit gitCommit) {
            users.add(gitCommit.getAuthor());
            users.add(gitCommit.getCommitter());
            return false;
          }
        });
        final ArrayList<String> usersList = new ArrayList<String>(users);
        Collections.sort(usersList);
        if (username != null) {
          usersList.remove(username);
          usersList.add(0, username);
        }
        myUsers.set(usersList);
      }
    }
    catch (VcsException e) {
      myTreeView.acceptError(e.getMessage());
    }
  }

  // no filter - or saved filter? -> then pass
  public void init() {
    assert ! myInitialized;

    myAlarm.addRequest(new Runnable() {
      public void run() {
        myFilterRequestsMerger.request();
        loadTagsNBranches(true, true, false);
        myInitialized = true;
        myTreeView.controllerReady();

        myAlarm.addRequest(new Runnable() {
          public void run() {
            loadTagsNBranches(false, false, true);
          }
        }, 100);
      }
    }, 100);
  }

  public boolean hasNext(final TravelTicket ticket) {
    return (ticket == null) || (! ticket.isIsBottomReached());
  }

  public boolean hasPrevious(final TravelTicket ticket) {
    return myFilterHolder.getCurrentPoint() != null;
  }

  public void next(final TravelTicket ticket) {
    myFilterHolder.addContinuationPoint(ticket.getLatestDate());
    myFilterRequestsMerger.request();
  }

  public void previous(final TravelTicket ticket) {
    myFilterHolder.popContinuationPoint();
    myFilterRequestsMerger.request();
  }

  public void refresh() {
    myFilterHolder.setDirty(true);
    myFilterRequestsMerger.request();
    myAlarm.addRequest(new Runnable() {
      public void run() {
        // todo USERS ARE NOT REFRESHED now! to be fixed after loading them faster
        loadTagsNBranches(true, true, false);
      }
    }, 100);
  }

  public void getDetails(final Collection<SHAHash> hashes) {
    final Application application = ApplicationManager.getApplication();
    myAlarm.addRequest(new Runnable() {
      public void run() {
        final List<CommittedChangeList> loaded = new LinkedList<CommittedChangeList>();
        final Set<Long> requested = new HashSet<Long>(hashes.size());
        for (SHAHash hash : hashes) {
          requested.add(GitChangeUtils.longForSHAHash(hash.getValue()));

          final CommittedChangeList changeList = myListsCache.get(hash);
          if (requested.contains(changeList.getNumber())) {
            loaded.add(changeList);
          }
        }
        if (! loaded.isEmpty()) {
          application.invokeLater(new Runnable() {
            public void run() {
              myTreeView.acceptDetails(loaded);
            }
          });
        }
      }
    }, 30);
  }

  public GitTreeFiltering getFiltering() {
    return myFiltering;
  }

  public GitTreeFiltering getHighlighting() {
    return myHighlighting;
  }

  public List<String> getKnownUsers() {
    return new ArrayList<String>(myUsers.get());
  }

  public List<String> getAllBranchesOrdered() {
    return new ArrayList<String>(myBranches.get());
  }

  public List<String> getAllTagsOrdered() {
    return new ArrayList<String>(myTags.get());
  }

  public boolean isInitialized() {
    return myInitialized;
  }

  private static class MyUpdateStateInterceptor implements GitTreeFiltering {
    private final MyFiltersStateHolder myState;
    @Nullable private final RequestsMerger mySecond;
    private final RequestsMerger myRequestsMerger;

    protected MyUpdateStateInterceptor(RequestsMerger requestsMerger, MyFiltersStateHolder state, final @Nullable RequestsMerger mySecond) {
      myRequestsMerger = requestsMerger;
      myState = state;
      this.mySecond = mySecond;
    }

    private void requestRefresh() {
      myRequestsMerger.request();
      if (mySecond != null) {
        mySecond.request();
      }
    }

    public void addFilter(ChangesFilter.Filter filter) {
      myState.addFilter(filter);
      requestRefresh();
    }

    public void removeFilter(ChangesFilter.Filter filter) {
      myState.removeFilter(filter);
      requestRefresh();
    }

    public void addStartingPoint(String ref) {
      myState.addStartingPoint(ref);
      requestRefresh();
    }

    public void removeStartingPoint(String ref) {
      myState.removeStartingPoint(ref);
      requestRefresh();
    }

    public void updateExcludePoints(List<String> points) {
      myState.updateExcludePoints(points);
      requestRefresh();
    }

    public List<String> getStartingPoints() {
      return myState.getStartingPoints();
    }

    public List<String> getExcludePoints() {
      return myState.getExcludePoints();
    }

    public Collection<ChangesFilter.Filter> getFilters() {
      return myState.getFilters();
    }
  }

  private static class MyFiltersStateHolder implements GitTreeFiltering {
    private final Object myLock;
    private final List<String> myStartingPoints;
    private boolean myDirty;

    private final List<Date> myContinuationPoints;

    @Nullable
    private List<String> myExcludePoints;
    private final Collection<ChangesFilter.Filter> myFilters;

    private MyFiltersStateHolder() {
      myLock = new Object();
      myStartingPoints = new LinkedList<String>();
      myFilters = new LinkedList<ChangesFilter.Filter>();
      myContinuationPoints = new LinkedList<Date>();
    }

    public boolean isDirty() {
      synchronized (myLock) {
        return myDirty;
      }
    }

    public void setDirty(boolean dirty) {
      synchronized (myLock) {
        myDirty = dirty;
      }
    }

    @Nullable
    public List<String> getExcludePoints() {
      synchronized (myLock) {
        return myExcludePoints;
      }
    }

    // page starts
    public void addContinuationPoint(final Date point) {
      synchronized (myLock) {
        if ((! myContinuationPoints.isEmpty()) && myContinuationPoints.get(myContinuationPoints.size() - 1).equals(point)) return;
        myDirty = true;
        myContinuationPoints.add(point);
      }
    }

    @Nullable
    public Date popContinuationPoint() {
      synchronized (myLock) {
        myDirty = true;
        return myContinuationPoints.isEmpty() ? null : myContinuationPoints.remove(myContinuationPoints.size() - 1);
      }
    }

    public Date getCurrentPoint() {
      synchronized (myLock) {
        return myContinuationPoints.isEmpty() ? null : myContinuationPoints.get(myContinuationPoints.size() - 1);
      }
    }

    public void addFilter(ChangesFilter.Filter filter) {
      synchronized (myLock) {
        myDirty = true;
        myFilters.add(filter);
        myContinuationPoints.clear();
      }
    }

    public void removeFilter(ChangesFilter.Filter filter) {
      synchronized (myLock) {
        myDirty = true;
        myFilters.remove(filter);
        myContinuationPoints.clear();
      }
    }

    public void addStartingPoint(String ref) {
      synchronized (myLock) {
        myDirty = true;
        myStartingPoints.add(ref);
        myContinuationPoints.clear();
      }
    }

    public void removeStartingPoint(String ref) {
      synchronized (myLock) {
        myDirty = true;
        myStartingPoints.remove(ref);
        myContinuationPoints.clear();
      }
    }

    public void updateExcludePoints(List<String> points) {
      synchronized (myLock) {
        myDirty = true;
        myExcludePoints = points;
        myContinuationPoints.clear();
      }
    }

    @Nullable
    public List<String> getStartingPoints() {
      synchronized (myLock) {
        return myStartingPoints;
      }
    }

    public Collection<ChangesFilter.Filter> getFilters() {
      synchronized (myLock) {
        return myFilters;
      }
    }

    public boolean isNothingSelected() {
      synchronized (myLock) {
        return ((myExcludePoints == null) || (myExcludePoints.isEmpty())) && myFilters.isEmpty() && myStartingPoints.isEmpty();
      }
    }
  }
}
