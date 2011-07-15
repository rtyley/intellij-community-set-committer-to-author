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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformIcons;
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
        case ADD:    return SystemInfo.isMac ? PlatformIcons.TABLE_ADD_ROW : PlatformIcons.ADD_ICON;
        case REMOVE: return SystemInfo.isMac ? PlatformIcons.TABLE_REMOVE_ROW : PlatformIcons.DELETE_ICON;
        case UP:     return SystemInfo.isMac ? PlatformIcons.TABLE_MOVE_ROW_UP : PlatformIcons.MOVE_UP_ICON;
        case DOWN:   return SystemInfo.isMac ? PlatformIcons.TABLE_MOVE_ROW_DOWN : PlatformIcons.MOVE_DOWN_ICON;
      }
      return null;
    }

    TableActionButton createButton(final Listener listener) {
      return new TableActionButton(this, listener);
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

  private Map<Buttons, TableActionButton> myButtons = new HashMap<Buttons, TableActionButton>();
  private final AnActionButton[] myActions;

  AddRemoveUpDownPanel(Listener listener, @Nullable JComponent contextComponent, boolean isHorizontal,
                              @Nullable AnActionButton[] additionalActions, Buttons... buttons) {
    super(new BorderLayout());
    AnActionButton[] actions = new AnActionButton[buttons.length];
    for (int i = 0; i < buttons.length; i++) {
      Buttons button = buttons[i];
      final TableActionButton b = button.createButton(listener);
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

  @Override
  public void addNotify() {
    final JRootPane pane = getRootPane();
    for (AnActionButton button : myActions) {
      final Shortcut shortcut = button.getShortcut();
      if (shortcut != null) {
        button.registerCustomShortcutSet(new CustomShortcutSet(shortcut), pane);
      }
    }
    super.addNotify(); // call after all to construct actions tooltips properly
  }

  public void setEnabled(Buttons button, boolean enabled) {
    final TableActionButton b = myButtons.get(button);
    if (b != null) {
      b.setEnabled(enabled);
    }
  }

  public AddRemoveUpDownPanel(Listener listener, @Nullable JComponent contentPane, @Nullable AnActionButton[] additionalActions) {
    this(listener, contentPane, false, additionalActions, Buttons.ADD, Buttons.REMOVE, Buttons.UP, Buttons.DOWN);
  }

  //public static void hideButtons(JPanel panelWithButtons, Buttons... buttons) {
  //  final AddRemoveUpDownPanel panel = UIUtil.findComponentOfType(panelWithButtons, AddRemoveUpDownPanel.class);
  //  assert panel != null : "Seems this panel isn't a result of EditableRowTable.wrapToTableWithButtons() wrapper";
  //  for (Buttons button : buttons) {
  //    final TableActionButton actionButton = panel.myButtons.get(button);
  //    if (actionButton != null) {
  //      actionButton.setVisible(false);
  //      actionButton.setEnabled(false);
  //    }
  //  }
  //}

  static class TableActionButton extends AnActionButton {
    private final Buttons myButton;
    private final Listener myListener;

    TableActionButton(Buttons button, Listener listener) {
      super(button.getText(), button.getText(), button.getIcon());
      myButton = button;
      myListener = listener;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myButton.performAction(myListener);
    }

    @Override
    public Shortcut getShortcut() {
      switch (myButton) {
        case ADD: return KeyboardShortcut.fromString("alt A");
        case REMOVE: return KeyboardShortcut.fromString("alt DELETE");
        case UP: return KeyboardShortcut.fromString("alt UP");
        case DOWN: return KeyboardShortcut.fromString("alt DOWN");
      }
      return null;
    }
  }
}
