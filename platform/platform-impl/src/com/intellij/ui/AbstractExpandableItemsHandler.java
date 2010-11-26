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
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Collections;

abstract public class AbstractExpandableItemsHandler<KeyType, ComponentType extends JComponent> implements ExpandableItemsHandler<KeyType> {
  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  protected final ComponentType myComponent;
  private final CellRendererPane myRendererPane = new CellRendererPane();
  private final TipComponent myTipComponent;

  private boolean isEnabled = true;
  private Hint myHint;

  private KeyType myKey;
  private Rectangle myKeyItemBounds;
  private BufferedImage myImage;

  protected AbstractExpandableItemsHandler(@NotNull final ComponentType component) {
    myComponent = component;
    myComponent.add(myRendererPane);
    myComponent.validate();

    myTipComponent = new TipComponent();
    myComponent.addMouseListener(
      new MouseListener() {
        public void mouseEntered(MouseEvent e) {
          handleMouseEvent(e);
        }

        public void mouseExited(MouseEvent e) {
          hideHint();
        }

        public void mouseClicked(MouseEvent e) {
        }

        public void mousePressed(MouseEvent e) {
          handleMouseEvent(e);
        }

        public void mouseReleased(MouseEvent e) {
          handleMouseEvent(e);
        }
      }
    );

    myComponent.addMouseMotionListener(
      new MouseMotionListener() {
        public void mouseDragged(MouseEvent e) {
          handleMouseEvent(e);
        }

        public void mouseMoved(MouseEvent e) {
          handleMouseEvent(e, false);
        }
      }
    );

    myComponent.addFocusListener(
      new FocusAdapter() {
        public void focusLost(FocusEvent e) {
          hideHint();
        }

        public void focusGained(FocusEvent e) {
          updateCurrentSelection();
        }
      }
    );

    myComponent.addComponentListener(
      new ComponentAdapter() {
        public void componentHidden(ComponentEvent e) {
          hideHint();
        }

        public void componentMoved(ComponentEvent e) {
          updateCurrentSelection();
        }

        @Override
        public void componentResized(ComponentEvent e) {
          updateCurrentSelection();
        }
      }
    );

    myComponent.addHierarchyBoundsListener(new HierarchyBoundsAdapter() {
      @Override
      public void ancestorMoved(HierarchyEvent e) {
        updateCurrentSelection();
      }

      @Override
      public void ancestorResized(HierarchyEvent e) {
        updateCurrentSelection();
      }
    });

    myComponent.addHierarchyListener(
      new HierarchyListener() {
        public void hierarchyChanged(HierarchyEvent e) {
          hideHint();
        }
      }
    );
  }

