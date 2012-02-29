/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kirill Likhodedov
 */
  public class Notificator {

  @NotNull private final Project myProject;

  public Notificator(@NotNull Project project) {
    myProject = project;
  }

  public void notify(@NotNull NotificationGroup notificationGroup, @NotNull String title, @NotNull String message,
                     @NotNull NotificationType type, @Nullable NotificationListener listener) {
    // title can be empty; description can't be neither null, nor empty
    if (StringUtil.isEmptyOrSpaces(message)) {
      message = title;
      title = "";
    }
    // if both title and description were empty, then it is a problem in the calling code => Notifications engine assertion will notify.
    createNotification(notificationGroup, title, message, type, listener).notify(myProject);
  }

  public static Notificator getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, Notificator.class);
  }

  public void notify(@NotNull NotificationGroup notificationGroup, @NotNull String title, @NotNull String message, @NotNull NotificationType type) {
    notify(notificationGroup, title, message, type, null);
  }

  public void notifyError(String title, String message) {
    notify(GitVcs.IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.ERROR, null);
  }

  public void notifySuccess(@NotNull String title, @NotNull String message) {
    notify(GitVcs.NOTIFICATION_GROUP_ID, title, message, NotificationType.INFORMATION,  null);
  }

  protected static Notification createNotification(NotificationGroup notificationGroup, String title, String message,
                                                   NotificationType type, NotificationListener listener) {
    return notificationGroup.createNotification(title, message, type, listener);
  }

}
