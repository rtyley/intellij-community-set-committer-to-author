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
package com.intellij.notification.impl.ui;

import com.intellij.concurrency.JobScheduler;
import com.intellij.notification.Notification;
import com.intellij.notification.impl.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

/**
 * @author spleaner
 */
public class NotificationComponent extends JLabel implements NotificationModelListener {
  private static final Icon EMPTY_ICON = IconLoader.getIcon("/ide/notifications.png");
  private static final Icon READ_ICON = IconLoader.getIcon("/ide/read_notifications.png");
  private static final Icon UNREAD_ICON = IconLoader.getIcon("/ide/unread_notifications.png");

  private WeakReference<JBPopup> myPopupRef;

  private BlinkIconWrapper myCurrentIcon;
  private boolean myBlinkIcon = true;
  private IdeNotificationArea myArea;

  public NotificationComponent(@NotNull final IdeNotificationArea area) {
    myArea = area;

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(final MouseEvent e) {
        if (UIUtil.isActionClick(e)) {
          showList();
        }
      }
    });

    myCurrentIcon = new BlinkIconWrapper(EMPTY_ICON, false);
    setIcon(myCurrentIcon);

    JobScheduler.getScheduler().scheduleAtFixedRate(new IconBlinker(), (long)1, (long)1, TimeUnit.SECONDS);
  }

  private static NotificationsManagerImpl getManager() {
    return NotificationsManagerImpl.getNotificationsManagerImpl();
  }

  @Override
  public void removeNotify() {
    getManager().removeListener(this); // clean up

    cancelPopup();
    super.removeNotify();
  }

  private void cancelPopup() {
    if (myPopupRef != null) {
      final JBPopup popup = myPopupRef.get();
      if (popup != null) {
        popup.cancel();
      }

      myPopupRef = null;
    }
  }

  public void addNotify() {
    super.addNotify();

    getManager().addListener(this);
  }

  private void showList() {
    if (myPopupRef != null) {
      final JBPopup popup = myPopupRef.get();
      if (popup != null && !popup.isVisible()) {
        myPopupRef = null;
      }
      else if (popup == null) myPopupRef = null;
    }

    if (myPopupRef == null) {
      myPopupRef = new WeakReference<JBPopup>(NotificationsListPanel.show(getProject(), this));
    }
  }

  public void updateStatus() {
    final NotificationsManagerImpl manager = getManager();

    Icon icon = EMPTY_ICON;
    if (manager.hasUnread(getProject())) {
      icon = UNREAD_ICON;
    }
    else if (manager.hasRead(getProject())) {
      icon = READ_ICON;
    }

    myCurrentIcon = new BlinkIconWrapper(icon, false);
    setIcon(myCurrentIcon);

    repaint();
  }

  @Nullable
  public Project getProject() {
    return myArea.getProject();
  }

  @SuppressWarnings({"SSBasedInspection"})
  public void notificationsAdded(@NotNull final Notification... notifications) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        updateStatus();
      }
    });
  }

  public void notificationsRead(@NotNull Notification... notification) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        updateStatus();
      }
    });
  }

  @SuppressWarnings({"SSBasedInspection"})
  public void notificationsRemoved(@NotNull final Notification... notifications) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        updateStatus();
      }
    });
  }

  private void blinkIcon(boolean visible) {
    myCurrentIcon.setPaint(visible);
  }

  private class IconBlinker implements Runnable {
    private boolean myVisible;

    public void run() {
      myVisible = !myVisible;
      blinkIcon(myVisible);
    }
  }

  private class BlinkIconWrapper implements Icon {
    private Icon myOriginal;
    private boolean myPaint;
    private boolean myBlink;
    private BufferedImage myGrayscaleImage;

    private BlinkIconWrapper(@NotNull final Icon original, final boolean blink) {
      myOriginal = original;
      myBlink = blink;
    }

    public void setPaint(final boolean paint) {
      if (paint != myPaint) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            repaint();
          }
        });
      }

      myPaint = paint;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
      if (!myBlinkIcon) {
        if (myGrayscaleImage == null) {
          myGrayscaleImage = new BufferedImage(myOriginal.getIconWidth(), myOriginal.getIconHeight(), BufferedImage.TYPE_BYTE_GRAY);
          final Graphics2D gi = myGrayscaleImage.createGraphics();

          gi.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));

          myOriginal.paintIcon(c, gi, 0, 0);
          gi.dispose();
        }

        g.drawImage(myGrayscaleImage, x, y, null);
      }
      else if (!myBlink || !myPaint) {
        myOriginal.paintIcon(c, g, x, y);
      }
    }

    public int getIconWidth() {
      return myOriginal.getIconWidth();
    }

    public int getIconHeight() {
      return myOriginal.getIconHeight();
    }
  }
}