  @Override
  public void setEnabled(boolean enabled) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    isEnabled = enabled;
    if (!isEnabled) hideHint();
  }

  @NotNull
  @Override
  public Collection<KeyType> getExpandedItems() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    return myKey == null ? Collections.<KeyType>emptyList() : Collections.singleton(myKey);
  }

  protected void updateCurrentSelection() {
    handleSelectionChange(myKey, true);
  }

  private void handleMouseEvent(MouseEvent e) {
    handleMouseEvent(e, true);
  }

  private void handleMouseEvent(MouseEvent e, boolean forceUpdate) {
    KeyType selected = getCellKeyForPoint(e.getPoint());
    if (forceUpdate || !Comparing.equal(myKey, selected)) {
      handleSelectionChange(selected, true);
    }
  }

  protected void handleSelectionChange(KeyType selected) {
    handleSelectionChange(selected, false);
  }

  protected void handleSelectionChange(final KeyType selected, final boolean processIfUnfocused) {
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      return;
    }
    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(new Runnable() {
      public void run() {
        doHandleSelectionChange(selected, processIfUnfocused);
      }
    }, 10);
  }

  private void doHandleSelectionChange(KeyType selected, boolean processIfUnfocused) {
    if (!isEnabled) return;

    if (selected == null
        || !myComponent.isShowing()
        || (!myComponent.isFocusOwner() && !processIfUnfocused)
        || isPopup()) {
      hideHint();
      return;
    }

    if (!Comparing.equal(myKey, selected)) {
      hideHint();
    }
    myKey = selected;

    Point location = createToolTipImage(myKey);

    if (location == null) {
      hideHint();
    }
    else if (myHint == null) {
      showHint(location);
    }
    else {
      repaintHint(location);
    }
  }

  private boolean isPopup() {
    Window window = SwingUtilities.getWindowAncestor(myComponent);
    return window != null && !(window instanceof Dialog || window instanceof Frame);
  }

  private void hideHint() {
    myUpdateAlarm.cancelAllRequests();
    if (myHint != null) {
      myHint.hide();
      myHint = null;
      repaintKeyItem();
    }
    myKey = null;
  }

  private void showHint(Point location) {
    assert myHint == null;

    if (!myComponent.isShowing()) {
      return;
    }

    JLayeredPane layeredPane = myComponent.getRootPane().getLayeredPane();
    Point layeredPanePoint = SwingUtilities.convertPoint(myComponent, location.x + myTipComponent.getPreferredSize().width, 0, layeredPane);
    boolean fitIntoLayeredPane = layeredPanePoint.x < layeredPane.getWidth();

    if (fitIntoLayeredPane) {
      myHint = new LightweightHint(myTipComponent);
    }
    else {
      MenuElement[] selectedPath = MenuSelectionManager.defaultManager().getSelectedPath();
      if (selectedPath.length > 0) {
        // do not show heavyweight hints when menu is shown to avoid their overlapping
        return;
      }
      myHint = new HeavyweightHint(myTipComponent, false);
    }
    myHint.show(myComponent, location.x, location.y, myComponent, new HintHint(myComponent, location));
    repaintKeyItem();
  }

  private void repaintHint(Point location) {
    if (myHint != null && myKey != null && myComponent.isShowing()) {
      myHint.updateBounds(location.x, location.y);
      myTipComponent.repaint();
      repaintKeyItem();
    }
  }

  private void repaintKeyItem() {
    if (myKeyItemBounds != null) {
      myComponent.repaint(myKeyItemBounds);
    }
  }

  @Nullable
  private Point createToolTipImage(@NotNull KeyType key) {
    Pair<Component, Rectangle> rendererAndBounds = getCellRendererAndBounds(key);
    if (rendererAndBounds == null) return null;

    Component renderer = rendererAndBounds.first;
    if (!(renderer instanceof JComponent)) return null;

    myKeyItemBounds = rendererAndBounds.second;

    Rectangle cellBounds = myKeyItemBounds;
    Rectangle visibleRect = getVisibleRect(key);

    int width = cellBounds.x + cellBounds.width - (visibleRect.x + visibleRect.width);
    int height = cellBounds.height;

    if (width <= 0 || height <= 0) return null;
    if (cellBounds.y < visibleRect.y) return null;
    if (cellBounds.y + cellBounds.height > visibleRect.y + visibleRect.height) return null;

    myImage = createImage(height, width);

    Graphics2D g = myImage.createGraphics();
    g.setClip(null);
    doFillBackground(height, width, g);
    g.translate(-(visibleRect.x + visibleRect.width - cellBounds.x), 0);
    doPaintTooltipImage(renderer, cellBounds, height, g, key);

    // paint border
    g.translate((visibleRect.x + visibleRect.width - cellBounds.x), 0);
    g.setColor(Color.GRAY);
    int rightX = myImage.getWidth() - 1;
    final int h = myImage.getHeight();
    UIUtil.drawLine(g, 0, 0, rightX, 0);
    UIUtil.drawLine(g, rightX, 0, rightX, h);
    UIUtil.drawLine(g, 0, h - 1, rightX, h - 1);

    g.dispose();
    myRendererPane.remove(renderer);

    return new Point(visibleRect.x + visibleRect.width, cellBounds.y);
  }

  protected BufferedImage createImage(final int height, final int width) {
    return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
  }

  protected void doFillBackground(int height, int width, Graphics2D g) {
    g.setColor(myComponent.getBackground());
    g.fillRect(0, 0, width, height);
  }

  protected void doPaintTooltipImage(Component rComponent, Rectangle cellBounds, int height, Graphics2D g, KeyType key) {
    myRendererPane.paintComponent(g, rComponent, myComponent, 0, 0, cellBounds.width, height, true);
  }

  protected Rectangle getVisibleRect(KeyType key) {
    return myComponent.getVisibleRect();
  }

  @Nullable
  protected abstract Pair<Component, Rectangle> getCellRendererAndBounds(KeyType key);

  protected abstract KeyType getCellKeyForPoint(Point point);

  private class TipComponent extends JComponent {
    public Dimension getMaximumSize() {
      return getPreferredSize();
    }

    public Dimension getMinimumSize() {
      return getPreferredSize();
    }

    public Dimension getPreferredSize() {
      return new Dimension(myImage.getWidth(), myImage.getHeight());
    }

    public void paint(Graphics g) {
      g.drawImage(myImage, 0, 0, null);
    }
  }
}