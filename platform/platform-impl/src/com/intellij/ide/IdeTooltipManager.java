/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BalloonImpl;
import com.intellij.ui.HintHint;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;

public class IdeTooltipManager implements ApplicationComponent, AWTEventListener {

  private RegistryValue myIsEnabled;

  private Component myCurrentComponent;
  private Component myQueuedComponent;

  private BalloonImpl myCurrentTipUi;
  private MouseEvent myCurrentEvent;
  private boolean myCurrentTipIsCentered;

  private Runnable myHideRunnable;

  private JBPopupFactory myPopupFactory;
  private JEditorPane myTipLabel;

  private boolean myShowDelay = true;

  private Alarm myAlarm = new Alarm();

  private int myX;
  private int myY;
  private RegistryValue myMode;

  private IdeTooltip myCurrentTooltip;
  private Runnable myShowRequest;
  private IdeTooltip myQueuedTooltip;


  public IdeTooltipManager(JBPopupFactory popupFactory) {
    myPopupFactory = popupFactory;
  }

  @Override
  public void initComponent() {
    myMode = Registry.get("ide.tooltip.mode");

    myIsEnabled = Registry.get("ide.tooltip.callout");
    myIsEnabled.addListener(new RegistryValueListener.Adapter() {
      @Override
      public void afterValueChanged(RegistryValue value) {
        processEnabled();
      }
    }, ApplicationManager.getApplication());

    Toolkit.getDefaultToolkit().addAWTEventListener(this, MouseEvent.MOUSE_EVENT_MASK | MouseEvent.MOUSE_MOTION_EVENT_MASK);

    ActionManager.getInstance().addAnActionListener(new AnActionListener.Adapter() {
      @Override
      public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        hideCurrent(null, action, event);
      }
    }, ApplicationManager.getApplication());

