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
package com.intellij.ui.components;

import com.intellij.ui.ComponentWithExpandableItems;
import com.intellij.ui.ExpandableItemsHandler;
import com.intellij.ui.ExpandableItemsHandlerFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.EmptyTextHelper;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Collection;

/**
 * @author Anton Makeev
 * @author Konstantin Bulenkov
 */
public class JBList extends JList implements ComponentWithEmptyText, ComponentWithExpandableItems<Integer> {
  private EmptyTextHelper myEmptyTextHelper;
  private ExpandableItemsHandler<Integer> myExpandableItemsHandler;

  public JBList() {
    init();
  }

  public JBList(ListModel dataModel) {
    super(dataModel);
    init();
  }

  public JBList(Object... listData) {
    super(listData);
    init();
  }

  public JBList(Collection items) {
    this(ArrayUtil.toObjectArray(items));
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    myEmptyTextHelper.paint(g);
  }

  private void init() {
    setSelectionBackground(UIUtil.getListSelectionBackground());
    setSelectionForeground(UIUtil.getListSelectionForeground());

    myEmptyTextHelper = new EmptyTextHelper(this) {
      @Override
      protected boolean isEmpty() {
        return JBList.this.isEmpty();
      }
    };

    myExpandableItemsHandler = ExpandableItemsHandlerFactory.install(this);
  }

  public boolean isEmpty() {
    return getItemsCount() == 0;
  }

  public int getItemsCount() {
    ListModel model = getModel();
    return model == null ? 0 : model.getSize();
  }

  public String getEmptyText() {
    return myEmptyTextHelper.getEmptyText();
  }

  public void setEmptyText(String emptyText) {
    myEmptyTextHelper.setEmptyText(emptyText);
  }

  public void setEmptyText(String emptyText, SimpleTextAttributes attrs) {
    myEmptyTextHelper.setEmptyText(emptyText, attrs);
  }

  public void clearEmptyText() {
    myEmptyTextHelper.clearEmptyText();
  }

  public void appendEmptyText(String text, SimpleTextAttributes attrs) {
    myEmptyTextHelper.appendEmptyText(text, attrs);
  }

  public void appendEmptyText(String text, SimpleTextAttributes attrs, ActionListener listener) {
    myEmptyTextHelper.appendEmptyText(text, attrs, listener);
  }

  @NotNull
  public ExpandableItemsHandler<Integer> getExpandableItemsHandler() {
    return myExpandableItemsHandler;
  }

  public <T> void installCellRenderer(final @NotNull NotNullFunction<T, JComponent> fun) {
    setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        @SuppressWarnings({"unchecked"})
        final JComponent comp = fun.fun((T)value);  
        comp.setOpaque(true);
        if (isSelected) {
          comp.setBackground(list.getSelectionBackground());
          comp.setForeground(list.getSelectionForeground());
        } else {
          comp.setBackground(list.getBackground());
          comp.setForeground(list.getForeground());
        }
        return comp;
      }
    });
  }
}
