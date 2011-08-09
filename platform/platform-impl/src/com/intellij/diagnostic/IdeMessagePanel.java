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
package com.intellij.diagnostic;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.IconLikeCustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.LightColors;
import com.intellij.ui.popup.NotificationPopup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class IdeMessagePanel extends JPanel implements MessagePoolListener, IconLikeCustomStatusBarWidget {
  public static final String FATAL_ERROR = "FatalError";
  private final IconPane myIdeFatal;

  private final IconPane[] myIcons;
  private static final String INTERNAL_ERROR_NOTICE = DiagnosticBundle.message("error.notification.tooltip");

  private IdeErrorsDialog myDialog;
  private boolean myOpeningInProgress;
  private final MessagePool myMessagePool;
  private boolean myNotificationPopupAlreadyShown = false;
  private final Icon myIcon = IconLoader.getIcon("/ide/fatalError.png");
  private final Icon myEmptyIcon = IconLoader.getIcon("/ide/emptyFatalError.png");

  public IdeMessagePanel(MessagePool messagePool) {
    super(new BorderLayout());
    myIdeFatal = new IconPane(myIcon, myEmptyIcon,
                              DiagnosticBundle.message("error.notification.empty.text"), new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        openFatals(null);
      }
    });

    myIdeFatal.setVerticalAlignment(SwingConstants.CENTER);

    myIcons = new IconPane[]{myIdeFatal};
    add(myIdeFatal, BorderLayout.CENTER);

    myMessagePool = messagePool;
    messagePool.addListener(this);

    JobScheduler.getScheduler().scheduleAtFixedRate(new Blinker(), (long)1, (long)1, TimeUnit.SECONDS);
    updateFatalErrorsIcon();

    setOpaque(false);
  }

  @NotNull
  public String ID() {
    return FATAL_ERROR;
  }

  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  public void dispose() {
  }

  public void install(@NotNull StatusBar statusBar) {
  }

  public JComponent getComponent() {
    return this;
  }

  public void openFatals(@Nullable final LogMessage message) {
    if (myDialog != null) return;
    if (myOpeningInProgress) return;
    myOpeningInProgress = true;

    final Runnable task = new Runnable() {
      public void run() {
        if (isOtherModalWindowActive()) {
          if (myDialog == null) {
            JobScheduler.getScheduler().schedule(this, (long)300, TimeUnit.MILLISECONDS);
          }
          return;
        }

        try {
          _openFatals(message);
        }
        finally {
          myOpeningInProgress = false;
        }
      }
    };

    task.run();
  }

  private void _openFatals(@Nullable final LogMessage message) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        myDialog = new IdeErrorsDialog(myMessagePool, message) {
          public void doOKAction() {
            super.doOKAction();
            disposeDialog(this);
          }

          public void doCancelAction() {
            super.doCancelAction();
            disposeDialog(this);
          }
        };

        myMessagePool.addListener(myDialog);
        if (!isOtherModalWindowActive()) {
          myDialog.show();
        }
        else {
          myDialog.close(0);
          disposeDialog(myDialog);
        }
      }
    });
  }

  private void disposeDialog(final IdeErrorsDialog listDialog) {
    myMessagePool.removeListener(listDialog);
    updateFatalErrorsIcon();
    myDialog = null;
  }

  public void newEntryAdded() {
    updateFatalErrorsIcon();

  }

  public void poolCleared() {
    updateFatalErrorsIcon();
  }

  private boolean isOtherModalWindowActive() {
    final Window window = getActiveModalWindow();
    if (window == null) return false;

    return myDialog == null || myDialog.getWindow() != window;

  }

  private static Window getActiveModalWindow() {
    final KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    final Window activeWindow = manager.getActiveWindow();
    if (activeWindow instanceof JDialog) {
      if (((JDialog) activeWindow).isModal()) {
        return activeWindow;
      }
    }

    return null;
  }

  private void updateFatalErrorsIcon() {
    final List<AbstractMessage> errors = myMessagePool.getFatalErrors(false, false);
    if (errors.isEmpty()) {
      myNotificationPopupAlreadyShown = false;
      myIdeFatal.deactivate();
    }
    else {
      myIdeFatal.activate(INTERNAL_ERROR_NOTICE);
      if (!myNotificationPopupAlreadyShown) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            String notificationText = tryGetFromMessages(errors);
            if (notificationText == null) {
              notificationText = INTERNAL_ERROR_NOTICE;
            }
            final JLabel label = new JLabel(notificationText);
            label.setIcon(myIcon);
            new NotificationPopup(IdeMessagePanel.this, label, LightColors.RED, false, new ActionListener() {
              public void actionPerformed(ActionEvent e) {
                _openFatals(null);
              }
            }, true);
          }
        });
        myNotificationPopupAlreadyShown = true;
      }
    }
  }

  private static String tryGetFromMessages(List<AbstractMessage> messages) {
    String result = null;
    for (AbstractMessage message : messages) {
      if (message instanceof LogMessageEx) {
        if (result == null) {
          result = ((LogMessageEx)message).getNotificationText();
        }
        else if (!result.equals(((LogMessageEx)message).getNotificationText())) {
          // if texts are different, show default
          return null;
        }
      }
      else {
        return null;
      }
    }
    return result;
  }

  private class Blinker implements Runnable {
    boolean myVisible = false;

    public void run() {
      myVisible = !myVisible;
      setBlinkedIconsVisibilityTo(myVisible);
    }

    private void setBlinkedIconsVisibilityTo(boolean aVisible) {
      for (final IconPane each : myIcons) {
        each.getIconWrapper().setVisible(aVisible || !each.shouldBlink());
      }
    }
  }

  private class IconPane extends JLabel {
    private final IconWrapper myIcon;
    private final String myEmptyText;
    private boolean myIsActive;
    private final ActionListener myListener;

    public IconPane(Icon aIcon, Icon offIcon, String aEmptyText, ActionListener aListener) {
      myIcon = new IconWrapper(aIcon, offIcon);
      myEmptyText = aEmptyText;
      myListener = aListener;
      setIcon(myIcon);

      setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 1));

      addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (myIsActive) {
            myListener.actionPerformed(null);
          }
        }
      });

      deactivate();
    }

    public IconWrapper getIconWrapper() {
      return myIcon;
    }

    public void activate(String aDisplayingText) {
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      myIsActive = true;
      myIcon.setIconPainted(true);
      setToolTipText(aDisplayingText);
      repaint();
    }

    public void deactivate() {
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      myIsActive = false;
      myIcon.setIconPainted(false);
      setToolTipText(myEmptyText);
      repaint();
    }

    public boolean shouldBlink() {
      return myMessagePool.hasUnreadMessages();
    }
  }

  private class IconWrapper implements Icon {
    private final Icon myIcon;
    private boolean myEnabled;
    private boolean myShouldPaint = true;
    private Icon myOffIcon;

    public IconWrapper(Icon aIcon, Icon offIcon) {
      myIcon = aIcon;
      myOffIcon = offIcon;
    }

    public void setIconPainted(boolean aPainted) {
      myEnabled = aPainted;
    }

    public int getIconHeight() {
      return myIcon.getIconHeight();
    }

    public int getIconWidth() {
      return myIcon.getIconWidth();
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
      if (myEnabled && myShouldPaint) {
        myIcon.paintIcon(c, g, x, y);
      } else if (myOffIcon != null) {
        myOffIcon.paintIcon(c, g, x, y);
      }
    }

    public void setVisible(final boolean visible) {
      if (myShouldPaint != visible) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            repaint();
          }
        });
      }
      myShouldPaint = visible;
    }
  }
}
