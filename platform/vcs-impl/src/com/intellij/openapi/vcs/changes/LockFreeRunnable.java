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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;

/**
 * @author irengrig
 */
public class LockFreeRunnable implements Runnable {
  private final Runnable myDelegate;

  private LockFreeRunnable(Runnable delegate) {
    myDelegate = delegate;
  }

  @Override
  public void run() {
    final ApplicationEx application = (ApplicationEx) ApplicationManager.getApplication();
    if (application.isWriteAccessAllowed() || application.holdsReadLock()) {
      application.executeOnPooledThread(myDelegate);
    } else {
      myDelegate.run();
    }
  }

  public static Runnable wrap(final Runnable runnable) {
    return new LockFreeRunnable(runnable);
  }
}
