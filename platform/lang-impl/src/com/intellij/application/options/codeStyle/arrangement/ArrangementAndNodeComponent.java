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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsNodeVisitor;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ArrangementNodeComponent Component} for showing {@link ArrangementCompositeMatchCondition composite nodes}.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 10:51 AM
 */
public class ArrangementAndNodeComponent extends JPanel implements ArrangementNodeComponent {

  private static final int BUBBLE_CONNECTOR_LENGTH = 10;

  @NotNull private final List<ArrangementNodeComponent> myComponents = new ArrayList<ArrangementNodeComponent>();

  @NotNull private final ArrangementCompositeMatchCondition mySetting;
  @Nullable private      Rectangle                          myScreenBounds;

  public ArrangementAndNodeComponent(@NotNull ArrangementCompositeMatchCondition setting,
                                     @NotNull ArrangementNodeComponentFactory factory,
                                     @NotNull ArrangementNodeDisplayManager manager)
  {
    mySetting = setting;
    setLayout(null);
    int x = 0;
    final Map<Object, ArrangementMatchCondition> operands = new HashMap<Object, ArrangementMatchCondition>();
    ArrangementSettingsNodeVisitor visitor = new ArrangementSettingsNodeVisitor() {
      @Override
      public void visit(@NotNull ArrangementAtomMatchCondition node) {
        operands.put(node.getValue(), node);
      }

      @Override
      public void visit(@NotNull ArrangementCompositeMatchCondition node) {
        operands.put(node, node);
      }
    };
    for (ArrangementMatchCondition operand : setting.getOperands()) {
      operand.invite(visitor);
    }

    List<Object> ordered = manager.sort(operands.keySet());
    for (Object key : ordered) {
      ArrangementMatchCondition operand = operands.get(key);
      assert operand != null;
      ArrangementNodeComponent component = factory.getComponent(operand);
      myComponents.add(component);
      JComponent uiComponent = component.getUiComponent();
      Dimension size = uiComponent.getPreferredSize();
      add(uiComponent);
      uiComponent.setBounds(x, 0, size.width, size.height);
      x += size.width + BUBBLE_CONNECTOR_LENGTH;
    }
  }

  @NotNull
  @Override
  public ArrangementMatchCondition getMatchCondition() {
    return mySetting;
  }

  @NotNull
  @Override
  public JComponent getUiComponent() {
    return this;
  }

  @Nullable
  @Override
  public Rectangle getScreenBounds() {
    return myScreenBounds;
  }

  @Override
  public void setScreenBounds(@Nullable Rectangle bounds) {
    myScreenBounds = bounds;
  }

  @Override
  public void setSelected(boolean selected) {
    for (ArrangementNodeComponent component : myComponents) {
      component.setSelected(selected);
    }
  }

  @Override
  public ArrangementNodeComponent getNodeComponentAt(@NotNull RelativePoint point) {
    if (myScreenBounds == null) {
      return null;
    }
    Point screenPoint = point.getScreenPoint();
    if (!myScreenBounds.contains(screenPoint)) {
      return null;
    }

    for (ArrangementNodeComponent component : myComponents) {
      Rectangle screenBounds = component.getScreenBounds();
      if (screenBounds != null && screenBounds.contains(screenPoint)) {
        return component;
      }
    }
    return null;
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getPreferredSize() {
    int myWidth = 0;
    int myHeight = 0;
    Component[] components = getComponents();
    for (Component component : components) {
      Dimension size = component.getPreferredSize();
      myWidth += size.width;
      myHeight = Math.max(size.height, myHeight);
    }
    if (components.length > 1) {
      myWidth += (components.length - 1) * BUBBLE_CONNECTOR_LENGTH;
    }
    return new Dimension(myWidth, myHeight);
  }

  @Override
  public void paint(Graphics g) {
    Point point = ArrangementConfigUtil.getLocationOnScreen(this);
    if (point != null) {
      Rectangle bounds = getBounds();
      myScreenBounds = new Rectangle(point.x, point.y, bounds.width, bounds.height);
    }
    super.paint(g);
    
    Component[] components = getComponents();
    if (components.length < 2) {
      return;
    }

    // Draw node connectors.
    int x = 0;
    g.setColor(UIManager.getColor("Tree.hash"));
    for (int i = 0; i < components.length; i++) {
      Component component = components[i];
      Rectangle bounds = component.getBounds();
      if (myScreenBounds != null && i < myComponents.size()) {
        myComponents.get(i).setScreenBounds(new Rectangle(
          myScreenBounds.x + bounds.x, myScreenBounds.y + bounds.y, bounds.width, bounds.height
        ));
      }
      int y = bounds.y + bounds.height / 2;
      x += bounds.width;
      if (i < components.length - 1) {
        g.drawLine(x, y, x + BUBBLE_CONNECTOR_LENGTH, y);
      }
      x += BUBBLE_CONNECTOR_LENGTH;
    }
  }

  @Override
  public Rectangle handleMouseMove(@NotNull MouseEvent event) {
    Point location = event.getLocationOnScreen();
    for (ArrangementNodeComponent component : myComponents) {
      Rectangle bounds = component.getScreenBounds();
      if (bounds != null && bounds.contains(location)) {
        return component.handleMouseMove(event);
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return String.format("(%s)", StringUtil.join(myComponents, " and "));
  }
}
