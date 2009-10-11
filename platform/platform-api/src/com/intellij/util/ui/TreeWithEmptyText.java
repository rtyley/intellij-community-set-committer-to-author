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

import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeModel;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

/**
 * @author yole
*/
public class TreeWithEmptyText extends Tree {
  private String myEmptyText = "";
  private final SimpleColoredComponent myEmptyTextComponent = new SimpleColoredComponent();
  private final ArrayList<ActionListener> myEmptyTextClickListeners = new ArrayList<ActionListener>();
  private static final int EMPTY_TEXT_TOP = 20;

  public TreeWithEmptyText(final TreeModel model) {
    super(model);
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(final MouseEvent e) {
        if (e.getButton() == 1 && e.getClickCount() == 1 && isModelEmpty()) {
          ActionListener actionListener = findEmptyTextActionListenerAt(e.getPoint());
          if (actionListener != null) {
            actionListener.actionPerformed(new ActionEvent(this, 0, ""));
          }
        }
      }
    });
    addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(final MouseEvent e) {
        if (isModelEmpty()) {
          if (findEmptyTextActionListenerAt(e.getPoint()) != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          }
          else {
            setCursor(Cursor.getDefaultCursor());
          }
        }
      }
    });
    myEmptyTextComponent.setFont(UIUtil.getLabelFont());
  }

  @Nullable
  private ActionListener findEmptyTextActionListenerAt(final Point point) {
    final Rectangle bounds = getBounds();
    final Dimension size = myEmptyTextComponent.getPreferredSize();
    int x = ((bounds.width - size.width)) / 2;
    if (new Rectangle(x, EMPTY_TEXT_TOP, bounds.width, bounds.height).contains(point)) {
      int index = myEmptyTextComponent.findFragmentAt(point.x - x);
      if (index >= 0 && index < myEmptyTextClickListeners.size()) {
        return myEmptyTextClickListeners.get(index);
      }
    }
    return null;
  }

  public String getEmptyText() {
    return myEmptyText;
  }

  public void setEmptyText(final String emptyText) {
    clearEmptyText();
    appendEmptyText(emptyText, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public void clearEmptyText() {
    myEmptyTextComponent.clear();
    myEmptyTextClickListeners.clear();
    myEmptyText = "";
  }

  public void appendEmptyText(String text, SimpleTextAttributes attrs) {
    appendEmptyText(text, attrs, null);
  }

  public void appendEmptyText(String text, SimpleTextAttributes attrs, ActionListener listener) {
    myEmptyText += text;
    myEmptyTextComponent.append(text, attrs);
    myEmptyTextClickListeners.add(listener);
  }

  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);
    if (isModelEmpty() && myEmptyText.length() > 0) {
      myEmptyTextComponent.setFont(getFont());
      myEmptyTextComponent.setBackground(getBackground());
      myEmptyTextComponent.setForeground(getForeground());
      final Rectangle bounds = getBounds();
      final Dimension size = myEmptyTextComponent.getPreferredSize();
      myEmptyTextComponent.setBounds(0, 0, size.width, size.height);
      int x = ((bounds.width - size.width)) / 2;
      Graphics g2 = g.create(bounds.x + x, bounds.y + EMPTY_TEXT_TOP, size.width, size.height);
      try {
        myEmptyTextComponent.paint(g2);
      }
      finally {
        g2.dispose();
      }
    }
  }

  public boolean isModelEmpty() {
    final TreeModel model = getModel();
    return model.getChildCount(model.getRoot()) == 0;
  }
}