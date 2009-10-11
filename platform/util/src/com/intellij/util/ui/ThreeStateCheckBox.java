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
package com.intellij.util.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;

/**
 * @author spleaner
 */
public class ThreeStateCheckBox extends JCheckBox {
  private State myState;
  private boolean myThirdStateEnabled = true;

  public static enum State {
    SELECTED, NOT_SELECTED, DONT_CARE
  }

  public ThreeStateCheckBox() {
    this(null, null, State.DONT_CARE);
  }

  public ThreeStateCheckBox(final State initial) {
    this(null, null, initial);
  }

  public ThreeStateCheckBox(final String text) {
    this(text, null, State.DONT_CARE);
  }

  public ThreeStateCheckBox(final String text, final State initial) {
    this(text, null, initial);
  }

  public ThreeStateCheckBox(final String text, final Icon icon) {
    this(text, icon, State.DONT_CARE);
  }

  public ThreeStateCheckBox(final String text, final Icon icon, final State initial) {
    super(text, icon);

    setModel(new ToggleButtonModel() {
      @Override
      public void setSelected(boolean selected) {
        myState = nextState();
        fireStateChanged();
        fireItemStateChanged(new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, this, ItemEvent.SELECTED));
      }

      @Override
      public boolean isSelected() {
        return myState == State.SELECTED;
      }
    });

    setState(initial);
  }

  private State nextState() {
    switch (myState) {
      case SELECTED:
        return State.NOT_SELECTED;
      case NOT_SELECTED:
        if (myThirdStateEnabled) {
          return State.DONT_CARE;
        }
        else {
          return State.SELECTED;
        }
      default:
        return State.SELECTED;
    }
  }

  public boolean isThirdStateEnabled() {
    return myThirdStateEnabled;
  }

  public void setThirdStateEnabled(final boolean thirdStateEnabled) {
    myThirdStateEnabled = thirdStateEnabled;
  }

  @Override
  public void setSelected(final boolean b) {
    setState(b ? State.SELECTED : State.NOT_SELECTED);
  }

  public void setState(State state) {
    myState = state;
    repaint();
  }

  public State getState() {
    return myState;
  }

  @Override
  public void paint(final Graphics g) {
    super.paint(g);
    switch (getState()) {
      case DONT_CARE:
        final Rectangle r = getBounds();
        final Insets i = getInsets();

        Icon icon = getIcon();
        if (icon == null) {
          icon = UIManager.getIcon("CheckBox.icon");
        }

        if (icon != null) {
          //final Color selected = UIManager.getColor("CheckBox.focus");
          //if (selected != null) {
          //  g.setColor(selected);
          //}

          final int width1 = icon.getIconWidth();
          final int height1 = r.height - i.top - i.bottom;
          final int yoffset = height1 / 2 - 1;
          final int xoffset = width1 / 2 - width1 / 5;

          g.fillRect(xoffset + i.left, yoffset + i.top, width1 / 3, 2);
        }
        break;
      default:
        break;
    }
  }
}
