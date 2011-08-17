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
package com.intellij.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
class AddRemoveUpDownPanel extends JPanel {
  public static enum Buttons {
    ADD, REMOVE, UP, DOWN;

    public static Buttons[] ALL = {ADD, REMOVE, UP, DOWN};

    Icon getIcon() {
      switch (this) {
        case ADD:    return IconUtil.getAddRowIcon();
        case REMOVE: return IconUtil.getRemoveRowIcon();
        case UP:     return IconUtil.getMoveRowUpIcon();
        case DOWN:   return IconUtil.getMoveRowDownIcon();
      }
      return null;
    }

    MyActionButton createButton(final Listener listener, String name) {
      return new MyActionButton(this, listener, name == null ? StringUtil.capitalize(name().toLowerCase()) : name);
    }

    public String getText() {
      return StringUtil.capitalize(name().toLowerCase());
    }

    public void performAction(Listener listener) {
      switch (this) {
        case ADD:
          listener.doAdd();
          break;
        case REMOVE:
          listener.doRemove();
          break;
        case UP:
          listener.doUp();
          break;
        case DOWN:
          listener.doDown();
          break;
      }
    }
  }
  public interface Listener {
    void doAdd();
    void doRemove();
    void doUp();
    void doDown();    

    class Adapter implements Listener {
      public void doAdd() {}
      public void doRemove() {}
      public void doUp() {}
      public void doDown() {}
    }
  }

  private Map<Buttons, MyActionButton> myButtons = new HashMap<Buttons, MyActionButton>();
  private final AnActionButton[] myActions;

  AddRemoveUpDownPanel(ListenerFactory factory, @Nullable JComponent contextComponent, boolean isHorizontal,
                       @Nullable AnActionButton[] additionalActions,
                       String addName, String removeName, String moveUpName,String moveDownName,
                       Buttons... buttons) {
    super(new BorderLayout());
    final Listener listener = factory.createListener(this);
    AnActionButton[] actions = new AnActionButton[buttons.length];
    for (int i = 0; i < buttons.length; i++) {
      Buttons button = buttons[i];
      String name = null;
      switch (button) {
        case ADD:    name = addName;      break;
        case REMOVE: name = removeName;   break;
        case UP:     name = moveUpName;   break;
        case DOWN:   name = moveDownName; break;
      }
      final MyActionButton b = button.createButton(listener, name);
      actions[i] = b;
      myButtons.put(button, b);
    }
    if (additionalActions != null && additionalActions.length > 0) {
      final ArrayList<AnActionButton> allActions = new ArrayList<AnActionButton>(Arrays.asList(actions));
      allActions.addAll(Arrays.asList(additionalActions));
      actions = allActions.toArray(new AnActionButton[allActions.size()]);
    }
    myActions = actions;
    for (AnActionButton action : actions) {
      action.setContextComponent(contextComponent);
    }
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN,
                                                                                  new DefaultActionGroup(myActions),
                                                                                  isHorizontal);
    toolbar.getComponent().setBorder(null);
    add(toolbar.getComponent(), BorderLayout.CENTER);
  }

  public AnActionButton getAnActionButton(Buttons button) {
    return myButtons.get(button);
  }

  @Override
  public void addNotify() {
    final JRootPane pane = getRootPane();
    for (AnActionButton button : myActions) {
      final ShortcutSet shortcut = button.getShortcut();
      if (shortcut != null) {
        if (button instanceof MyActionButton
            && ((MyActionButton)button).isAddButton()
            && UIUtil.isDialogRootPane(pane)) {
          button.registerCustomShortcutSet(shortcut, pane);
        } else {
          button.registerCustomShortcutSet(shortcut, button.getContextComponent());
        }
      }
    }
    super.addNotify(); // call after all to construct actions tooltips properly
  }

  public void setEnabled(Buttons button, boolean enabled) {
    final MyActionButton b = myButtons.get(button);
    if (b != null) {
      b.setEnabled(enabled);
    }
  }

  static class MyActionButton extends AnActionButton {
    private final Buttons myButton;
    private final Listener myListener;

    MyActionButton(Buttons button, Listener listener, String name) {
      super(name, name, button.getIcon());
      myButton = button;
      myListener = listener;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myButton.performAction(myListener);
    }

    @Override
    public ShortcutSet getShortcut() {
      switch (myButton) {
        case ADD: return CommonShortcuts.getNewForDialogs();
        case REMOVE: return CustomShortcutSet.fromString("DELETE");
        case UP: return CustomShortcutSet.fromString("alt UP");
        case DOWN: return CustomShortcutSet.fromString("alt DOWN");
      }
      return null;
    }

    @Override
    public void updateButton(AnActionEvent e) {
      final JComponent c = getContextComponent();
      if (c instanceof JTable || c instanceof JList) {
        final ListSelectionModel model = c instanceof JTable ? ((JTable)c).getSelectionModel() 
                                                             : ((JList)c).getSelectionModel();
        final int size = c instanceof JTable ? ((JTable)c).getRowCount()  
                                             : ((JList)c).getModel().getSize();
        final int min = model.getMinSelectionIndex();
        final int max = model.getMaxSelectionIndex();
        
        if ((myButton == Buttons.UP && min < 1)
          || (myButton == Buttons.DOWN && max == size - 1)
          || (myButton != Buttons.ADD && size == 0)) {
          e.getPresentation().setEnabled(false);
        } else {
          e.getPresentation().setEnabled(isEnabled());
        }
      }
    }

    @Override
    public boolean isEnabled() {
      if (myButton == Buttons.REMOVE) {
        final JComponent c = getContextComponent();
        if (c instanceof JTable && ((JTable)c).isEditing()) return false;
      }
      return super.isEnabled();
    }

    boolean isAddButton() {
      return myButton == Buttons.ADD;
    }
  }

  interface ListenerFactory {
    Listener createListener(AddRemoveUpDownPanel panel);
  }
}
