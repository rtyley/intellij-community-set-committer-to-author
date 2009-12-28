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
package com.intellij.openapi.progress.util;

import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.util.LinkedList;

/**
 * @author lex
 */
public class ProgressWindowWithNotification extends ProgressWindow {
  private final LinkedList<ProgressIndicatorListener> myListeners = new LinkedList<ProgressIndicatorListener>();

  public ProgressWindowWithNotification(boolean shouldShowCancel, Project project) {
    super(shouldShowCancel, project);
  }

  public ProgressWindowWithNotification(boolean shouldShowCancel, boolean shouldShowBackground, Project project) {
    super(shouldShowCancel, shouldShowBackground, project);
  }

  public ProgressWindowWithNotification(boolean shouldShowCancel, boolean shouldShowBackground, Project project, String cancelText) {
    super(shouldShowCancel, shouldShowBackground, project, cancelText);
  }

  public ProgressWindowWithNotification(boolean shouldShowCancel, boolean shouldShowBackground, Project project, JComponent parentComponent, String cancelText) {
    super(shouldShowCancel, shouldShowBackground, project, parentComponent, cancelText);
  }

  public void cancel() {
    super.cancel();
    for (final ProgressIndicatorListener progressIndicatorListener : myListeners) {
      progressIndicatorListener.cancelled();
    }
  }

  public synchronized void stop() {
    for (final ProgressIndicatorListener progressIndicatorListener : myListeners) {
      progressIndicatorListener.stopped();
    }
    super.stop();
  }

  public void addListener(ProgressIndicatorListener listener) {
    myListeners.addFirst(listener);
  }

  public void removeListener(ProgressIndicatorListener listener) {
    myListeners.remove(listener);
  }
}
