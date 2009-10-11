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
package com.intellij.ide.startup.impl;

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author mike
 */
public class StartupManagerImpl extends StartupManagerEx {
  private final List<Runnable> myActivities = new ArrayList<Runnable>();
  private final List<Runnable> myPostStartupActivities = Collections.synchronizedList(new ArrayList<Runnable>());
  private final List<Runnable> myPreStartupActivities = Collections.synchronizedList(new ArrayList<Runnable>());

  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.startup.impl.StartupManagerImpl");

  private volatile FileSystemSynchronizerImpl myFileSystemSynchronizer = new FileSystemSynchronizerImpl();
  private volatile boolean myStartupActivityRunning = false;
  private volatile boolean myStartupActivityPassed = false;
  private volatile boolean myPostStartupActivityPassed = false;
  private volatile boolean myBackgroundIndexing = false;

  private final Project myProject;

  public StartupManagerImpl(Project project) {
    myProject = project;
  }

  public void registerStartupActivity(Runnable runnable) {
    LOG.assertTrue(!myStartupActivityPassed, "Registering startup activity that will never be run");
    myActivities.add(runnable);
  }

  public synchronized void registerPostStartupActivity(Runnable runnable) {
    LOG.assertTrue(!myPostStartupActivityPassed, "Registering post-startup activity that will never be run");
    myPostStartupActivities.add(runnable);
  }

  public void setBackgroundIndexing(boolean backgroundIndexing) {
    myBackgroundIndexing = backgroundIndexing;
  }

  public boolean startupActivityRunning() {
    return myStartupActivityRunning;
  }

  public boolean startupActivityPassed() {
    return myStartupActivityPassed;
  }

  public boolean postStartupActivityPassed() {
    return myPostStartupActivityPassed;
  }

  public void registerPreStartupActivity(@NotNull Runnable runnable) {
    myPreStartupActivities.add(runnable);
  }

  public FileSystemSynchronizerImpl getFileSystemSynchronizer() {
    return myFileSystemSynchronizer;
  }

  public void runStartupActivities() {
    ApplicationManager.getApplication().runReadAction(
      new Runnable() {
        public void run() {
          assert !HeavyProcessLatch.INSTANCE.isRunning();
          HeavyProcessLatch.INSTANCE.processStarted();
          try {
            runActivities(myPreStartupActivities);
            if (myFileSystemSynchronizer != null || !ApplicationManager.getApplication().isUnitTestMode()) {
              myFileSystemSynchronizer.setCancelable(true);
              try {
                myFileSystemSynchronizer.executeFileUpdate();
              }
              catch (ProcessCanceledException e) {
                throw e;
              }
              catch (Throwable e) {
                LOG.error(e);
              }
              myFileSystemSynchronizer = null;
            }
            myStartupActivityRunning = true;
            runActivities(myActivities);

            myStartupActivityRunning = false;
            myStartupActivityPassed = true;
          }
          finally {
            HeavyProcessLatch.INSTANCE.processFinished();
          }
        }
      }
    );
  }

  public synchronized void runPostStartupActivities() {
    final Application app = ApplicationManager.getApplication();
    app.assertIsDispatchThread();
    if (myBackgroundIndexing || DumbService.getInstance(myProject).isDumb()) {
      final List<Runnable> dumbAware = CollectionFactory.arrayList();
      for (Iterator<Runnable> iterator = myPostStartupActivities.iterator(); iterator.hasNext();) {
        Runnable runnable = iterator.next();
        if (runnable instanceof DumbAware) {
          dumbAware.add(runnable);
          iterator.remove();
        }
      }
      runActivities(dumbAware);
      DumbService.getInstance(myProject).runWhenSmart(new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;

          runActivities(myPostStartupActivities);
          myPostStartupActivities.clear();
          myPostStartupActivityPassed = true;
        }
      });
    }
    else {
      runActivities(myPostStartupActivities);
      myPostStartupActivities.clear();
      myPostStartupActivityPassed = true;
    }

    if (app.isUnitTestMode()) return;

    VirtualFileManager.getInstance().refresh(!app.isHeadlessEnvironment());
  }

  private static void runActivities(final List<Runnable> activities) {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    while (!activities.isEmpty()) {
      final Runnable runnable = activities.remove(0);
      if (indicator != null) indicator.checkCanceled();

      try {
        runnable.run();
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable ex) {
        LOG.error(ex);
      }
    }
  }

  public void runWhenProjectIsInitialized(final Runnable action) {
    final Runnable runnable;

    final Application application = ApplicationManager.getApplication();
    if (action instanceof DumbAware) {
      runnable = new DumbAwareRunnable() {
        public void run() {
          application.runWriteAction(action);
        }
      };
    }
    else {
      runnable = new Runnable() {
        public void run() {
          application.runWriteAction(action);
        }
      };
    }

    if (myProject.isInitialized() || (application.isUnitTestMode() && myPostStartupActivityPassed)) {
      // in tests which simulate project opening, post-startup activities could have been run already. Then we should act as if the project was initialized
      if (application.isDispatchThread()) {
        runnable.run();
      }
      else {
        application.invokeLater(new Runnable() {
          public void run() {
            if (!myProject.isDisposed()) {
              runnable.run();
            }
          }
        });
      }
    }
    else {
      registerPostStartupActivity(runnable);
    }
  }

  @TestOnly
  public void prepareForNextTest() {
    myActivities.clear();
    myPostStartupActivities.clear();
    myPreStartupActivities.clear();
  }

  @TestOnly
  public void checkCleared() {
    try {
      assert myActivities.isEmpty() : "Activities: "+myActivities;
      assert myPostStartupActivities.isEmpty() : "Post Activities: "+myPostStartupActivities;
      assert myPreStartupActivities.isEmpty() : "Pre Activities: "+myPreStartupActivities;
    }
    finally {
      prepareForNextTest();
    }
  }
}
