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

import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.TooltipEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.AbstractPopup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.EventListener;
import java.util.EventObject;

public class LightweightHint extends UserDataHolderBase implements Hint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.LightweightHint");

  private final JComponent myComponent;
  private JComponent myFocusBackComponent;
  private final EventListenerList myListenerList = new EventListenerList();
  private MyEscListener myEscListener;
  private JBPopup myPopup;
  private JComponent myParentComponent;
  private boolean myIsRealPopup = false;
  private boolean myForceLightweightPopup = false;
  private boolean mySelectingHint;

  private boolean myForceShowAsPopup = false;
  private String myTitle = null;
  private boolean myCancelOnClickOutside = true;
  private boolean myCancelOnOtherWindowOpen = true;
  private boolean myResizable;

  private IdeTooltip myCurrentIdeTooltip;
  private HintHint myHintHint;
  private JComponent myFocusRequestor;

  public LightweightHint(@NotNull final JComponent component) {
    myComponent = component;
  }

  public void setForceLightweightPopup(final boolean forceLightweightPopup) {
    myForceLightweightPopup = forceLightweightPopup;
  }


  public void setForceShowAsPopup(final boolean forceShowAsPopup) {
    myForceShowAsPopup = forceShowAsPopup;
  }

  public void setFocusRequestor(JComponent c) {
    myFocusRequestor = c;
  }

  public void setTitle(final String title) {
    myTitle = title;
  }

  public boolean isSelectingHint() {
    return mySelectingHint;
  }

  public void setSelectingHint(final boolean selectingHint) {
    mySelectingHint = selectingHint;
  }

  public void setCancelOnClickOutside(final boolean b) {
    myCancelOnClickOutside = b;
  }

  public void setCancelOnOtherWindowOpen(final boolean b) {
    myCancelOnOtherWindowOpen = b;
  }

  public void setResizable(final boolean b) {
    myResizable = b;
  }

  /**
   * Shows the hint in the layered pane. Coordinates <code>x</code> and <code>y</code>
   * are in <code>parentComponent</code> coordinate system. Note that the component
   * appears on 250 layer.
   */
  public void show(@NotNull final JComponent parentComponent,
                   final int x,
                   final int y,
                   final JComponent focusBackComponent,
                   @NotNull final HintHint hintHint) {
    myParentComponent = parentComponent;
    myHintHint = hintHint;

    myFocusBackComponent = focusBackComponent;

    LOG.assertTrue(myParentComponent.isShowing());
    myEscListener = new MyEscListener();
    myComponent.registerKeyboardAction(myEscListener, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                       JComponent.WHEN_IN_FOCUSED_WINDOW);
    final JLayeredPane layeredPane = parentComponent.getRootPane().getLayeredPane();

    myComponent.validate();

    if (!myForceShowAsPopup &&
        (myForceLightweightPopup || fitsLayeredPane(layeredPane, myComponent, new RelativePoint(parentComponent, new Point(x, y)), hintHint))) {
      beforeShow();
      final Dimension preferredSize = myComponent.getPreferredSize();


      if (hintHint.isAwtTooltip()) {
        IdeTooltip tooltip = new IdeTooltip(hintHint.getOriginalComponent(), hintHint.getOriginalPoint(), myComponent, hintHint, myComponent) {
          @Override
          protected boolean canAutohideOn(TooltipEvent event) {
            if (event.getInputEvent() instanceof MouseEvent) {
              return !(hintHint.isContentActive() && event.isIsEventInsideBalloon());
            } else if (event.getAction() != null) {
              return false;
            } else {
              return true;
            }
          }

          @Override
          protected void onHidden() {
            fireHintHidden();
            TooltipController.getInstance().resetCurrent();
          }

          @Override
          public boolean canBeDismissedOnTimeout() {
            return false;
          }
        }.setToCenterIfSmall(hintHint.isMayCenterTooltip())
          .setPreferredPosition(hintHint.getPreferredPosition())
          .setHighlighterType(hintHint.isHightlighterType())
          .setTextForeground(hintHint.getTextForeground())
          .setTextBackground(hintHint.getTextBackground())
          .setBorderColor(hintHint.getBorderColor())
          .setFont(hintHint.getTextFont())
          .setCalloutShift(hintHint.getCalloutShift())
          .setPositionChangeShift(hintHint.getPositionChangeX(), hintHint.getPositionChangeY())
          .setExlicitClose(hintHint.isExplicitClose());

        myComponent.validate();
        myCurrentIdeTooltip = IdeTooltipManager.getInstance().show(tooltip, false);
      } else {
        final Point layeredPanePoint = SwingUtilities.convertPoint(parentComponent, x, y, layeredPane);
        myComponent.setBounds(layeredPanePoint.x, layeredPanePoint.y, preferredSize.width, preferredSize.height);
        layeredPane.add(myComponent, JLayeredPane.POPUP_LAYER);

        myComponent.validate();
        myComponent.repaint();
      }
    }
    else {
      myIsRealPopup = true;
      myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(myComponent, myFocusRequestor)
        .setRequestFocus(myFocusRequestor != null)
        .setResizable(myResizable)
        .setMovable(myTitle != null)
        .setTitle(myTitle)
        .setModalContext(false)
        .setShowShadow(!myForceLightweightPopup && myForceShowAsPopup)
        .setCancelKeyEnabled(false)
        .setCancelOnClickOutside(myCancelOnClickOutside)
        .setCancelOnOtherWindowOpen(myCancelOnOtherWindowOpen)
        .setForceHeavyweight(!myForceLightweightPopup && myForceShowAsPopup)
        .createPopup();

      beforeShow();
      myPopup.show(new RelativePoint(myParentComponent, new Point(x, y)));
    }
  }

  protected void beforeShow() {

  }

  private static boolean fitsLayeredPane(JLayeredPane pane, JComponent component, RelativePoint desiredLocation, HintHint hintHint) {
    if (hintHint.isAwtTooltip()) {
      Dimension size = component.getPreferredSize();
      Dimension paneSize = pane.getSize();
      return size.width < paneSize.width && size.height < paneSize.height;
    } else {
      final Rectangle lpRect = new Rectangle(pane.getLocationOnScreen().x, pane.getLocationOnScreen().y, pane.getWidth(), pane.getHeight());
      Rectangle componentRect = new Rectangle(desiredLocation.getScreenPoint().x,
                                              desiredLocation.getScreenPoint().y,
                                              component.getPreferredSize().width,
                                              component.getPreferredSize().height);
      return lpRect.contains(componentRect);
    }
  }

  private void fireHintHidden() {
    final EventListener[] listeners = myListenerList.getListeners(HintListener.class);
    for (EventListener listener : listeners) {
      ((HintListener)listener).hintHidden(new EventObject(this));
    }
  }

  /**
   * @return bounds of hint component in the layered pane.
   */
  public final Rectangle getBounds() {
    return myComponent.getBounds();
  }

  public boolean isVisible() {
    if (myIsRealPopup) {
      return myPopup != null && myPopup.isVisible();
    } else if (myCurrentIdeTooltip != null) {
      return myComponent.isShowing() || IdeTooltipManager.getInstance().isQueuedToShow(myCurrentIdeTooltip);
    } else {
      return myComponent.isShowing();
    }
  }

  public final boolean isRealPopup() {
    return myIsRealPopup;
  }

  public void hide() {
    if (isVisible()) {
      if (myIsRealPopup) {
        myPopup.cancel();
        myPopup = null;
      }
      else {
        if (myCurrentIdeTooltip != null) {
          IdeTooltip tooltip = myCurrentIdeTooltip;
          myCurrentIdeTooltip = null;
          tooltip.hide();
        } else {
          final JRootPane rootPane = myComponent.getRootPane();
          if (rootPane != null) {
            final Rectangle bounds = myComponent.getBounds();
            final JLayeredPane layeredPane = rootPane.getLayeredPane();

            try {
              if (myFocusBackComponent != null) {
                LayoutFocusTraversalPolicyExt.setOverridenDefaultComponent(myFocusBackComponent);
              }
              layeredPane.remove(myComponent);
            }
            finally {
              LayoutFocusTraversalPolicyExt.setOverridenDefaultComponent(null);
            }

            layeredPane.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
          }
        }
      }
    }
    if (myEscListener != null) {
      myComponent.unregisterKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
    }

    TooltipController.getInstance().hide(this);

    fireHintHidden();
  }

  @Override
  public void pack() {
    updateBounds(-1, -1, false);
  }

  @Override
  public void updateBounds(int x, int y) {
    updateBounds(x, y, true);
  }

  private void updateBounds(int x, int y, boolean updateLocation) {
    setSize(myComponent.getPreferredSize());
    if (updateLocation) {
      setLocation(new RelativePoint(myParentComponent, new Point(x, y)));
    }
  }

  public final JComponent getComponent() {
    return myComponent;
  }

  public final void addHintListener(final HintListener listener) {
    myListenerList.add(HintListener.class, listener);
  }

  public final void removeHintListener(final HintListener listener) {
    myListenerList.remove(HintListener.class, listener);
  }

  public Point getLocationOn(JComponent c) {
    Point location;
    if (isRealPopup()) {
      location = myPopup.getLocationOnScreen();
      SwingUtilities.convertPointFromScreen(location, c);
    } else {
      location = SwingUtilities.convertPoint(
        myComponent.getParent(),
        myComponent.getLocation(),
        c
      );
    }

    return location;
  }

  @Override
  public void setLocation(RelativePoint point) {
    if (isRealPopup()) {
      myPopup.setLocation(point.getScreenPoint());
    } else {
      if (myCurrentIdeTooltip != null) {
        Point screenPoint = point.getScreenPoint();
        if (!screenPoint.equals(new RelativePoint(myCurrentIdeTooltip.getComponent(), myCurrentIdeTooltip.getPoint()).getScreenPoint())) {
          myCurrentIdeTooltip.setPoint(point.getPoint());
          myCurrentIdeTooltip.setComponent(point.getComponent());
          IdeTooltipManager.getInstance().show(myCurrentIdeTooltip, true);
        }
      } else {
        Point targetPoint = point.getPoint(myComponent.getParent());
        myComponent.setLocation(targetPoint);

        myComponent.revalidate();
        myComponent.repaint();
      }
    }
  }

  public void setSize(Dimension size) {
    if (myIsRealPopup) {
      myPopup.setSize(size);
    } else {
      //todo kirillk
      if (myHintHint.isAwtTooltip()) {
        return;
      } else {
        myComponent.setSize(size);

        myComponent.revalidate();
        myComponent.repaint();
      }
    }
  }

  public Dimension getSize() {
    return myComponent.getSize();
  }

  private final class MyEscListener implements ActionListener {
    public final void actionPerformed(final ActionEvent e) {
      hide();
    }
  }

  @Override
  public String toString() {
    return getComponent().toString();
  }

  public boolean canControlAutoHide() {
    return myCurrentIdeTooltip != null && myCurrentIdeTooltip.getTipComponent().isShowing() ;
  }

  public IdeTooltip getCurrentIdeTooltip() {
    return myCurrentIdeTooltip;
  }
}
