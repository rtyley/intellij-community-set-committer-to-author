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

package com.intellij.util.ui;

import com.intellij.openapi.Disposable;

public abstract class Timer implements Disposable {

  private Thread myThread;
  private final int mySpan;

  private volatile boolean myRunning;
  private volatile boolean myDisposed;
  private volatile boolean myRestartRequest;

  private final String myName;

  private volatile boolean myTakeInitialDelay = true;
  private volatile boolean myInitiallySlept = false;
  private volatile boolean myInterruptRequest;

  public Timer(String name, int span) {
    myName = name;
    mySpan = span;
    myThread = new Thread(name+" timer") {
      public void run() {
        try {
          while(true) {
            if (myInterruptRequest) break;

            if (myTakeInitialDelay || myInitiallySlept) {
              sleep(mySpan);
            }
            myInitiallySlept = true;

            if (myRestartRequest) {
              myRestartRequest = false;
              continue;
            }

            if (myRunning) {
              onTimer();
            }
          }
        }
        catch (InterruptedException e) {
        }
        myDisposed = true;
      }
    };
  }

  public void setTakeInitialDelay(final boolean take) {
    myTakeInitialDelay = take;
  }

  public final int getSpan() {
    return mySpan;
  }

  public final void start() {
    assert !myThread.isAlive();
    myThread.start();
  }

  protected abstract void onTimer() throws InterruptedException;

  public final void suspend() {
    if (myDisposed) return;

    if (myThread.isAlive()) {
      myRunning = false;
    }
    myInitiallySlept = false;
  }

  public final void resume() {
    startIfNeeded();
    myRunning = true;
  }

  private void startIfNeeded() {
    if (myDisposed) return;

    if (!myThread.isAlive()) {
      start();
    }
  }

  public final void dispose() {
    if (myThread != null) {
      startIfNeeded();
      myInterruptRequest = true;
      myThread.interrupt();
      myDisposed = true;
      myThread = null;
    }
  }

  public void restart() {
    startIfNeeded();
    myRestartRequest = true;
  }

  public String toString() {
    return "Timer=" + myName;
  }

  public boolean isRunning() {
    return myRunning;
  }

  public boolean isDisposed() {
    return myDisposed;
  }
}
