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
package git4idea.history.wholeTree;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.openapi.vcs.CompoundNumber;
import com.intellij.openapi.vcs.StaticReadonlyList;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BufferedListConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import git4idea.history.browser.ChangesFilter;
import git4idea.history.browser.GitCommit;
import git4idea.history.browser.LowLevelAccess;
import git4idea.history.browser.LowLevelAccessImpl;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author irengrig
 */
public class LoaderImpl implements Loader {
  private static final Logger LOG = Logger.getInstance("#git4idea.history.wholeTree.LoaderImpl");

  private static final long ourTestTimeThreshold = 100;
  private final static int ourBigArraysSize = 10;
  private final static int ourTestCount = 5;
  private final static int ourSlowPreloadCount = 50;
  private final TreeComposite<VisibleLine> myTreeComposite;
  private final Map<VirtualFile, LowLevelAccess> myAccesses;
  private final LinesProxy myLinesCache;
  
  private int myLoadId;
  private boolean mySomeDataShown;
  private final Object myLock;
  
  private GitLogLongPanel.UIRefresh myUIRefresh;
  private final Project myProject;
  private ModalityState myModalityState;

  private final BackgroundTaskQueue myQueue;
  private final CommitIdsHolder<Pair<VirtualFile, String>> myCommitIdsHolder;
  private List<VirtualFile> myRootsList;

  public LoaderImpl(final Project project, final Collection<VirtualFile> allGitRoots) {
    myProject = project;
    myTreeComposite = new TreeComposite<VisibleLine>(ourBigArraysSize, WithoutDecorationComparator.getInstance());
    myLinesCache = new LinesProxy(myTreeComposite);
    myAccesses = new HashMap<VirtualFile, LowLevelAccess>();
    for (VirtualFile gitRoot : allGitRoots) {
      myAccesses.put(gitRoot, new LowLevelAccessImpl(project, gitRoot));
    }
    // todo refresh roots
    myRootsList = new ArrayList<VirtualFile>(myAccesses.keySet());
    Collections.sort(myRootsList, FilePathComparator.getInstance());
    myLock = new Object();
    myLoadId = 0;
    myQueue = new BackgroundTaskQueue(project, "Git log");
    myCommitIdsHolder = new CommitIdsHolder();
  }

  public LinesProxy getLinesProxy() {
    return myLinesCache;
  }

  public void setModalityState(ModalityState modalityState) {
    myModalityState = modalityState;
  }

  public void setUIRefresh(GitLogLongPanel.UIRefresh UIRefresh) {
    myUIRefresh = UIRefresh;
  }

  private class MyJoin implements Runnable {
    private final int myId;

    public MyJoin(final int id) {
      myId = id;
    }

