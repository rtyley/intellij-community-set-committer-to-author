package com.intellij.util.indexing;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.ide.startup.FileContentQueue;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 22, 2009
 */
public class BackgroundCacheUpdaterRunner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.BackgroundCacheUpdaterRunner");
  private static final Key<Boolean> DONT_INDEX_AGAIN_KEY = Key.create("DONT_INDEX_AGAIN_KEY");
  private final Map<Project, Collection<VirtualFile>> myProjectToFileMap;  // todo: --> Map<Project, VirtualFile>

  public BackgroundCacheUpdaterRunner(Project project, Collection<VirtualFile> files) {
    myProjectToFileMap = Collections.singletonMap(project, files);
  }

  public BackgroundCacheUpdaterRunner(Collection<VirtualFile> files) {
    myProjectToFileMap = new HashMap<Project, Collection<VirtualFile>>();
    for (VirtualFile file : files) {
      final Project project = ProjectUtil.guessProjectForFile(file);
      Collection<VirtualFile> list = myProjectToFileMap.get(project);
      if (list == null) {
        myProjectToFileMap.put(project, list = new ArrayList<VirtualFile>());
      }
      list.add(file);
    }
  }

  public void processFiles(final CacheUpdater updater) {
    for (Map.Entry<Project, Collection<VirtualFile>> entry : myProjectToFileMap.entrySet()) {
      final Project project = entry.getKey();
      final Collection<VirtualFile> files = entry.getValue();

      final Consumer<ProgressIndicator> action = new Consumer<ProgressIndicator>() {
        public void consume(final ProgressIndicator indicator) {
          final MyFileContentQueue queue = new MyFileContentQueue();

          try {
            final double count = files.size();
            queue.queue(files, indicator);

            final Consumer<VirtualFile> uiUpdater = new Consumer<VirtualFile>() {
              final Set<VirtualFile> processed = new THashSet<VirtualFile>();

              public void consume(VirtualFile virtualFile) {
                indicator.checkCanceled();
                indicator.setFraction(processed.size() / count);
                processed.add(virtualFile);
                indicator.setText2(virtualFile.getPresentableUrl());
              }
            };

            while (project == null || !project.isDisposed()) {
              indicator.checkCanceled();
              if (runWhileUserInactive(project, queue, uiUpdater, updater)) {
                break;
              }
            }
            if (project != null && project.isDisposed()) {
              indicator.cancel();
            }
          }
          finally {
            queue.clear();
            updater.updatingDone();
          }
        }
      };
      if (project != null) {
        DumbServiceImpl.getInstance(project).queueIndexUpdate(action);
      }
      else {
        final ProgressIndicator currentIndicator = ProgressManager.getInstance().getProgressIndicator();
        action.consume(currentIndicator != null? currentIndicator : new EmptyProgressIndicator());
      }
    }
  }

  private boolean runWhileUserInactive(final Project project, final MyFileContentQueue queue, final Consumer<VirtualFile> updateUi, final CacheUpdater updater) {
    final ProgressIndicatorBase innerIndicator = new ProgressIndicatorBase();
    final ApplicationAdapter canceller = new ApplicationAdapter() {
      @Override
      public void beforeWriteActionStart(Object action) {
        innerIndicator.cancel();
      }
    };

    final Ref<Boolean> finished = Ref.create(Boolean.FALSE);
    ProgressManager.getInstance().runProcess(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().addApplicationListener(canceller);
        try {
          while (true) {
            if (project != null && project.isDisposed()) return;

            final com.intellij.ide.startup.FileContent fileContent = queue.take();
            if (fileContent == null) {
              finished.set(Boolean.TRUE);
              return;
            }

            final VirtualFile file = fileContent.getVirtualFile();
            if (file == null) {
              finished.set(Boolean.TRUE);
              return;
            }

            try {
              ApplicationManager.getApplication().runReadAction(new Runnable() {
                public void run() {
                  innerIndicator.checkCanceled();

                  if (!file.isValid()) {
                    return;
                  }

                  if (project != null && project.isDisposed()) {
                    return;
                  }

                  updateUi.consume(file);

                  if (project != null) {
                    fileContent.putUserData(FileBasedIndex.PROJECT, project);
                  }
                  updater.processFile(fileContent);

                  innerIndicator.checkCanceled();
                }
              });
            }
            catch (ProcessCanceledException e) {
              queue.pushback(fileContent);
              return;
            }
            catch (Throwable e) {
              LOG.error("Error while indexing " + file.getPresentableUrl() + "\n" + "To reindex this file IDEA has to be restarted", e);
              file.putUserData(DONT_INDEX_AGAIN_KEY, Boolean.TRUE);
            }
          }
        }
        finally {
          ApplicationManager.getApplication().removeApplicationListener(canceller);
        }
      }
    }, innerIndicator);

    return finished.get().booleanValue();
  }

  private static class MyFileContentQueue extends FileContentQueue {
    @Nullable private FileContent myBuffer;

    @Override
    protected void addLast(VirtualFile file) throws InterruptedException {
      IndexingStamp.flushCache();
      super.addLast(file);
    }

    @Override
    public FileContent take() {
      final FileContent buffer = myBuffer;
      if (buffer != null) {
        myBuffer = null;
        return buffer;
      }

      return super.take();
    }

    public void pushback(@NotNull FileContent content) {
      myBuffer = content;
    }

  }

}
