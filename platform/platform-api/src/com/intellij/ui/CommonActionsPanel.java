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
package com.intellij.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class CommonActionsPanel extends JPanel {
  private final boolean myDecorateButtons;

  public enum Buttons {
    ADD, REMOVE, EDIT,  UP, DOWN;

    public static Buttons[] ALL = {ADD, REMOVE, EDIT,  UP, DOWN};

    Icon getIcon() {
      switch (this) {
        case ADD:    return IconUtil.getAddIcon();
        case EDIT:    return IconUtil.getEditIcon();
        case REMOVE: return IconUtil.getRemoveIcon();
        case UP:     return IconUtil.getMoveUpIcon();
        case DOWN:   return IconUtil.getMoveDownIcon();
      }
      return null;
    }

    MyActionButton createButton(final Listener listener, String name, Icon icon) {
      return new MyActionButton(this, listener, name == null ? StringUtil.capitalize(name().toLowerCase()) : name, icon);
    }

    public String getText() {
      return StringUtil.capitalize(name().toLowerCase());
    }

    public void performAction(Listener listener) {
      switch (this) {
        case ADD: listener.doAdd(); break;
        case EDIT: listener.doEdit(); break;
        case REMOVE: listener.doRemove(); break;
        case UP: listener.doUp(); break;
        case DOWN: listener.doDown(); break;
      }
    }
  }
  public interface Listener {
    void doAdd();
    void doRemove();
    void doUp();
    void doDown();
    void doEdit();

    class Adapter implements Listener {
      public void doAdd() {}
      public void doRemove() {}
      public void doUp() {}
      public void doDown() {}
      public void doEdit() {}
    }
  }

  private Map<Buttons, MyActionButton> myButtons = new HashMap<Buttons, MyActionButton>();
  private final AnActionButton[] myActions;

  CommonActionsPanel(ListenerFactory factory, @Nullable JComponent contextComponent, ActionToolbarPosition position,
                     @Nullable AnActionButton[] additionalActions, @Nullable Comparator<AnActionButton> buttonComparator,
                     String addName, String removeName, String moveUpName, String moveDownName, String editName,
                     Icon addIcon, Buttons... buttons) {
    super(new BorderLayout());
    final Listener listener = factory.createListener(this);
    AnActionButton[] actions = new AnActionButton[buttons.length];
    for (int i = 0; i < buttons.length; i++) {
      Buttons button = buttons[i];
      String name = null;
      switch (button) {
        case ADD:    name = addName;      break;        
        case EDIT:   name = editName;     break;
        case REMOVE: name = removeName;   break;
        case UP:     name = moveUpName;   break;
        case DOWN:   name = moveDownName; break;
      }
      final MyActionButton b = button.createButton(listener, name, button == Buttons.ADD && addIcon != null ? addIcon : button.getIcon());
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
    if (buttonComparator != null) {
      Arrays.sort(myActions, buttonComparator);
    }
    myDecorateButtons = UIUtil.isUnderAquaLookAndFeel() && position == ActionToolbarPosition.BOTTOM;

    final ActionManagerEx mgr = (ActionManagerEx)ActionManager.getInstance();
    final ActionToolbar toolbar = mgr.createActionToolbar(ActionPlaces.UNKNOWN,
                                                          new DefaultActionGroup(myActions),
                                                          position == ActionToolbarPosition.BOTTOM || position == ActionToolbarPosition.TOP,
                                                          myDecorateButtons);
    toolbar.getComponent().setOpaque(false);
    toolbar.getComponent().setBorder(null);
    add(toolbar.getComponent(), BorderLayout.CENTER);
  }

  @Override
  protected void paintComponent(Graphics g2) {
    final Graphics2D g = (Graphics2D)g2;
    if (myDecorateButtons) {
      MacUIUtil.drawToolbarDecoratorBackground(g, getWidth(), getHeight());
    } else {
      super.paintComponent(g);
    }
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
        if (button instanceof MyActionButton && ((MyActionButton)button).isRemoveButton()) {
          registerDeleteHook((MyActionButton)button);
        }
      }
    }
    
    super.addNotify(); // call after all to construct actions tooltips properly
  }

  private static void registerDeleteHook(final MyActionButton removeButton) {
    new AnAction("Delete Hook") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        removeButton.actionPerformed(e);
      }

      @Override
      public void update(AnActionEvent e) {
        final JComponent contextComponent = removeButton.getContextComponent();
        if (contextComponent instanceof JTable && ((JTable)contextComponent).isEditing()) {
          e.getPresentation().setEnabled(false);
          return;
        }
        final SpeedSearchSupply supply = SpeedSearchSupply.getSupply(contextComponent);
        if (supply != null && supply.isPopupActive()) {
          e.getPresentation().setEnabled(false);
          return;
        }
        removeButton.update(e);
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("DELETE", "BACK_SPACE"), removeButton.getContextComponent());
  }

  public void setEnabled(Buttons button, boolean enabled) {
    final MyActionButton b = myButtons.get(button);
    if (b != null) {
      b.setEnabled(enabled);
    }
  }

  static class MyActionButton extends AnActionButton implements DumbAware {
    private final Buttons myButton;
    private final Listener myListener;

    MyActionButton(Buttons button, Listener listener, String name, Icon icon) {
      super(name, name, icon);
      myButton = button;
      myListener = listener;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myButton.performAction(myListener);
    }

    @Override
    public ShortcutSet getShortcut() {
      return getCommonShortcut(myButton);
    }

    @Override
    public void updateButton(AnActionEvent e) {
      super.updateButton(e);
      if (!e.getPresentation().isEnabled()) return;

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
            || (myButton != Buttons.ADD && size == 0)
            || (myButton == Buttons.EDIT && (min != max || min == -1))) {
          e.getPresentation().setEnabled(false);
        }
        else {
          e.getPresentation().setEnabled(isEnabled());
        }
      }
    }

    //@Override
    //public boolean isEnabled() {
    //  if (myButton == Buttons.REMOVE) {
    //    final JComponent c = getContextComponent();
    //    if (c instanceof JTable && ((JTable)c).isEditing()) return false;
    //  }
    //  return super.isEnabled();
    //}

    boolean isAddButton() {
      return myButton == Buttons.ADD;
    }

    boolean isRemoveButton() {
      return myButton == Buttons.REMOVE;
    }
  }

  public static ShortcutSet getCommonShortcut(Buttons button) {
    switch (button) {
      case ADD: return CommonShortcuts.getNewForDialogs();
      case EDIT: return CustomShortcutSet.fromString("ENTER");
      case REMOVE: return CustomShortcutSet.fromString(SystemInfo.isMac ? "meta BACK_SPACE" : "alt DELETE");
      case UP: return CustomShortcutSet.fromString("alt UP");
      case DOWN: return CustomShortcutSet.fromString("alt DOWN");
    }
    return null;
  }

  interface ListenerFactory {
    Listener createListener(CommonActionsPanel panel);
  }
}
