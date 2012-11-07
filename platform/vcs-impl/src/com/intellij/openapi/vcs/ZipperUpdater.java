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
package com.intellij.openapi.vcs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.SomeQueue;
import com.intellij.util.Alarm;
import com.intellij.vcsUtil.VcsUtil;

@SomeQueue
public class ZipperUpdater {
  private final Alarm myAlarm;
  private boolean myRaised;
  private final Object myLock = new Object();
  private final int myDelay;
  private boolean myIsEmpty;

  public ZipperUpdater(final int delay, Disposable parentDisposable) {
    myDelay = delay;
    myIsEmpty = true;
    myAlarm = new Alarm(VcsUtil.VCS_SHARED, parentDisposable);
  }

  public ZipperUpdater(final int delay, final Alarm.ThreadToUse threadToUse, Disposable parentDisposable) {
    myDelay = delay;
    myIsEmpty = true;
    myAlarm = new Alarm(threadToUse, parentDisposable);
  }

  public void queue(final Runnable runnable) {
    queue(runnable, false);
  }

  public void queue(final Runnable runnable, final boolean urgent) {
    synchronized (myLock) {
      if (myAlarm.isDisposed()) return;
      final boolean wasRaised = myRaised;
      myRaised = true;
      myIsEmpty = false;
      if (! wasRaised) {
        myAlarm.addRequest(new Runnable() {
          public void run() {
            synchronized (myLock) {
              if (! myRaised) return;
              myRaised = false;
            }
            runnable.run();
            synchronized (myLock) {
              myIsEmpty = ! myRaised;
            }
          }
        }, urgent ? 0 : myDelay);
      }
    }
  }

  public boolean isEmpty() {
    synchronized (myLock) {
      return myIsEmpty;
    }
  }

  public void stop() {
    myAlarm.cancelAllRequests();
  }
}
