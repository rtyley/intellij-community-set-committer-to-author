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
package com.intellij.openapi.wm.impl.content;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.TitlePanel;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.content.*;
import com.intellij.ui.content.tabs.TabbedContentAction;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.ui.tabs.impl.singleRow.MoreIcon;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.GeneralPath;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ToolWindowContentUi extends JPanel implements ContentUI, PropertyChangeListener, DataProvider {
  public static final String POPUP_PLACE = "ToolwindowPopup";

  private ContentManager myManager;

  ArrayList<ContentTabLabel> myTabs = new ArrayList<ContentTabLabel>();
  private final Map<Content, ContentTabLabel> myContent2Tabs = new HashMap<Content, ContentTabLabel>();

  private final JPanel myContent = new JPanel(new BorderLayout());
  ToolWindowImpl myWindow;

  TabbedContentAction.CloseAllAction myCloseAllAction;
  TabbedContentAction.MyNextTabAction myNextTabAction;
  TabbedContentAction.MyPreviousTabAction myPreviousTabAction;


  private final BaseLabel myIdLabel = new BaseLabel(this, false);

  private final MoreIcon myMoreIcon = new MoreIcon() {
    protected Rectangle getIconRec() {
      return myLastLayout.moreRect;
    }

    protected boolean isActive() {
      return myWindow.isActive();
    }

    protected int getIconY(final Rectangle iconRec) {
      return iconRec.height / 2 - getIconHeight() / 2 + TitlePanel.STRUT;
    }
  };

  private JPopupMenu myPopup;
  private final PopupMenuListener myPopupListener;

  private static final int MORE_ICON_BORDER = 6;

  private LayoutData myLastLayout;

  public ToolWindowContentUi(ToolWindowImpl window) {
    myWindow = window;
    myContent.setOpaque(false);
    myContent.setFocusable(false);
    setOpaque(false);

    setBorder(new EmptyBorder(0, 0, 0, 2));

    myPopupListener = new MyPopupListener();

    new BaseButtonBehavior(this) {
      protected void execute(final MouseEvent e) {
        if (myLastLayout != null) {
          final Rectangle moreRect = myLastLayout.moreRect;
          if (moreRect != null && moreRect.contains(e.getPoint())) {
            showPopup();
          }
        }
      }
    };
  }

  public JComponent getComponent() {
    return myContent;
  }

  public JComponent getTabComponent() {
    return this;
  }

  public void setManager(final ContentManager manager) {
    myManager = manager;
    myManager.addContentManagerListener(new ContentManagerListener() {
      public void contentAdded(final ContentManagerEvent event) {
        final ContentTabLabel tab = new ContentTabLabel(event.getContent(), ToolWindowContentUi.this);
        myTabs.add(event.getIndex(), tab);
        myContent2Tabs.put(event.getContent(), tab);
        event.getContent().addPropertyChangeListener(ToolWindowContentUi.this);
        rebuild();
      }

      public void contentRemoved(final ContentManagerEvent event) {
        final ContentTabLabel tab = myContent2Tabs.get(event.getContent());
        if (tab != null) {
          myTabs.remove(tab);
          myContent2Tabs.remove(event.getContent());
          event.getContent().removePropertyChangeListener(ToolWindowContentUi.this);

          ensureSelectedContentVisible();

          rebuild();
        }
      }

      public void contentRemoveQuery(final ContentManagerEvent event) {
      }

      public void selectionChanged(final ContentManagerEvent event) {
        ensureSelectedContentVisible();

        update();

        myContent.revalidate();
        myContent.repaint();
      }
    });

    initMouseListeners(this, ToolWindowContentUi.this);
    update();


    myCloseAllAction = new TabbedContentAction.CloseAllAction(myManager);
    myNextTabAction = new TabbedContentAction.MyNextTabAction(myManager);
    myPreviousTabAction = new TabbedContentAction.MyPreviousTabAction(myManager);
  }

  private void ensureSelectedContentVisible() {
    final Content selected = myManager.getSelectedContent();
    if (selected == null) {
      myContent.removeAll();
      return;
    }

    if (myContent.getComponentCount() == 1) {
      final Component visible = myContent.getComponent(0);
      if (visible == selected.getComponent()) return;
    }

    myContent.removeAll();
    myContent.add(selected.getComponent(), BorderLayout.CENTER);

    myContent.revalidate();
    myContent.repaint();
  }


  private void rebuild() {
    removeAll();

    add(myIdLabel);
    initMouseListeners(myIdLabel, this);

    for (ContentTabLabel each : myTabs) {
      add(each);
      initMouseListeners(each, this);
    }

    update();

    revalidate();
    repaint();

    if (myTabs.size() == 0 && myWindow.isToHideOnEmptyContent()) {
      myWindow.hide(null);
    }
  }

  private class LayoutData {
    int toFitWidth;
    int requiredWidth;
    Dimension layoutSize = getSize();
    boolean fullLayout = true;

    int moreRectWidth;

    ArrayList<ContentTabLabel> toLayout = new ArrayList<ContentTabLabel>();
    ArrayList<ContentTabLabel> toDrop = new ArrayList<ContentTabLabel>();

    Rectangle moreRect;

    public int eachX;
    public int eachY;
    public int contentCount = myManager.getContentCount();
  }

  public void doLayout() {
    LayoutData data = new LayoutData();

    data.eachX = 0;
    data.eachY = TitlePanel.STRUT;

    myIdLabel.setBounds(data.eachX, data.eachY, myIdLabel.getPreferredSize().width, getHeight());
    data.eachX += myIdLabel.getPreferredSize().width;
    int tabsStart = data.eachX;

    if (myManager.getContentCount() == 0) return;

    Content selected = myManager.getSelectedContent();
    if (selected == null) {
      selected = myManager.getContents()[0];
    }

    if (myLastLayout != null && myLastLayout.layoutSize.equals(getSize()) && myLastLayout.contentCount == myManager.getContentCount()) {
      for (ContentTabLabel each : myTabs) {
        if (!each.isValid()) break;
        if (each.myContent == selected && each.getBounds().width != 0) {
          data = myLastLayout;
          data.fullLayout = false;
        }
      }
    }


    if (data.fullLayout) {
      for (ContentTabLabel eachTab : myTabs) {
        final Dimension eachSize = eachTab.getPreferredSize();
        data.requiredWidth += eachSize.width;
        data.requiredWidth++;
        data.toLayout.add(eachTab);
      }


      data.moreRectWidth = myMoreIcon.getIconWidth() + MORE_ICON_BORDER * 2;
      data.toFitWidth = getSize().width - data.eachX;

      final ContentTabLabel selectedTab = myContent2Tabs.get(selected);
      while (true) {
        if (data.requiredWidth <= data.toFitWidth) break;
        if (data.toLayout.size() <= 1) break;

        if (data.toLayout.get(0) != selectedTab) {
          dropTab(data, data.toLayout.remove(0));
        } else if (data.toLayout.get(data.toLayout.size() - 1) != selectedTab) {
          dropTab(data, data.toLayout.remove(data.toLayout.size() - 1));
        } else {
          break;
        }
      }


      boolean reachedBounds = false;
      data.moreRect = null;
      for (ContentTabLabel each : data.toLayout) {
        if (isToDrawTabs()) {
          data.eachY = 0;
        } else {
          data.eachY = TitlePanel.STRUT;
        }
        final Dimension eachSize = each.getPreferredSize();
          if (data.eachX + eachSize.width < data.toFitWidth + tabsStart) {
            each.setBounds(data.eachX, data.eachY, eachSize.width, getHeight() - data.eachY);
            data.eachX += eachSize.width;
            data.eachX++;
          } else {
            if (!reachedBounds) {
              final int width = getWidth() - data.eachX - data.moreRectWidth;
              each.setBounds(data.eachX, data.eachY, width, getHeight() - data.eachY);
              data.eachX += width;
              data.eachX ++;
            } else {
              each.setBounds(0, 0, 0, 0);
            }
            reachedBounds = true;
          }
      }

      for (ContentTabLabel each : data.toDrop) {
        each.setBounds(0, 0, 0, 0);
      }
    }

    if (data.toDrop.size() > 0) {
      data.moreRect = new Rectangle(data.eachX + MORE_ICON_BORDER, 0, myMoreIcon.getIconWidth(), getHeight());
      final int selectedIndex = myManager.getIndexOfContent(myManager.getSelectedContent());
      if (selectedIndex == 0) {
        myMoreIcon.setPaintedIcons(false, true);
      } else if (selectedIndex == myManager.getContentCount() - 1) {
        myMoreIcon.setPaintedIcons(true, false);
      } else {
        myMoreIcon.setPaintedIcons(true, true);
      }
    } else {
      data.moreRect = null;
    }
    
    myLastLayout = data;
  }

  private void dropTab(final LayoutData data, final ContentTabLabel toDropLabel) {
    data.requiredWidth -= (toDropLabel.getPreferredSize().width + 1);
    data.toDrop.add(toDropLabel);
    if (data.toDrop.size() == 1) {
      data.toFitWidth -= data.moreRectWidth;
    }
  }

  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);
    if (!isToDrawTabs()) return;

    final Graphics2D g2d = (Graphics2D)g;

    final GraphicsConfig c = new GraphicsConfig(g);
    c.setAntialiasing(true);


    for (ContentTabLabel each : myTabs) {
      final Shape shape = getShapeFor(each);
      final Rectangle bounds = each.getBounds();
      if (myWindow.isActive()) {
        Color from;
        Color to;
        if (each.isSelected()) {
          from = new Color(90, 133, 215);
          to = new Color(33, 87, 138);
          g2d.setPaint(new GradientPaint(bounds.x, bounds.y, from, bounds.x, (float)bounds.getMaxY(), to));
        }
        else {
          from = new Color(129, 147, 219);
          to = new Color(84, 130, 171);
          g2d.setPaint(new GradientPaint(bounds.x, bounds.y, from, bounds.x, (float)bounds.getMaxY(), to));
        }
      }
      else {
        g2d.setPaint(
          new GradientPaint(bounds.x, bounds.y, new Color(152, 143, 134), bounds.x, (float)bounds.getMaxY(), new Color(165, 157, 149)));
      }

      g2d.fill(shape);
    }

    c.restore();
  }

  protected void paintChildren(final Graphics g) {
    super.paintChildren(g);
    if (!isToDrawTabs()) return;

    final GraphicsConfig c = new GraphicsConfig(g);
    c.setAntialiasing(true);

    final Graphics2D g2d = (Graphics2D)g;

    final Color edges = myWindow.isActive() ? new Color(38, 63, 106) : new Color(130, 120, 111);
    g2d.setColor(edges);
    for (int i = 0; i < myTabs.size(); i++) {
      ContentTabLabel each = myTabs.get(i);
      final Shape shape = getShapeFor(each);
      g2d.draw(shape);
    }

    c.restore();

    if (myLastLayout != null && myLastLayout.moreRect != null) {
      myMoreIcon.paintIcon(this, g);
    }
  }

  private Shape getShapeFor(ContentTabLabel label) {
    final Rectangle bounds = label.getBounds();

    if (bounds.width <= 0 || bounds.height <= 0) return new GeneralPath();

    if (!label.isSelected()) {
      bounds.y += 3;
    }

    bounds.width += 1;

    int arc = 2;

    final GeneralPath path = new GeneralPath();
    path.moveTo(bounds.x, bounds.y + bounds.height);
    path.lineTo(bounds.x, bounds.y + arc);
    path.quadTo(bounds.x, bounds.y, bounds.x + arc, bounds.y);
    path.lineTo(bounds.x + bounds.width - arc, bounds.y);
    path.quadTo(bounds.x + bounds.width, bounds.y, bounds.x + bounds.width, bounds.y + arc);
    path.lineTo(bounds.x + bounds.width, bounds.y + bounds.height);
    path.closePath();

    return path;
  }


  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    size.height = 0;
    for (int i = 0; i < getComponentCount(); i++) {
      final Component each = getComponent(i);
      size.height = Math.max(each.getPreferredSize().height, size.height);
    }
    return size;
  }

  public void propertyChange(final PropertyChangeEvent evt) {
    update();
  }

  private void update() {
    for (ContentTabLabel each : myTabs) {
      each.update();
    }

    myIdLabel.setText(myWindow.getId());
    myIdLabel.setBorder(new EmptyBorder(0, 2, 0, 8));

    if (myTabs.size() == 1) {
      final String text = myTabs.get(0).getText();
      if (text != null && text.trim().length() > 0) {
        myIdLabel.setText(myIdLabel.getText() + " ");
        myIdLabel.setBorder(new EmptyBorder(0, 2, 0, 0));
      }
    }

    revalidate();
    repaint();
  }

  public boolean isSingleSelection() {
    return true;
  }

  public boolean isToSelectAddedContent() {
    return false;
  }

  public boolean canBeEmptySelection() {
    return false;
  }

  public void beforeDispose() {
  }

  static void initMouseListeners(final JComponent c, final ToolWindowContentUi ui) {
    if (c.getClientProperty(ui) != null) return;


    final Point[] myLastPoint = new Point[1];

    c.addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseDragged(final MouseEvent e) {
        if (myLastPoint[0] == null) return;

        final Window window = SwingUtilities.windowForComponent(c);

        if (window instanceof IdeFrame) return;

        final Rectangle oldBounds = window.getBounds();
        final Point newPoint = e.getPoint();
        SwingUtilities.convertPointToScreen(newPoint, c);
        final Point offset = new Point(newPoint.x - myLastPoint[0].x, newPoint.y - myLastPoint[0].y);
        window.setLocation(oldBounds.x + offset.x, oldBounds.y + offset.y);
        myLastPoint[0] = newPoint;
      }
    });

    c.addMouseListener(new MouseAdapter() {
      public void mousePressed(final MouseEvent e) {
        myLastPoint[0] = e.getPoint();
        SwingUtilities.convertPointToScreen(myLastPoint[0], c);
        if (!e.isPopupTrigger()) {
          if (UIUtil.isCloseClick(e)) {
            ui.processHide(e);
          }
          else {
            ui.myWindow.fireActivated();
          }
        }
      }
    });


    final DefaultActionGroup contentGroup = new DefaultActionGroup();
    if (c instanceof ContentTabLabel) {
      final Content content = ((ContentTabLabel)c).myContent;
      contentGroup.add(new TabbedContentAction.CloseAction(content));
      contentGroup.add(ui.myCloseAllAction);
      contentGroup.add(new TabbedContentAction.CloseAllButThisAction(content));
      contentGroup.addSeparator();
      if (content.isPinnable()) {
        contentGroup.add(PinToolwindowTabAction.getPinAction());
        contentGroup.addSeparator();
      }

      contentGroup.add(ui.myNextTabAction);
      contentGroup.add(ui.myPreviousTabAction);
      contentGroup.addSeparator();
    }

    c.addMouseListener(new PopupHandler() {
      public void invokePopup(final Component comp, final int x, final int y) {
        DefaultActionGroup group = new DefaultActionGroup();
        group.addAll(contentGroup);

        final ActionGroup windowPopup = ui.myWindow.getPopupGroup();
        if (windowPopup != null) {
          group.addAll(windowPopup);
        }

        final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(POPUP_PLACE, group);
        popupMenu.getComponent().show(comp, x, y);
      }
    });

    c.putClientProperty(ui, Boolean.TRUE);
  }

  private void processHide(final MouseEvent e) {
    IdeEventQueue.getInstance().blockNextEvents(e);
    final Component c = e.getComponent();
    if (c instanceof ContentTabLabel) {
      final ContentTabLabel tab = (ContentTabLabel)c;
      if (myManager.canCloseContents() && tab.myContent.isCloseable()) {
        myManager.removeContent(tab.myContent, true);
      } else {
        if (myManager.getContentCount() == 1) {
          hideWindow(e);
        }
      }
    }
    else {
      hideWindow(e);
    }
  }

  private void hideWindow(final MouseEvent e) {
    if (e.isControlDown()) {
      myWindow.fireHiddenSide();
    }
    else {
      myWindow.fireHidden();
    }
  }

  @Nullable
  public Object getData(@NonNls String dataId) {
    if (dataId.equals(PlatformDataKeys.TOOL_WINDOW.getName())) return myWindow;

    return null;
  }

  public boolean isToDrawTabs() {
    return myTabs.size() > 1;
  }

  private void showPopup() {
    myPopup = new JPopupMenu();
    myPopup.addPopupMenuListener(myPopupListener);
    for (final ContentTabLabel each : myTabs) {
      final JCheckBoxMenuItem item = new JCheckBoxMenuItem(each.getText());
      if (myManager.isSelected(each.myContent)) {
        item.setSelected(true);
      }
      item.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          myManager.setSelectedContent(each.myContent, true);
        }
      });
      myPopup.add(item);
    }
    myPopup.show(this, myLastLayout.moreRect.x, myLastLayout.moreRect.y);
  }

  public void dispose() {
  }


  private class MyPopupListener implements PopupMenuListener {
    public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
    }

    public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
      if (myPopup != null) {
        myPopup.removePopupMenuListener(this);
      }
      myPopup = null;
    }

    public void popupMenuCanceled(final PopupMenuEvent e) {
    }
  }

}
