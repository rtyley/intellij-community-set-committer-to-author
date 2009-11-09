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
package com.intellij.openapi.project;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.caches.CacheUpdater;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.BalloonHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.util.containers.Queue;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class DumbServiceImpl extends DumbService {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.project.DumbServiceImpl");
  private volatile boolean myDumb = false;
  private final DumbModeListener myPublisher;
  private final Queue<IndexUpdateRunnable> myUpdatesQueue = new Queue<IndexUpdateRunnable>(5);
  private final Queue<Runnable> myRunWhenSmartQueue = new Queue<Runnable>(5);
  private final Project myProject;

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass"})
  public static DumbServiceImpl getInstance(@NotNull Project project) {
    return (DumbServiceImpl)DumbService.getInstance(project);
  }

  public DumbServiceImpl(Project project, MessageBus bus) {
    myProject = project;
    myPublisher = bus.syncPublisher(DUMB_MODE);
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  public boolean isDumb() {
    return myDumb;
  }

  @Override
  public void runWhenSmart(Runnable runnable) {
    if (!isDumb()) {
      runnable.run();
    }
    else {
      synchronized (myRunWhenSmartQueue) {
        myRunWhenSmartQueue.addLast(runnable);
      }
    }
  }

  public void queueCacheUpdate(Collection<CacheUpdater> updaters) {
    // prevent concurrent modifications
    final CacheUpdateRunner runner = new CacheUpdateRunner(myProject, new ArrayList<CacheUpdater>(updaters));

    final Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread() && !myDumb && application.isWriteAccessAllowed()) {
      ProgressIndicator indicator = new EmptyProgressIndicator();
      final int size = runner.queryNeededFiles(indicator);
      if (size < 50) {
        // if not that many files found, process them on the spot, avoiding entering dumb mode
        if (size > 0) {
          runner.processFiles(indicator, false);
        }
        runner.updatingDone();
        return;
      }
    }


    final IndexUpdateRunnable updateRunnable = new IndexUpdateRunnable(runner);

    invokeOnEDT(new DumbAwareRunnable() {
      public void run() {
        if (myProject.isDisposed()) {
          return;
        }
        // ok to test and set the flag like this, because the change is always done from dispatch thread
        final boolean wasDumb = myDumb;
        if (!wasDumb) {
          // always change dumb status inside write action.
          // This will ensure all active read actions are completed before the app goes dumb
          application.runWriteAction(new Runnable() {
            public void run() {
              myDumb = true;
              myPublisher.enteredDumbMode();

              updateRunnable.run();
            }
          });
        }
        else {
          myUpdatesQueue.addLast(updateRunnable);
        }
      }
    });
  }

  private static void invokeOnEDT(DumbAwareRunnable runnable) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      runnable.run();
    }
    else {
      SwingUtilities.invokeLater(runnable);
    }
  }

  private void updateFinished() {
    myDumb = false;
    myPublisher.exitDumbMode();
    while (true) {
      final Runnable runnable;
      synchronized (myRunWhenSmartQueue) {
        if (myRunWhenSmartQueue.isEmpty()) {
          break;
        }
        runnable = myRunWhenSmartQueue.pullFirst();
      }
      if (myProject.isDisposed()) {
        return;
      }
      runnable.run();
    }
  }

  @Override
  public BalloonHandler showDumbModeNotification(final String message) {
    if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return new BalloonHandler() {
        public void hide() {
        }
      };
    }

    StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getIdeFrame(myProject).getStatusBar();
    HyperlinkListener listener = new HyperlinkListener() {
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;

        Messages.showMessageDialog("<html>" +
                                   ApplicationNamesInfo.getInstance().getFullProductName() +
                                   " is now indexing project sources and libraries to enable advanced features <br>" +
                                   "(refactorings, navigation, usage search, code analysis, formatting, etc.)<br>" +
                                   "During this process you can use code editor and VCS integrations,<br>" +
                                   "and adjust IDE and Run Configurations settings." +
                                   "</html>", "Don't panic!", null);
      }
    };
    return statusBar.notifyProgressByBalloon(MessageType.WARNING, message, null, listener);
  }

  private static final Ref<CacheUpdateRunner> NULL_ACTION = new Ref<CacheUpdateRunner>(null);
  
  private class IndexUpdateRunnable implements Runnable {
    private final CacheUpdateRunner myAction;
    private double myProcessedItems;
    private volatile int myTotalItems;
    private double myCurrentBaseTotal;

    public IndexUpdateRunnable(CacheUpdateRunner action) {
      myAction = action;
      myTotalItems = 0;
      myCurrentBaseTotal = 0;
    }

    public void run() {
      if (myProject.isDisposed()) {
        return;
      }

      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, IdeBundle.message("progress.indexing"), false) {

        private final ArrayBlockingQueue<Ref<CacheUpdateRunner>> myActionQueue = new ArrayBlockingQueue<Ref<CacheUpdateRunner>>(1);

        @Override
        public void run(@NotNull final ProgressIndicator indicator) {
          final ProgressIndicator proxy =
            (ProgressIndicator)Proxy.newProxyInstance(indicator.getClass().getClassLoader(), new Class[]{ProgressIndicator.class}, new InvocationHandler() {
              public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("setFraction".equals(method.getName())) {
                  final double fraction = (Double)args[0];
                  args[0] = new Double((myProcessedItems + fraction * myCurrentBaseTotal) / myTotalItems);
                }
                try {
                  return method.invoke(indicator, args);
                }
                catch (InvocationTargetException e) {
                  final Throwable cause = e.getCause();
                  if (cause instanceof ProcessCanceledException) {
                    throw cause;
                  }
                  throw e;
                }
              }
            });
          runAction(proxy, myAction);
        }

        private void runAction(ProgressIndicator indicator, CacheUpdateRunner updateRunner) {
          do {
            int count = 0;
            try {
              indicator.setIndeterminate(true);
              indicator.setText(IdeBundle.message("progress.indexing.scanning"));
              count = updateRunner.queryNeededFiles(indicator);

              myCurrentBaseTotal = count;
              myTotalItems += count;

              indicator.setIndeterminate(false);
              indicator.setText(IdeBundle.message("progress.indexing.updaing"));
              updateRunner.processFiles(indicator, true);
            }
            finally {
              updateRunner.updatingDone();
              myProcessedItems += count;
              invokeOnEDT(new DumbAwareRunnable() {
                public void run() {
                  if (myUpdatesQueue.isEmpty()) {
                    // really terminate the tesk
                    myActionQueue.offer(NULL_ACTION);
                    updateFinished();
                  }
                  else {
                    //run next dumb action
                    final IndexUpdateRunnable nextUpdateRunnable = myUpdatesQueue.pullFirst();
                    // run next action under already existing progress indicator
                    if (!myActionQueue.offer(new Ref<CacheUpdateRunner>(nextUpdateRunnable.myAction))) {
                      LOG.assertTrue(false, "Action queue rejected next updateRunnable!");
                      nextUpdateRunnable.run();
                    }
                  }
                }
              });

              // try to obtain the next action or terminate if no actions left
              try {
                Ref<CacheUpdateRunner> ref;
                do {
                  ref = myActionQueue.poll(500, TimeUnit.MILLISECONDS);
                  updateRunner = ref != null? ref.get() : null;
                  if (myProject.isDisposed()) {
                    // just terminate the progress task
                    break;
                  }
                }
                while (ref == null);
              }
              catch (InterruptedException ignored) {
                LOG.info(ignored);
                break;
              }
            }
          }
          while (updateRunner != null);
          // make it impossible to add actions to the queue anymore
          myActionQueue.offer(NULL_ACTION);
        }

      });
    }
  }

}
