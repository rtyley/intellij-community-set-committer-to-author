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
package com.intellij.openapi.wm.ex;

import com.intellij.notification.impl.IdeNotificationArea;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.BalloonHandler;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.impl.status.StatusBarPatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;

public interface StatusBarEx extends StatusBar {
  String getInfo();

  void clear();

  void addFileStatusComponent(StatusBarPatch component);

  void removeFileStatusComponent(StatusBarPatch component);

  void cleanupCustomComponents();

  void add(ProgressIndicatorEx indicator, TaskInfo info);

  boolean isProcessWindowOpen();

  void setProcessWindowOpen(boolean open);

  BalloonHandler notifyProgressByBalloon(@NotNull MessageType type, @NotNull String htmlBody);

  BalloonHandler notifyProgressByBalloon(@NotNull MessageType type, @NotNull String htmlBody, @Nullable Icon icon, @Nullable HyperlinkListener listener);

  void update(Editor editor);

  void dispose();

  IdeNotificationArea getNotificationArea();
}
