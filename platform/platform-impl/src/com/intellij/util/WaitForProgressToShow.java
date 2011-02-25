/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;

public class WaitForProgressToShow {
  private WaitForProgressToShow() {
  }

  public static void execute(ProgressIndicator pi) {
    if (pi.isModal() && pi instanceof ProgressWindow) {
      final long maxWait = 3000;
      final long start = System.currentTimeMillis();
      while ((!((ProgressWindow)pi).isPopupWasShown()) && (pi.isRunning()) && (System.currentTimeMillis() - maxWait < start)) {
        final Object lock = new Object();
        synchronized (lock) {
          try {
            lock.wait(100);
          }
          catch (InterruptedException e) {
            //
          }
        }
      }
      ProgressManager.checkCanceled();
    }
  }
}