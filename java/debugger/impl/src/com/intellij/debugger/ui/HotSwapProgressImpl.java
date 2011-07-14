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
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.HotSwapProgress;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.ui.MessageCategory;
import gnu.trove.TIntObjectHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HotSwapProgressImpl extends HotSwapProgress{
  static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup("HotSwap", ToolWindowId.DEBUG, true);

  TIntObjectHashMap<List<String>> myMessages = new TIntObjectHashMap<List<String>>();
  private final ProgressIndicator myProgressIndicator;
  private final ProgressWindow myProgressWindow;
  private String myTitle = DebuggerBundle.message("progress.hot.swap.title");

  public HotSwapProgressImpl(Project project) {
    super(project);
    myProgressWindow = new BackgroundableProcessIndicator(getProject(), myTitle, new PerformInBackgroundOption() {
      public boolean shouldStartInBackground() {
        return DebuggerSettings.getInstance().HOTSWAP_IN_BACKGROUND;
      }

      public void processSentToBackground() {
      }

    }, null, null, true) {
      public void cancel() {
        HotSwapProgressImpl.this.cancel();
        super.cancel();
      }
    };
    myProgressIndicator = myProgressWindow;
  }

  public void finished() {
    super.finished();

    final List<String> errors = getMessages(MessageCategory.ERROR);
    final List<String> warnings = getMessages(MessageCategory.WARNING);
    if (errors.size() > 0) {
      NOTIFICATION_GROUP.createNotification(DebuggerBundle.message("status.hot.swap.completed.with.errors"), buildMessage(errors),
                                                              NotificationType.ERROR, null).notify(getProject());
    }
    else if (warnings.size() > 0){
      NOTIFICATION_GROUP.createNotification(DebuggerBundle.message("status.hot.swap.completed.with.warnings"),
                                            buildMessage(warnings), NotificationType.WARNING, null).notify(getProject());
    }
    else if (myMessages.size() > 0){
      List<String> messages = new ArrayList<String>();
      for (int category : myMessages.keys()) {
        messages.addAll(getMessages(category));
      }
      NOTIFICATION_GROUP.createNotification(buildMessage(messages), NotificationType.INFORMATION).notify(getProject());
    }
  }

  private List<String> getMessages(int category) {
    final List<String> messages = myMessages.get(category);
    return messages == null? Collections.<String>emptyList() : messages;
  }
    
  private static String buildMessage(List<String> messages) {
    return StringUtil.trimEnd(StringUtil.join(messages, " \n").trim(), ";");
  }
  
  public void addMessage(DebuggerSession session, final int type, final String text) {
    List<String> messages = myMessages.get(type);
    if (messages == null) {
      messages = new ArrayList<String>();
      myMessages.put(type, messages);
    }
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(session.getSessionName()).append(": ").append(text).append(";");
      messages.add(builder.toString());
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public void setText(final String text) {
    DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
      public void run() {
        myProgressIndicator.setText(text);
      }
    }, myProgressIndicator.getModalityState());

  }

  public void setTitle(final String text) {
    DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
      public void run() {
        myProgressWindow.setTitle(text);
      }
    }, myProgressWindow.getModalityState());

  }

  public void setFraction(final double v) {
    DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
      public void run() {
        myProgressIndicator.setFraction(v);
      }
    }, myProgressIndicator.getModalityState());
  }

  public boolean isCancelled() {
    return myProgressIndicator.isCanceled();
  }

  public ProgressIndicator getProgressIndicator() {
     return myProgressIndicator;
  }

  public void setDebuggerSession(DebuggerSession session) {
    myTitle = DebuggerBundle.message("progress.hot.swap.title") + " : " + session.getSessionName();
    myProgressWindow.setTitle(myTitle);
  }
}
