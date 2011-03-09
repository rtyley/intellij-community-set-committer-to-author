/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.navigationToolbar;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

/**
* @author Konstantin Bulenkov
*/
class NavBarItem extends SimpleColoredComponent implements DataProvider{
  private final String myText;
  private final SimpleTextAttributes myAttributes;
  private final int myIndex;
  private final Icon myIcon;
  private final NavBarPanel myPanel;
  private Object myObject;
  private final boolean isPopupElement;

  public NavBarItem(NavBarPanel panel, Object object, int idx) {
    myPanel = panel;
    myObject = object;
    myIndex = idx;
    isPopupElement = idx == -1;
    if (object != null) {
      Icon closedIcon = NavBarPresentation.getIcon(object, false);
      Icon openIcon = NavBarPresentation.getIcon(object, true);

      if (closedIcon == null && openIcon != null) closedIcon = openIcon;
      if (openIcon == null && closedIcon != null) openIcon = closedIcon;
      if (openIcon == null) {
        openIcon = closedIcon = EmptyIcon.create(5);
      }
      final NavBarPresentation presentation = myPanel.getPresentation();
      myText = NavBarPresentation.getPresentableText(object, myPanel.getWindow());
      myIcon = wrapIcon(openIcon, closedIcon, idx);
      myAttributes = presentation.getTextAttributes(object, false);
    } else {
      myText = "Sample";
      myIcon = Icons.DIRECTORY_OPEN_ICON;
      myAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }

    setIpad(new Insets(1, 2, 1, 2));
    update();
  }

  /**
   * item for node popup
   * @param panel
   * @param object
   */
  public NavBarItem(NavBarPanel panel, Object object) {
    this(panel, object, -1);
  }

  public Object getObject() {
    return myObject;
  }

  public SimpleTextAttributes getAttributes() {
    return myAttributes;
  }

  void update() {
    clear();

    setIcon(myIcon);
    final Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    boolean focused = isPopupElement ? myPanel.isNodePopupActive() : focusOwner == myPanel;

    final NavBarModel model = myPanel.getModel();
    boolean selected = isPopupElement ? myPanel.isSelectedInPopup(myObject) : model.getSelectedIndex() == myIndex;

    setPaintFocusBorder(!focused && selected && !isPopupElement);
    setFocusBorderAroundIcon(false);

    setBackground(selected && focused
                  ? UIUtil.getListSelectionBackground()
                  : (UIUtil.isUnderGTKLookAndFeel() ? Color.WHITE : UIUtil.getListBackground()));

    final Color fg = selected && focused
                     ? UIUtil.getListSelectionForeground()
                     : model.getSelectedIndex() < myIndex && model.getSelectedIndex() != -1
                       ? UIUtil.getInactiveTextColor()
                       : myAttributes.getFgColor();

    final Color bg = selected && focused ? UIUtil.getListSelectionBackground() : myAttributes.getBgColor();

    append(myText, new SimpleTextAttributes(bg, fg, myAttributes.getWaveColor(), myAttributes.getStyle()));

    repaint();
  }

  private Icon wrapIcon(final Icon openIcon, final Icon closedIcon, final int idx) {
    return new Icon() {
      public void paintIcon(Component c, Graphics g, int x, int y) {
        if (myPanel.getModel().getSelectedIndex() == idx && myPanel.isNodePopupActive()) {
          openIcon.paintIcon(c, g, x, y);
        }
        else {
          closedIcon.paintIcon(c, g, x, y);
        }
      }

      public int getIconWidth() {
        return openIcon.getIconWidth();
      }

      public int getIconHeight() {
        return openIcon.getIconHeight();
      }
    };
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.PROJECT.is(dataId)) {
      return myPanel.getProject();
    }

    if (LangDataKeys.PSI_ELEMENT.is(dataId)) {
      return myObject instanceof PsiElement ? myObject : null;
    }

    if (LangDataKeys.PSI_FILE.is(dataId)) {
      return myObject instanceof PsiElement ? ((PsiElement)myObject).getContainingFile() : null;
    }

    return null;
  }
}