    @Override
    public void run() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          synchronized (myLock) {
            if (myId == myLoadId) {
              myUIRefresh.skeletonLoadComplete();
            }
          }
        }
      }, myModalityState, new Condition() {
        @Override
        public boolean value(Object o) {
          return (! (! myProject.isDisposed()) && (myId == myLoadId));
        }
      });
    }
  }

  public TreeComposite<VisibleLine> getTreeComposite() {
    return myTreeComposite;
  }

  @CalledInAwt
  @Override
  public void loadSkeleton(final Collection<String> startingPoints, final Collection<ChangesFilter.Filter> filters) {
    // load first portion, limited, measure time, decide whether to load only ids or load commits...
    final Application application = ApplicationManager.getApplication();
    application.assertIsDispatchThread();

    final int current;
    synchronized (myLock) {
      current = ++ myLoadId;
      mySomeDataShown = false;
    }
    final boolean drawHierarchy = filters.isEmpty();

    myQueue.run(new Task.Backgroundable(myProject, "Git log refresh", false, BackgroundFromStartOption.getInstance()) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          final Join join = new Join(myAccesses.size(), new MyJoin(current));
          final Runnable joinCaller = new Runnable() {
            @Override
            public void run() {
              join.complete();
            }
          };

          myTreeComposite.clearMembers();

          final List<LoaderBase> endOfTheList = new LinkedList<LoaderBase>();
          final Consumer<CommitHashPlusParents> consumer = createCommitsHolderConsumer(drawHierarchy);
          final RefreshingCommitsPackConsumer listConsumer = new RefreshingCommitsPackConsumer(current, consumer);

          for (VirtualFile vf : myRootsList) {
            final LowLevelAccess access = myAccesses.get(vf);

            final BufferedListConsumer<CommitHashPlusParents> bufferedListConsumer =
              new BufferedListConsumer<CommitHashPlusParents>(15, listConsumer, 400);
            bufferedListConsumer.setFlushListener(joinCaller);

            final long start = System.currentTimeMillis();
            final boolean allDataAlreadyLoaded =
              FullDataLoader.load(myLinesCache, access, startingPoints, filters, bufferedListConsumer.asConsumer(), ourTestCount);
            final long end = System.currentTimeMillis();

            bufferedListConsumer.flushPart();
            if (allDataAlreadyLoaded) {
              bufferedListConsumer.flush();
            } else {
              final boolean loadFull = (end - start) > ourTestTimeThreshold;
              if (loadFull) {
                final LoaderBase loaderBase = new LoaderBase(access, bufferedListConsumer, filters,
                  ourTestCount, loadFull, startingPoints, myLinesCache, ourSlowPreloadCount);
                listConsumer.setInterruptRunnable(loaderBase.getStopper());
                loaderBase.execute();
                endOfTheList.add(new LoaderBase(access, bufferedListConsumer, filters, ourSlowPreloadCount, false, startingPoints, myLinesCache, -1));
              } else {
                final LoaderBase loaderBase = new LoaderBase(access, bufferedListConsumer, filters, ourTestCount, loadFull, startingPoints, myLinesCache, -1);
                listConsumer.setInterruptRunnable(loaderBase.getStopper());
                loaderBase.execute();
              }
            }
          }
          myQueue.run(new Task.Backgroundable(myProject, "Git log refresh", false, BackgroundFromStartOption.getInstance()) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (LoaderBase loaderBase : endOfTheList) {
          try {
            listConsumer.setInterruptRunnable(loaderBase.getStopper());
            loaderBase.execute();
          }
          catch (VcsException e) {
            myUIRefresh.acceptException(e);
          }
        }
      }});
        } catch (VcsException e) {
          myUIRefresh.acceptException(e);
        } catch (MyStopListenToOutputException e) {
        } finally {
          //myUIRefresh.skeletonLoadComplete();
        }
      }
    });
  }

  private Consumer<CommitHashPlusParents> createCommitsHolderConsumer(boolean drawHierarchy) {
    Consumer<CommitHashPlusParents> consumer;
    if (drawHierarchy) {
      final SkeletonBuilder skeletonBuilder = new SkeletonBuilder(ourBigArraysSize, ourBigArraysSize - 1);
      consumer = skeletonBuilder;
      myTreeComposite.addMember(skeletonBuilder.getResult());
    } else {
      final StaticReadonlyList<VisibleLine> readonlyList = new StaticReadonlyList<VisibleLine>(ourBigArraysSize);
      consumer = new Consumer<CommitHashPlusParents>() {
        @Override
        public void consume(CommitHashPlusParents commitHashPlusParents) {
          readonlyList.consume(new TreeSkeletonImpl.Commit(commitHashPlusParents.getHash(), 0, commitHashPlusParents.getTime()));
        }
      };
      myTreeComposite.addMember(readonlyList);
    }
    return consumer;
  }

  private static class MyStopListenToOutputException extends RuntimeException {}

  private class RefreshingCommitsPackConsumer implements Consumer<List<CommitHashPlusParents>> {
    private final int myId;
    private final Consumer<CommitHashPlusParents> myConsumer;
    private Application myApplication;
    private final Runnable myRefreshRunnable;
    private final Condition myRefreshCondition;
    private Runnable myInterruptRunnable;

    public void setInterruptRunnable(final Runnable interruptRunnable) {
      final Boolean[] interruptedNotified = new Boolean[1];
      myInterruptRunnable = new Runnable() {
        @Override
        public void run() {
          if (! Boolean.TRUE.equals(interruptedNotified[0])) {
            interruptRunnable.run();
          }
          interruptedNotified[0] = true;
        }
      };
    }

    public RefreshingCommitsPackConsumer(int id, Consumer<CommitHashPlusParents> consumer) {
      myId = id;
      myConsumer = consumer;
      myInterruptRunnable = EmptyRunnable.getInstance();
      myApplication = ApplicationManager.getApplication();
      myRefreshRunnable = new Runnable() {
        @Override
        public void run() {
          if (myTreeComposite.getAwaitedSize() == myTreeComposite.getSize()) return;
          LOG.info("Items refresh: (was=" + myTreeComposite.getSize() + " will be=" + myTreeComposite.getAwaitedSize());
          myTreeComposite.repack();
          if (! mySomeDataShown) {
            // todo remove
            myUIRefresh.setSomeDataReadyState();
          }
          myUIRefresh.fireDataReady(0, myTreeComposite.getSize());
          mySomeDataShown = true;
        }
      };
      myRefreshCondition = new Condition() {
        @Override
        public boolean value(Object o) {
          return (! (! myProject.isDisposed()) && myId == myLoadId);
        }
      };
    }

    @Override
    public void consume(List<CommitHashPlusParents> commitHashPlusParentses) {
      boolean toInterrupt = false;
      synchronized (myLock) {
        toInterrupt = myId != myLoadId;
      }
      if (toInterrupt) {
        myInterruptRunnable.run();
        return;
      }
      for (CommitHashPlusParents item : commitHashPlusParentses) {
        myConsumer.consume(item);
      }
      synchronized (myLock) {
        toInterrupt = myId != myLoadId;
        if (! toInterrupt) {
          myApplication.invokeLater(myRefreshRunnable, myModalityState, myRefreshCondition);
        }
      }
      if (toInterrupt) {
        myInterruptRunnable.run();
      }
    }
  }

  private static class LoaderBase {
    private final boolean myLoadFullData;
    private final BufferedListConsumer<CommitHashPlusParents> myConsumer;
    private final LowLevelAccess myAccess;
    private final Collection<String> myStartingPoints;
    private final Consumer<GitCommit> myLinesCache;
    private final int myMaxCount;
    private final Collection<ChangesFilter.Filter> myFilters;
    private final int myIgnoreFirst;
    private Runnable myStopper;

    public LoaderBase(LowLevelAccess access,
                       BufferedListConsumer<CommitHashPlusParents> consumer,
                       Collection<ChangesFilter.Filter> filters,
                       int ignoreFirst, boolean loadFullData, Collection<String> startingPoints, final Consumer<GitCommit> linesCache,
                       final int maxCount) {
      myAccess = access;
      myConsumer = consumer;
      myFilters = filters;
      myIgnoreFirst = ignoreFirst;
      myLoadFullData = loadFullData;
      myStartingPoints = startingPoints;
      myLinesCache = linesCache;
      myMaxCount = maxCount;
      myStopper = EmptyRunnable.getInstance();
    }

    public void execute() throws VcsException {
      final MyConsumer consumer = new MyConsumer(myConsumer, 0);

      if (myLoadFullData) {
        LOG.info("FULL " + myMaxCount);
        FullDataLoader.load(myLinesCache, myAccess, myStartingPoints, myFilters, myConsumer.asConsumer(), myMaxCount);
      } else {
        LOG.info("SKELETON " + myMaxCount);
        myStopper = myAccess.loadHashesWithParents(myStartingPoints, myFilters, consumer);
      }
      myConsumer.flush();
    }

    public Runnable getStopper() {
      return myStopper;
    }

    private static class MyConsumer implements Consumer<CommitHashPlusParents> {
      private final int myIgnoreFirst;
      private final BufferedListConsumer<CommitHashPlusParents> myConsumer;
      private int myCnt;

      private MyConsumer(BufferedListConsumer<CommitHashPlusParents> consumer, int ignoreFirst) {
        myConsumer = consumer;
        myIgnoreFirst = ignoreFirst;
        myCnt = 0;
      }

      @Override
      public void consume(CommitHashPlusParents commitHashPlusParents) {
        if (myCnt >= myIgnoreFirst) {
          myConsumer.consumeOne(commitHashPlusParents);
        }
        ++ myCnt;
      }
    }
  }

  // true if there are no more rows
  private static class FullDataLoader {
    private boolean myLoadIsComplete;
    private int myCnt;

    private FullDataLoader() {
      myLoadIsComplete = false;
      myCnt = 0;
    }

    public static boolean load(final Consumer<GitCommit> linesCache, final LowLevelAccess access, final Collection<String> startingPoints,
                               final Collection<ChangesFilter.Filter> filters, final Consumer<CommitHashPlusParents> consumer,
                               final int maxCnt) throws VcsException {
      return new FullDataLoader().loadFullData(linesCache, access, startingPoints, filters, consumer, maxCnt);
    }

    private boolean loadFullData(final Consumer<GitCommit> linesCache, final LowLevelAccess access, final Collection<String> startingPoints,
                               final Collection<ChangesFilter.Filter> filters, final Consumer<CommitHashPlusParents> consumer,
                               final int maxCnt) throws VcsException {
    access.loadCommits(startingPoints, null, null, filters, new Consumer<GitCommit>() {
      @Override
      public void consume(GitCommit gitCommit) {
        linesCache.consume(gitCommit);
        consumer.consume(GitCommitToCommitConvertor.getInstance().convert(gitCommit));
        if (gitCommit.getParentsHashes().isEmpty()) {
          myLoadIsComplete = true;
        }
        ++ myCnt;
      }
    }, maxCnt, Collections.<String>emptyList());
    return myLoadIsComplete || (maxCnt > 0) && (myCnt < maxCnt);
  }
  }

  private static class GitCommitToCommitConvertor implements Convertor<GitCommit, CommitHashPlusParents> {
    private final static GitCommitToCommitConvertor ourInstance = new GitCommitToCommitConvertor();

    public static GitCommitToCommitConvertor getInstance() {
      return ourInstance;
    }

    @Override
    public CommitHashPlusParents convert(GitCommit o) {
      final Set<String> parentsHashes = o.getParentsHashes();
      return new CommitHashPlusParents(o.getShortHash(), parentsHashes.toArray(new String[parentsHashes.size()]), o.getDate().getTime());
    }
  }

  private static class WithoutDecorationComparator implements Comparator<Pair<CompoundNumber, VisibleLine>> {
    private final static WithoutDecorationComparator ourInstance = new WithoutDecorationComparator();

    public static WithoutDecorationComparator getInstance() {
      return ourInstance;
    }

    @Override
    public int compare(Pair<CompoundNumber, VisibleLine> o1, Pair<CompoundNumber, VisibleLine> o2) {
      if (o1 == null || o2 == null) {
        return o1 == null ? -1 : 1;
      }
      final Object obj1 = o1.getSecond();
      final Object obj2 = o2.getSecond();

      if (obj1 instanceof TreeSkeletonImpl.Commit && obj2 instanceof TreeSkeletonImpl.Commit) {
        final long diff;
        if (o1.getFirst().getMemberNumber() == o2.getFirst().getMemberNumber()) {
          // natural order
          diff = o1.getFirst().getIdx() - o2.getFirst().getIdx();
        } else {
          // lets take time here
          diff = - (((TreeSkeletonImpl.Commit)obj1).getTime() - ((TreeSkeletonImpl.Commit)obj2).getTime());
        }
        return diff == 0 ? 0 : (diff < 0 ? -1 : 1);
      }
      return Comparing.compare(obj1.toString(), obj2.toString());
    }
  }

  private final static int ourLoadSize = 30;

  public void loadCommitDetails(final int startIdx, final int endIdx) {
    final Set<Pair<VirtualFile, String>> newIds = new HashSet<Pair<VirtualFile, String>>();
    for (int i = startIdx; i <= endIdx; i++) {
      if (myLinesCache.shouldLoad(i)) {
        final TreeSkeletonImpl.Commit commit = (TreeSkeletonImpl.Commit) myTreeComposite.get(i).getData();
        final CompoundNumber memberData = myTreeComposite.getMemberData(i);
        newIds.add(new Pair<VirtualFile, String>(myRootsList.get(memberData.getMemberNumber()), String.valueOf(commit.getHash())));
      }
    }

    myCommitIdsHolder.add(newIds);
    scheduleDetailsLoad();
  }

  private void scheduleDetailsLoad() {
    final Collection<Pair<VirtualFile, String>> toLoad = myCommitIdsHolder.get(ourLoadSize);
    if (toLoad.isEmpty()) return;
    myQueue.run(new Task.Backgroundable(myProject, "Load git commits details", false, BackgroundFromStartOption.getInstance()) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final MultiMap<VirtualFile, String> map = new MultiMap<VirtualFile, String>();
        for (Pair<VirtualFile, String> pair : toLoad) {
          map.putValue(pair.getFirst(), pair.getSecond());
        }
        for (VirtualFile virtualFile : map.keySet()) {
          try {
            final Collection<String> values = map.get(virtualFile);
            final List<GitCommit> commitDetails = myAccesses.get(virtualFile).getCommitDetails(values);
            for (GitCommit commitDetail : commitDetails) {
              myLinesCache.consume(commitDetail);
            }
            // todo another UI event
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                myUIRefresh.setSomeDataReadyState();
              }
            }, myModalityState);
          }
          catch (final VcsException e) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                myUIRefresh.acceptException(e);
              }
            }, myModalityState);
          }
        }
        if (myCommitIdsHolder.haveData()) {
          scheduleDetailsLoad();
        }
      }
    });
  }
}