    processEnabled();
  }

  @Override
  public void eventDispatched(AWTEvent event) {
    if (!myIsEnabled.asBoolean()) return;

    MouseEvent me = (MouseEvent)event;
    Component c = me.getComponent();
    if (me.getID() == MouseEvent.MOUSE_ENTERED) {
      boolean canShow = true;
      if (me.getComponent() != myCurrentComponent) {
        canShow = hideCurrent(me, null, null);
      }
      if (canShow) {
        maybeShowFor(c, me);
      }
    } else if (me.getID() == MouseEvent.MOUSE_EXITED) {
      if (me.getComponent() == myCurrentComponent || me.getComponent() == myQueuedComponent) {
        hideCurrent(me, null, null);
      }
    } else if (me.getID() == MouseEvent.MOUSE_MOVED) {
      if (me.getComponent() == myCurrentComponent || me.getComponent() == myQueuedComponent) {
        if (myCurrentTipUi != null && myCurrentTipUi.wasFadedIn()) {
          if (hideCurrent(me, null, null)) {
            maybeShowFor(c, me);
          }
        } else {
          if (!myCurrentTipIsCentered) {
            myX = me.getX();
            myY = me.getY();
            maybeShowFor(c, me);
          }
        }
      } else if (myCurrentComponent == null && myQueuedComponent == null) {
        maybeShowFor(c, me);
      }
    } else if (me.getID() == MouseEvent.MOUSE_PRESSED) {
      if (me.getComponent() == myCurrentComponent) {
        hideCurrent(me, null, null);
      }
    } else if (me.getID() == MouseEvent.MOUSE_DRAGGED) {
      hideCurrent(me, null, null);
    }
  }

  private void maybeShowFor(Component c, MouseEvent me) {
    if (!(c instanceof JComponent)) return;

    JComponent comp = (JComponent)c;

    String tooltipText = comp.getToolTipText(me);
    if (tooltipText == null || tooltipText.trim().length() == 0) return;

    queueShow(comp, me, Boolean.TRUE.equals(comp.getClientProperty(UIUtil.CENTER_TOOLTIP)));
  }

  private void queueShow(final JComponent c, final MouseEvent me, final boolean toCenter) {
    final IdeTooltip tooltip = new IdeTooltip(c, me.getPoint(), null, new Object()) {
      @Override
      protected boolean beforeShow() {
        myCurrentEvent = me;

        if (!c.isShowing()) return false;

        String text = c.getToolTipText(myCurrentEvent);
        if (text == null || text.trim().length() == 0) return false;

        JLayeredPane layeredPane = IJSwingUtilities.findParentOfType(c, JLayeredPane.class);

        myTipLabel = initPane(text, new HintHint(me).setAwtTooltip(true), layeredPane);

        setTipComponent(myTipLabel);
        return true;
      }
    }.setToCenter(toCenter);

    show(tooltip, false);
  }

  public IdeTooltip show(final IdeTooltip tooltip, boolean now) {
    myAlarm.cancelAllRequests();

    hideCurrent(null, null, null);

    myQueuedComponent = tooltip.getComponent();
    myQueuedTooltip = tooltip;

    myShowRequest = new Runnable() {
      @Override
      public void run() {
        if (myShowRequest == null) {
          return;
        }

        if (myQueuedComponent != tooltip.getComponent() || !tooltip.getComponent().isShowing()) {
          hideCurrent(null, null, null);
          return;
        }

        if (tooltip.beforeShow()) {
          show(tooltip, null);
        }
        else {
          hideCurrent(null, null, null);
        }
      }
    };

    if (now) {
      myShowRequest.run();
    } else {
      myAlarm.addRequest(myShowRequest, myShowDelay ? tooltip.getShowDelay() : tooltip.getInitialReshowDelay());
    }

    return tooltip;
  }

  private void show(final IdeTooltip tooltip, Runnable beforeShow) {
    boolean toCenterX;
    boolean toCenterY;

    boolean toCenter = tooltip.isToCenter();
    if (!toCenter && tooltip.isToCenterIfSmall()) {
      Dimension size = tooltip.getComponent().getSize();
      toCenterX = size.width < 64;
      toCenterY = size.height < 64;
      toCenter = toCenterX || toCenterY;
    } else {
      toCenterX = true;
      toCenterY = true;
    }

    Point effectivePoint = tooltip.getPoint();
    if (toCenter) {
      Rectangle bounds = tooltip.getComponent().getBounds();
      effectivePoint.x = toCenterX ? bounds.width / 2 : effectivePoint.x;
      effectivePoint.y = toCenterY ? (bounds.height / 2) : effectivePoint.y;
    }


    if (myCurrentComponent == tooltip.getComponent() && effectivePoint.equals(new Point(myX, myY))) {
      return;
    }

    Color bg = tooltip.getTextBackground() != null ? tooltip.getTextBackground() : getTextBackground(true);
    Color fg = tooltip.getTextForeground() != null ? tooltip.getTextForeground() : getTextForeground(true);
    Color border = tooltip.getBorderColor() != null ? tooltip.getBorderColor() : getBorderColor(true);

    BalloonBuilder builder = myPopupFactory.createBalloonBuilder(tooltip.getTipComponent())
      .setPreferredPosition(tooltip.getPreferredPosition())
      .setFillColor(bg)
      .setBorderColor(border)
      .setAnimationCycle(150)
      .setShowCallout(true)
      .setCalloutShift(tooltip.getCalloutShift())
      .setPositionChangeXShift(tooltip.getPositionChangeX())
      .setPositionChangeYShift(tooltip.getPositionChangeY());
    tooltip.getTipComponent().setForeground(fg);
    tooltip.getTipComponent().setBorder(new EmptyBorder(1, 3, 2, 3));
    tooltip.getTipComponent().setFont(tooltip.getFont() != null ? tooltip.getFont() : getTextFont(true));


    if (beforeShow != null) {
      beforeShow.run();
    }

    myCurrentTipUi = (BalloonImpl)builder.createBalloon();
    myCurrentComponent = tooltip.getComponent();
    myX = effectivePoint.x;
    myY = effectivePoint.y;
    myCurrentTipIsCentered = toCenter;
    myCurrentTooltip = tooltip;
    myShowRequest = null;
    myQueuedComponent = null;
    myQueuedTooltip = null;

    myCurrentTipUi.show(new RelativePoint(tooltip.getComponent(), effectivePoint), tooltip.getPreferredPosition());
    myAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (myCurrentTooltip == tooltip && tooltip.canBeDismissedOnTimeout()) {
          hideCurrent(null, null, null);
        }
      }
    }, tooltip.getDismissDelay());
  }


  public Color getTextForeground(boolean awtTooltip) {
    return useGraphite(awtTooltip) ? Color.white : UIUtil.getToolTipForeground();
  }

  public Color getLinkForeground(boolean awtTooltip) {
    return useGraphite(awtTooltip) ? new Color(209, 209, 255) : Color.blue;
  }

  public Color getTextBackground(boolean awtTooltip) {
    return useGraphite(awtTooltip) ? new Color(100, 100, 100, 230) : UIUtil.getToolTipBackground();
  }

  public String getUlImg(boolean awtTooltip) {
    return useGraphite(awtTooltip) ? "/general/mdot-white.png" : "/general/mdot.png";
  }

  public Color getBorderColor(boolean awtTooltip) {
    return useGraphite(awtTooltip) ? getTextBackground(awtTooltip).darker() : Color.darkGray;
  }

  public boolean isOwnBorderAllowed(boolean awtTooltip) {
    return !awtTooltip;
  }

  public boolean isOpaqueAllowed(boolean awtTooltip) {
    return !awtTooltip;
  }

  public Font getTextFont(boolean awtTooltip) {
    return UIManager.getFont("ToolTip.font");
  }

  private boolean isUseSystemLook() {
    boolean useSystem;

    if ("default".equalsIgnoreCase(myMode.asString())) {
      useSystem = true;
    } else if ("system".equalsIgnoreCase(myMode.asString())) {
      useSystem = true;
    } else if ("graphite".equalsIgnoreCase(myMode.asString())) {
      useSystem = false;
    } else {
      useSystem = false;
    }
    return useSystem;
  }

  private boolean hideCurrent(@Nullable MouseEvent me, AnAction action, AnActionEvent event) {
    myShowRequest = null;
    myQueuedComponent = null;
    myQueuedTooltip = null;

    if (myCurrentTooltip == null) return true;

    if (myCurrentTipUi != null) {
      boolean isInside = me != null && myCurrentTipUi.isInsideBalloon(me);
      boolean canAutoHide = myCurrentTooltip.canAutohideOn(new TooltipEvent(me, isInside, action, event));
      boolean implicitMouseMove = me != null && (me.getID() == MouseEvent.MOUSE_MOVED || me.getID() == MouseEvent.MOUSE_EXITED || me.getID() == MouseEvent.MOUSE_ENTERED);

      if (!canAutoHide || (myCurrentTooltip.isExplicitClose() && implicitMouseMove)) {
        if (myHideRunnable != null) {
          myHideRunnable = null;
        }
        return false;
      }
    }

    myHideRunnable = new Runnable() {
      @Override
      public void run() {
        if (myHideRunnable != null) {
          hideCurrentNow();
          myHideRunnable = null;
        }
      }
    };

    if (me != null) {
      myAlarm.addRequest(myHideRunnable, Registry.intValue("ide.tooltip.autoDismissDeadZone"));
    } else {
      myHideRunnable.run();
      myHideRunnable = null;
    }

    return true;
  }

  private void hideCurrentNow() {
    if (myCurrentTipUi != null) {
      myCurrentTipUi.hide();
      myCurrentTooltip.onHidden();
      myShowDelay = false;
      myAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          myShowDelay = true;
        }
      }, Registry.intValue("ide.tooltip.reshowDelay"));
    }

    myShowRequest = null;
    myCurrentTooltip = null;
    myCurrentTipUi = null;
    myCurrentComponent = null;
    myQueuedComponent = null;
    myQueuedTooltip = null;
    myCurrentEvent = null;
    myCurrentTipIsCentered = false;
    myX = -1;
    myY = -1;
  }

  private void processEnabled() {
    if (myIsEnabled.asBoolean()) {
      ToolTipManager.sharedInstance().setEnabled(false);
    } else {
      ToolTipManager.sharedInstance().setEnabled(true);
    }
  }

  @Override
  public void disposeComponent() {
  }

  public static IdeTooltipManager getInstance() {
    return ApplicationManager.getApplication().getComponent(IdeTooltipManager.class);
  }

  private boolean useGraphite(boolean awtHint) {
    return !isUseSystemLook() && awtHint;
  }

  public void hide(IdeTooltip tooltip) {
    if (myCurrentTooltip == tooltip || tooltip == null) {
      hideCurrent(null, null, null);
    }
  }

  public void cancelAutoHide() {
    myHideRunnable = null;
  }


  public static JEditorPane initPane(@NonNls String text, final HintHint hintHint, @Nullable JLayeredPane layeredPane) {
    final Ref<Dimension> prefSize = new Ref<Dimension>(null);
    String htmlBody = getHtmlBody(text);
    htmlBody = UIUtil.convertSpace2Nbsp(htmlBody);
    text = "<html><head>" +
           UIUtil.getCssFontDeclaration(hintHint.getTextFont(), hintHint.getTextForeground(), hintHint.getLinkForeground(), hintHint.getUlImg()) +
           "</head><body>" +
           htmlBody +
           "</body></html>";

    final JEditorPane pane = new JEditorPane() {
      @Override
      public Dimension getPreferredSize() {
        Dimension s = prefSize.get() != null ? new Dimension(prefSize.get()) : super.getPreferredSize();
        Border b = getBorder();
        if (b != null) {
          Insets insets = b.getBorderInsets(this);
          if (insets != null) {
            s.width += insets.left + insets.right;
            s.height += insets.top + insets.bottom;
          }
        }
        return s;
      }
    };

    final HTMLEditorKit.HTMLFactory factory = new HTMLEditorKit.HTMLFactory() {
      @Override
      public View create(Element elem) {
        AttributeSet attrs = elem.getAttributes();
        Object elementName = attrs.getAttribute(AbstractDocument.ElementNameAttribute);
        Object o = (elementName != null) ? null : attrs.getAttribute(StyleConstants.NameAttribute);
        if (o instanceof HTML.Tag) {
          HTML.Tag kind = (HTML.Tag)o;
          if (kind == HTML.Tag.HR) {
            return new CustomHrView(elem, hintHint.getTextForeground());
          }
        }
        return super.create(elem);
      }
    };

    HTMLEditorKit kit = new HTMLEditorKit() {
      @Override
      public ViewFactory getViewFactory() {
        return factory;
      }
    };
    pane.setEditorKit(kit);
    pane.setText(text);

    pane.setCaretPosition(0);
    pane.setEditable(false);

    if (hintHint.isOwnBorderAllowed()) {
      setBorder(pane);
      setColors(pane);
    }
    else {
      pane.setBorder(null);
    }

    if (hintHint.isAwtTooltip()) {
      Dimension size = layeredPane != null ? layeredPane.getSize() : new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
      int fitWidth = (int)(size.width * 0.8);
      Dimension prefSizeOriginal = pane.getPreferredSize();
      if (prefSizeOriginal.width > fitWidth) {
        pane.setSize(new Dimension(fitWidth, Integer.MAX_VALUE));
        Dimension fixedWidthSize = pane.getPreferredSize();
        prefSize.set(new Dimension(fitWidth, fixedWidthSize.height));
      }
      else {
        prefSize.set(new Dimension(prefSizeOriginal));
      }
    }

    pane.setOpaque(hintHint.isOpaqueAllowed());
    pane.setBackground(hintHint.getTextBackground());

    return pane;
  }

  public static void setColors(JComponent pane) {
    pane.setForeground(Color.black);
    pane.setBackground(HintUtil.INFORMATION_COLOR);
    pane.setOpaque(true);
  }

  public static void setBorder(JComponent pane) {
    pane.setBorder(
      BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.black), BorderFactory.createEmptyBorder(0, 5, 0, 5)));
  }

  public static String getHtmlBody(@NonNls String text) {
    String result = text;
    if (!text.startsWith("<html>")) {
      result = text.replaceAll("\n", "<br>");
    }
    else {
      final int bodyIdx = text.indexOf("<body>");
      final int closedBodyIdx = text.indexOf("</body>");
      if (bodyIdx != -1 && closedBodyIdx != -1) {
        result = text.substring(bodyIdx + "<body>".length(), closedBodyIdx);
      }
      else {
        text = StringUtil.trimStart(text, "<html>").trim();
        text = StringUtil.trimEnd(text, "</html>").trim();
        text = StringUtil.trimStart(text, "<body>").trim();
        text = StringUtil.trimEnd(text, "</body>").trim();
        result = text;
      }
    }



    return result.replaceAll("<font(.*?)>", "").replaceAll("</font>", "");
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "IDE Tooltip Manager";
  }

  public boolean isQueuedToShow(IdeTooltip tooltip) {
    return Comparing.equal(myQueuedTooltip, tooltip);
  }
}
