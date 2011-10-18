/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Ticket;
import com.intellij.util.continuation.Continuation;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import git4idea.GitRevisionNumber;
import git4idea.history.GitHistoryUtils;
import git4idea.history.NewGitUsersComponent;
import git4idea.history.browser.ChangesFilter;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;

import java.util.*;

/**
 * @author irengrig
 */
public class LoadController implements Loader {
  private final UsersIndex myUsersIndex;
  private final Project myProject;
  private final Mediator myMediator;
  private final DetailsCache myDetailsCache;
  private final GitCommitsSequentially myGitCommitsSequentially;
  private LoadAlgorithm myPreviousAlgorithm;
  private NewGitUsersComponent myUsersComponent;
  private static final Logger LOG = Logger.getInstance("#git4idea.history.wholeTree.LoadController");
  private final Map<VirtualFile, Pair<String, Long>> mySortOrder;

  public LoadController(final Project project, final Mediator mediator, final DetailsCache detailsCache, final GitCommitsSequentially gitCommitsSequentially) {
    myProject = project;
    myMediator = mediator;
    myDetailsCache = detailsCache;
    myGitCommitsSequentially = gitCommitsSequentially;
    myUsersIndex = new UsersIndex();
    myUsersComponent = NewGitUsersComponent.getInstance(myProject);
    mySortOrder = new HashMap<VirtualFile, Pair<String, Long>>();
  }

  @CalledInAwt
  @Override
  public void loadSkeleton(final Ticket ticket,
                           final RootsHolder rootsHolder,
                           final Collection<String> startingPoints,
                           final GitLogFilters filters,
                           final LoadGrowthController loadGrowthController) {
    if (myPreviousAlgorithm != null) {
      myPreviousAlgorithm.stop();
    }
    final List<LoaderAndRefresher<CommitHashPlusParents>> list = new ArrayList<LoaderAndRefresher<CommitHashPlusParents>>();
    final List<ByRootLoader> shortLoaders = new ArrayList<ByRootLoader>();
    final List<VirtualFile> roots = rootsHolder.getRoots();
    int i = 0;
    for (VirtualFile root : roots) {
      final LoaderAndRefresherImpl.MyRootHolder rootHolder = roots.size() == 1 ?
        new LoaderAndRefresherImpl.OneRootHolder(root) :
        new LoaderAndRefresherImpl.ManyCaseHolder(i, rootsHolder);

      final boolean haveStructureFilter = filters.haveStructureFilter();
      // check if no files under root are selected
      if (haveStructureFilter && ! filters.haveStructuresForRoot(root)) {
        ++ i;
        continue;
      }
      filters.callConsumer(new Consumer<List<ChangesFilter.Filter>>() {
        @Override
        public void consume(final List<ChangesFilter.Filter> filters) {
          final LoaderAndRefresherImpl loaderAndRefresher =
          new LoaderAndRefresherImpl(ticket, filters, myMediator, startingPoints, myDetailsCache, myProject, rootHolder, myUsersIndex,
                                     loadGrowthController.getId(), haveStructureFilter);
          list.add(loaderAndRefresher);
        }
      }, true, root);

      shortLoaders.add(new ByRootLoader(myProject, rootHolder, myMediator, myDetailsCache, ticket, myUsersIndex, filters, startingPoints));
      ++ i;
    }

    myUsersComponent.acceptUpdate(myUsersIndex.getKeys());

    if (myPreviousAlgorithm != null) {
      final Continuation oldContinuation = myPreviousAlgorithm.getContinuation();
      oldContinuation.cancelCurrent();
      oldContinuation.clearQueue();
    }

    final Continuation continuation = Continuation.createFragmented(myProject, true);
    continuation.add(Arrays.<TaskDescriptor>asList(new RepositoriesSorter(list, shortLoaders, continuation)));
    continuation.resume();
  }
  
  private class RepositoriesSorter extends TaskDescriptor {
    private final List<LoaderAndRefresher<CommitHashPlusParents>> mySimpleLoaders;
    private final List<ByRootLoader> myShortLoaders;
    private final Continuation myContinuation;

    private RepositoriesSorter(final List<LoaderAndRefresher<CommitHashPlusParents>> simpleLoaders, final List<ByRootLoader> shortLoaders,
                               final Continuation continuation) {
      super("Order repositories", Where.POOLED);
      mySimpleLoaders = simpleLoaders;
      myShortLoaders = shortLoaders;
      myContinuation = continuation;
    }

    @Override
    public void run(ContinuationContext context) {
      for (ByRootLoader shortLoader : myShortLoaders) {
        final VirtualFile root = shortLoader.getRootHolder().getRoot();
        try {
          final GitRepository repositoryForRoot = GitRepositoryManager.getInstance(myProject).getRepositoryForRoot(root);
          if (repositoryForRoot == null) continue;
          final String currentRevisionName = repositoryForRoot.getCurrentRevision();
          final Pair<String, Long> pair = mySortOrder.get(root);
          if (pair != null && pair.getFirst() != null && pair.getFirst().equals(currentRevisionName)) {
            continue;
          }
          final VcsRevisionNumber currentRevision = GitHistoryUtils.getCurrentRevision(myProject, new FilePathImpl(root), null);
          if (currentRevision != null) {
            mySortOrder.put(root,
                            new Pair<String, Long>(currentRevisionName, ((GitRevisionNumber)currentRevision).getTimestamp().getTime()));
            continue;
          }
          mySortOrder.put(root, new Pair<String, Long>(currentRevisionName, Long.MAX_VALUE));
        }
        catch (VcsException e) {
          LOG.info(e);
        }
      }
      Collections.sort(myShortLoaders, new Comparator<ByRootLoader>() {
        @Override
        public int compare(ByRootLoader rl1, ByRootLoader rl2) {
          final Pair<String, Long> pair1 = mySortOrder.get(rl1.getRootHolder().getRoot());
          final Pair<String, Long> pair2 = mySortOrder.get(rl2.getRootHolder().getRoot());
          final Long l1 = pair1 == null ? null : pair1.getSecond();
          final Long l2 = pair2 == null ? null : pair2.getSecond();
          return Comparing.compare(l2, l1);
        }
      });
      Collections.sort(mySimpleLoaders, new Comparator<LoaderAndRefresher<CommitHashPlusParents>>() {
        @Override
        public int compare(LoaderAndRefresher<CommitHashPlusParents> lr1, LoaderAndRefresher<CommitHashPlusParents> lr2) {
          final Pair<String, Long> pair1 = mySortOrder.get(lr1.getRoot());
          final Pair<String, Long> pair2 = mySortOrder.get(lr2.getRoot());
          final Long l1 = pair1 == null ? null : pair1.getSecond();
          final Long l2 = pair2 == null ? null : pair2.getSecond();
          return Comparing.compare(l2, l1);
        }
      });

      myPreviousAlgorithm = new LoadAlgorithm(myProject, mySimpleLoaders, myShortLoaders, myContinuation, myGitCommitsSequentially);
      myPreviousAlgorithm.fillContinuation();
    }
  }

  @Override
  public void resume() {
    assert myPreviousAlgorithm != null;
    myPreviousAlgorithm.resume();
  }

  private List<String> filterNumbers(final String[] s) {
    final List<String> result = new ArrayList<String>();
    for (String part : s) {
      if (s.length > 40) continue;
      final AbstractHash abstractHash = AbstractHash.createStrict(part);
      if (abstractHash != null) result.add(part);
    }
    return result;
  }
}
