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
package com.intellij.openapi.wm.impl.status;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonHandler;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.Alarm;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class InfoAndProgressPanel extends JPanel implements StatusBarPatch {
  private final ProcessPopup myPopup;
  private final TextPanel myInfoPanel = new TextPanel(true);

  private final ArrayList<ProgressIndicatorEx> myOriginals = new ArrayList<ProgressIndicatorEx>();
  private final ArrayList<TaskInfo> myInfos = new ArrayList<TaskInfo>();
  private final Map<InlineProgressIndicator, ProgressIndicatorEx> myInline2Original
    = new HashMap<InlineProgressIndicator, ProgressIndicatorEx>();
  private final MultiValuesMap<ProgressIndicatorEx, InlineProgressIndicator> myOriginal2Inlines
    = new MultiValuesMap<ProgressIndicatorEx, InlineProgressIndicator>();

  private final MergingUpdateQueue myUpdateQueue;
  private final AsyncProcessIcon myProgressIcon;
  private final Alarm myQueryAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private boolean myShouldClosePopupAndOnProcessFinish;
  private final CompoundBorder myCompoundBorder;

  public InfoAndProgressPanel(final StatusBar statusBar) {
    setOpaque(false);
    final Border emptyBorder = BorderFactory.createEmptyBorder(0, 2, 0, 2);
    myInfoPanel.setBorder(emptyBorder);
    myInfoPanel.setOpaque(false);

    myCompoundBorder = BorderFactory.createCompoundBorder(new StatusBarImpl.SeparatorBorder.Left(), new EmptyBorder(0, 2, 0, 2));

    myProgressIcon = new AsyncProcessIcon("Background process");
    myProgressIcon.setOpaque(true);

    myProgressIcon.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (!myPopup.isShowing()) {
          openProcessPopup();
        } else {
          hideProcessPopup();
        }
      }
    });

    myProgressIcon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    StatusBarTooltipper.install(this, myProgressIcon, statusBar);

    myUpdateQueue = new MergingUpdateQueue("Progress indicator", 50, true, MergingUpdateQueue.ANY_COMPONENT);
    myPopup = new ProcessPopup(this);

    restoreEmptyStatus();
  }

  public JComponent getComponent() {
    return this;
  }

  public String updateStatusBar(final Editor selected, final JComponent componentSelected) {
    return ActionsBundle.message("action.ShowProcessWindow.double.click");
  }

  public void clear() {

  }

  public void addProgress(final ProgressIndicatorEx original, TaskInfo info) {
    synchronized (myOriginals) {
      final boolean veryFirst = myOriginals.isEmpty();

      myOriginals.add(original);
      myInfos.add(info);

      final InlineProgressIndicator expanded = createInlineDelegate(info, original, false);
      final InlineProgressIndicator compact = createInlineDelegate(info, original, true);

      myPopup.addIndicator(expanded);
      myProgressIcon.resume();

      if (veryFirst && !myPopup.isShowing()) {
        buildInInlineIndicator(compact);
      }
      else {
        buildInProcessCount();
      }

      runQuery();
    }
  }

  private void removeProgress(InlineProgressIndicator progress) {
    synchronized (myOriginals) {
      if (!myInline2Original.containsKey(progress)) return;

      final boolean last = myOriginals.size() == 1;
      final boolean beforeLast = myOriginals.size() == 2;

      myPopup.removeIndicator(progress);

      final ProgressIndicatorEx original = removeFromMaps(progress);
      if (myOriginals.contains(original)) return;

      if (last) {
        restoreEmptyStatus();
        if (myShouldClosePopupAndOnProcessFinish) {
          hideProcessPopup();
        }
      }
      else {
        if (myPopup.isShowing() || myOriginals.size() > 1) {
          buildInProcessCount();
        }
        else if (beforeLast) {
          buildInInlineIndicator(createInlineDelegate(myInfos.get(0), myOriginals.get(0), true));
        }
        else {
          restoreEmptyStatus();
        }
      }

      runQuery();
    }
  }

  private ProgressIndicatorEx removeFromMaps(final InlineProgressIndicator progress) {
    final ProgressIndicatorEx original = myInline2Original.get(progress);

    myInline2Original.remove(progress);

    myOriginal2Inlines.remove(original, progress);
    if (myOriginal2Inlines.get(original) == null) {
      final int originalIndex = myOriginals.indexOf(original);
      myOriginals.remove(originalIndex);
      myInfos.remove(originalIndex);
    }

    return original;
  }

  private void openProcessPopup() {
    synchronized (myOriginals) {
      if (myPopup.isShowing()) return;
      if (!myOriginals.isEmpty()) {
        myShouldClosePopupAndOnProcessFinish = true;
        buildInProcessCount();
      }
      else {
        myShouldClosePopupAndOnProcessFinish = false;
        restoreEmptyStatus();
      }
      myPopup.show();
    }
  }

  void hideProcessPopup() {
    synchronized (myOriginals) {
      if (!myPopup.isShowing()) return;

      if (myOriginals.size() == 1) {
        buildInInlineIndicator(createInlineDelegate(myInfos.get(0), myOriginals.get(0), true));
      }
      else if (myOriginals.isEmpty()) {
        restoreEmptyStatus();
      }
      else {
        buildInProcessCount();
      }

      myPopup.hide();
    }
  }

  private void buildInProcessCount() {
    removeAll();
    setLayout(new BorderLayout());

    final JPanel progressCountPanel = new JPanel(new BorderLayout(0, 2));
    String processWord = myOriginals.size() == 1 ? " process" : " processes";
    final LinkLabel label = new LinkLabel(myOriginals.size() + processWord + " running...", null, new LinkListener() {
      public void linkSelected(final LinkLabel aSource, final Object aLinkData) {
        triggerPopupShowing();
      }
    });
    label.setOpaque(true);

    final Wrapper labelComp = new Wrapper(label);
    progressCountPanel.add(labelComp, BorderLayout.CENTER);

    myProgressIcon.setBorder(myCompoundBorder);
    progressCountPanel.add(myProgressIcon, BorderLayout.WEST);

    add(myInfoPanel, BorderLayout.CENTER);

    progressCountPanel.setBorder(new EmptyBorder(0, 0, 0, 4));
    add(progressCountPanel, BorderLayout.EAST);

    revalidate();
    repaint();
  }

  private void buildInInlineIndicator(final InlineProgressIndicator inline) {
    removeAll();
    setLayout(new InlineLayout());
    add(myInfoPanel);

    final JPanel inlinePanel = new JPanel(new BorderLayout());

    inline.getComponent().setBorder(new EmptyBorder(0, 0, 0, 2));
    inlinePanel.add(inline.getComponent(), BorderLayout.CENTER);

    myProgressIcon.setBorder(myCompoundBorder);
    inlinePanel.add(myProgressIcon, BorderLayout.WEST);

    inline.updateProgressNow();

    add(inlinePanel);

    myInfoPanel.revalidate();
    myInfoPanel.repaint();
  }

  public void setText(final String text) {
    myInfoPanel.setText(text);
  }

  public BalloonHandler notifyByBalloon(MessageType type, String htmlBody, Icon icon, HyperlinkListener listener) {
    final Balloon balloon = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
      htmlBody.replace("\n", "<br>"),
      icon != null ? icon : type.getDefaultIcon(),
      type.getPopupBackground(),
      listener).createBalloon();

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        Component comp = InfoAndProgressPanel.this;
        if (comp.isShowing()) {
          int offset = comp.getHeight() / 2;
          Point point = new Point(comp.getWidth() - offset, comp.getHeight() - offset);
          balloon.show(new RelativePoint(comp, point), Balloon.Position.above);
        } else {
          final JRootPane rootPane = SwingUtilities.getRootPane(comp);
          if (rootPane != null && rootPane.isShowing()) {
            final Container contentPane = rootPane.getContentPane();
            final Rectangle bounds = contentPane.getBounds();
            final Point target = UIUtil.getCenterPoint(bounds, new Dimension(1, 1));
            target.y = bounds.height - 3;
            balloon.show(new RelativePoint(contentPane, target), Balloon.Position.above);
          }
        }
      }
    });

    return new BalloonHandler() {
      public void hide() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            balloon.hide();
          }
        });
      }
    };
  }

  private static class InlineLayout extends AbstractLayoutManager {

    public Dimension preferredLayoutSize(final Container parent) {
      Dimension result = new Dimension();
      for (int i = 0; i < parent.getComponentCount(); i++) {
        final Dimension prefSize = parent.getComponent(i).getPreferredSize();
        result.width += prefSize.width;
        result.height = Math.max(prefSize.height, result.height);
      }
      return result;
    }

    public void layoutContainer(final Container parent) {
      final Dimension size = parent.getSize();
      int compWidth = size.width / parent.getComponentCount();
      int eachX = 0;
      for (int i = 0; i < parent.getComponentCount(); i++) {
        final Component each = parent.getComponent(i);
        if (i == parent.getComponentCount() - 1) {
          compWidth = size.width - eachX;
        }
        each.setBounds(eachX, 0, compWidth, size.height);
        eachX += compWidth;
      }
    }
  }

  private InlineProgressIndicator createInlineDelegate(final TaskInfo info, final ProgressIndicatorEx original, final boolean compact) {
    final Collection<InlineProgressIndicator> inlines = myOriginal2Inlines.get(original);
    if (inlines != null) {
      for (InlineProgressIndicator eachInline : inlines) {
        if (eachInline.isCompact() == compact) return eachInline;
      }
    }

    final InlineProgressIndicator inline = new MyInlineProgressIndicator(compact, info, original);

    myInline2Original.put(inline, original);
    myOriginal2Inlines.put(original, inline);

    if (compact) {
      inline.getComponent().addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          if (!myPopup.isShowing()) {
            openProcessPopup();
          }
        }
      });
    }

    return inline;
  }

  private void triggerPopupShowing() {
    if (myPopup.isShowing()) {
      hideProcessPopup();
    }
    else {
      openProcessPopup();
    }
  }

  private void restoreEmptyStatus() {
    removeAll();
    setLayout(new BorderLayout());
    add(myInfoPanel, BorderLayout.CENTER);

    myProgressIcon.setBorder(myCompoundBorder);

    long wastedTime = ProgressManagerImpl.getWastedTime();
    if (ApplicationManagerEx.getApplicationEx().isInternal() && wastedTime > 10 * 1000) {
      JPanel wrapper = new JPanel(new BorderLayout());
      wrapper.add(new JLabel(" Your wasted time: " + formatTime(wastedTime) + " "), BorderLayout.CENTER);
      wrapper.add(myProgressIcon, BorderLayout.EAST);

      long time = System.currentTimeMillis() - ApplicationManagerEx.getApplicationEx().getStartTime();
      long percentage = wastedTime * 100 / time;
      String period = new SimpleDateFormat("m 'min' H 'hours'").format(new Date(2000, 0, 1, 0, 0, 0).getTime() + time);

      List<Pair<String, Long>> list = ProgressManagerImpl.getTimeWasters();
      StringBuilder s = new StringBuilder("<html>Successfully wasted " + percentage +"% of your time in " + period  + ":<br><border>");
      for (Pair<String, Long> each : list) {
        s.append("<tr><td>");
        s.append(each.first);
        s.append(":</td><td>");
        s.append(formatTime(each.second));
        s.append("</td></tr>");
      }
      s.append("</border></html>");
      wrapper.setToolTipText(s.toString());
      add(wrapper, BorderLayout.EAST);
    } else {
      add(myProgressIcon, BorderLayout.EAST);
    }

    myProgressIcon.suspend();
    myInfoPanel.revalidate();
    myInfoPanel.repaint();
  }

  private String formatTime(long t) {
    if (t < 1000) return "< 1 sec";
    if (t < 60 * 1000) return (t / 1000) + " sec";
    return "~" + (int)Math.ceil(t / (60 * 1000f)) + " min";
  }

  public boolean isProcessWindowOpen() {
    return myPopup.isShowing();
  }

  public void setProcessWindowOpen(final boolean open) {
    if (open) {
      openProcessPopup();
    }
    else {
      hideProcessPopup();
    }
  }

  private class MyInlineProgressIndicator extends InlineProgressIndicator {
    private final ProgressIndicatorEx myOriginal;
    private final TaskInfo myTask;

    public MyInlineProgressIndicator(final boolean compact, final TaskInfo task, final ProgressIndicatorEx original) {
      super(compact, task);
      myOriginal = original;
      myTask = task;
      original.addStateDelegate(this);
    }

    public void cancel() {
      super.cancel();
      updateProgress();
    }

    @Override
    public void stop() {
      super.stop();
      updateProgress();
    }

    @Override
    protected boolean isFinished() {
      return isFinished(myTask);
    }

    @Override
    public void finish(@NotNull final TaskInfo task) {
      super.finish(task);
      queueRunningUpdate(new Runnable() {
        public void run() {
          removeProgress(MyInlineProgressIndicator.this);
          dispose();
        }
      });
    }

    protected void cancelRequest() {
      myOriginal.cancel();
    }

    protected void queueProgressUpdate(final Runnable update) {
      myUpdateQueue.queue(new Update(MyInlineProgressIndicator.this, false, 1) {
        public void run() {
          ApplicationManager.getApplication().invokeLater(update);
        }
      });
    }

    protected void queueRunningUpdate(final Runnable update) {
      myUpdateQueue.queue(new Update(new Object(), false, 0) {
        public void run() {
          ApplicationManager.getApplication().invokeLater(update);
        }
      });
    }
  }

  private void runQuery() {
    if (getRootPane() == null) return;

    synchronized (myOriginals) {
      for (InlineProgressIndicator each : myInline2Original.keySet()) {
        each.updateProgress();
      }
    }
    myQueryAlarm.addRequest(new Runnable() {
      public void run() {
        runQuery();
      }
    }, 2000);
  }
}
