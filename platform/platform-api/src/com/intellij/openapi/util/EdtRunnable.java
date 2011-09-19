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
package com.intellij.openapi.util;

import com.intellij.util.ui.UIUtil;

public abstract class EdtRunnable implements ExpirableRunnable {

  private boolean myExpired;

  public final void run() {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        if (!isExpired()) {
          runEdt();
        }
      }
    });
  }

  public void expire() {
    myExpired = true;
  }

  @Override
  public boolean isExpired() {
    return myExpired;
  }

  public abstract void runEdt();
}