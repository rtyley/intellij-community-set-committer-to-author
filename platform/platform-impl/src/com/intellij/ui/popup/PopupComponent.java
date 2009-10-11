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
package com.intellij.ui.popup;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ReflectionUtil;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;

public interface PopupComponent {

  Logger LOG = Logger.getInstance("#com.intellij.ui.popup.PopupComponent");

  void hide(boolean dispose);

  void show();

  Window getWindow();

  interface Factory {
    PopupComponent getPopup(Component owner, Component content, int x, int y);

    class AwtDefault implements Factory {
      public PopupComponent getPopup(Component owner, Component content, int x, int y) {
        return new AwtPopupWrapper(PopupFactory.getSharedInstance().getPopup(owner, content, x, y));
      }
    }

    class AwtHeavyweight implements Factory {
      public PopupComponent getPopup(Component owner, Component content, int x, int y) {
        final PopupFactory factory = PopupFactory.getSharedInstance();

        try {
          final Method method = PopupFactory.class.getDeclaredMethod("setPopupType", int.class);
          method.setAccessible(true);
          method.invoke(factory, 2);

        }
        catch (Throwable e) {
          LOG.error(e);
        }

        return new AwtPopupWrapper(factory.getPopup(owner, content, x, y));
      }
    }

    class Dialog implements Factory {
      public PopupComponent getPopup(Component owner, Component content, int x, int y) {
        return new DialogPopupWrapper(owner, content, x, y);
      }
    }
  }

  class DialogPopupWrapper implements PopupComponent {
    private JDialog myDialog;

    public DialogPopupWrapper(Component owner, Component content, int x, int y) {
      if (!owner.isShowing()) {
        throw new IllegalArgumentException("Popup owner must be showing");
      }

      final Window wnd = owner instanceof Window ? (Window)owner: SwingUtilities.getWindowAncestor(owner);
      if (wnd instanceof Frame) {
        myDialog = new JDialog((Frame)wnd, false);
      } else {
        myDialog = new JDialog((Dialog)wnd, false);
      }

      myDialog.getContentPane().setLayout(new BorderLayout());
      myDialog.getContentPane().add(content, BorderLayout.CENTER);

      myDialog.setUndecorated(true);
      myDialog.pack();
      myDialog.setLocation(x, y);
    }

    public Window getWindow() {
      return myDialog;
    }

    public void hide(boolean dispose) {
      myDialog.setVisible(false);
      if (dispose) {
        myDialog.dispose();
      }
    }

    public void show() {
      myDialog.setVisible(true);
    }
  }

  class AwtPopupWrapper implements PopupComponent {

    private Popup myPopup;

    public AwtPopupWrapper(Popup popup) {
      myPopup = popup;
    }

    public void hide(boolean dispose) {
      myPopup.hide();
    }

    public void show() {
      myPopup.show();
    }

    public Window getWindow() {
      final Component c = (Component)ReflectionUtil.getField(Popup.class, myPopup, Component.class, "component");
      return c instanceof JWindow ? (JWindow)c : null;
    }
  }

}