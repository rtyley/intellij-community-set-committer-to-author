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

package com.intellij.notification;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.notification.impl.NotificationsConfigurable;
import com.intellij.notification.impl.NotificationsConfiguration;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author peter
 */
public class EventLog implements Notifications {
  private final List<Notification> myNotifications = new CopyOnWriteArrayList<Notification>();

  public EventLog() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(Notifications.TOPIC, this);
  }

  @Override
  public void notify(@NotNull Notification notification) {
    final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length == 0) {
      addGlobalNotifications(notification);
      NotificationsManagerImpl.getNotificationsManagerImpl().setStatusMessage(null, notification);
    }
    for (Project p : openProjects) {
      printNotification(getProjectComponent(p).myConsoleView, p, notification);
    }
  }

  private void addGlobalNotifications(Notification notification) {
    synchronized (myNotifications) {
      myNotifications.add(notification);
    }
  }

  private List<Notification> takeNotifications() {
    synchronized (myNotifications) {
      final ArrayList<Notification> result = new ArrayList<Notification>(myNotifications);
      myNotifications.clear();
      return result;
    }
  }

  private static void printNotification(ConsoleViewImpl view, Project project, final Notification notification) {
    if (!NotificationsConfiguration.getSettings(notification.getGroupId()).isShouldLog()) {
      return;
    }

    view.print(DateFormat.getTimeInstance(DateFormat.MEDIUM).format(notification.getCreationTime()) + " ", ConsoleViewContentType.NORMAL_OUTPUT);

    Pair<String, Boolean> pair = NotificationsManagerImpl.formatForLog(notification);


    final NotificationType type = notification.getType();
    view.print(pair.first, type == NotificationType.ERROR
                         ? ConsoleViewContentType.ERROR_OUTPUT
                         : type == NotificationType.INFORMATION
                           ? ConsoleViewContentType.NORMAL_OUTPUT
                           : ConsoleViewContentType.WARNING_OUTPUT);
    if (pair.second) {
      view.print(" ", ConsoleViewContentType.NORMAL_OUTPUT);
      view.printHyperlink("more", new HyperlinkInfo() {
        @Override
        public void navigate(Project project) {
          Balloon balloon = notification.getBalloon();
          if (balloon != null) {
            balloon.hide();
          }

          NotificationsManagerImpl.notifyByBalloon(notification, NotificationDisplayType.STICKY_BALLOON, project);
        }
      });
      view.print(" ", ConsoleViewContentType.NORMAL_OUTPUT);
    }
    view.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);

    NotificationsManagerImpl.getNotificationsManagerImpl().setStatusMessage(project, notification);
  }

  private static EventLog getApplicationComponent() {
    return ApplicationManager.getApplication().getComponent(EventLog.class);
  }

  @Override
  public void register(@NotNull String groupDisplayType, @NotNull NotificationDisplayType defaultDisplayType) {
  }

  public static class ProjectTracker extends AbstractProjectComponent {
    private final ConsoleViewImpl myConsoleView;

    public ProjectTracker(final Project project) {
      super(project);
      myConsoleView = new ConsoleViewImpl(project, true);
      for (Notification notification : getApplicationComponent().takeNotifications()) {
        printNotification(myConsoleView, project, notification);
      }

      project.getMessageBus().connect(project).subscribe(Notifications.TOPIC, new Notifications() {
        @Override
        public void notify(@NotNull Notification notification) {
          printNotification(myConsoleView, project, notification);
        }

        @Override
        public void register(@NotNull String groupDisplayType, @NotNull NotificationDisplayType defaultDisplayType) {
        }
      });
    }

    @Override
    public void projectClosed() {
      NotificationsManagerImpl.getNotificationsManagerImpl().setStatusMessage(null, null);
    }
  }

  public static ProjectTracker getProjectComponent(Project project) {
    return project.getComponent(ProjectTracker.class);
  }
  public static class FactoryItself implements ToolWindowFactory, DumbAware {
    public void createToolWindowContent(final Project project, ToolWindow toolWindow) {
      final ProjectTracker tracker = getProjectComponent(project);

      SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);
      panel.setContent(tracker.myConsoleView.getComponent());

      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new DumbAwareAction("Settings", "Edit notification settings", IconLoader.getIcon("/general/secondaryGroup.png")) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          ShowSettingsUtil.getInstance().editConfigurable(project, new NotificationsConfigurable());
        }
      });
      group.addAll(ContainerUtil.subList(Arrays.asList(tracker.myConsoleView.createConsoleActions()), 2)); // no next/prev
      ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, false);
      toolbar.setTargetComponent(panel);
      panel.setToolbar(toolbar.getComponent());

      final Content content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false);
      toolWindow.getContentManager().addContent(content);
    }

  }


}
