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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.progress.util.ProgressIndicatorBase;

/**
 * @author cdr
 */
public class DaemonProgressIndicator extends ProgressIndicatorBase {
  public DaemonProgressIndicator() {
  }

  public synchronized void stop() {
    super.stop();
    cancel();
  }

  public synchronized void stopIfRunning() {
    if (isRunning()) {
      stop();
    }
    else {
      cancel();
    }
  }

  public boolean waitFor(int millisTimeout) {
    synchronized (this) {
      try {
        // we count on ProgressManagerImpl doing progress.notifyAll() on finish
        wait(millisTimeout);
      }
      catch (InterruptedException ignored) {
      }
    }
    return isCanceled();
  }
}
