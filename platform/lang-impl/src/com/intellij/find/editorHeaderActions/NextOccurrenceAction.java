package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.EditorComboBox;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

/**
* Created by IntelliJ IDEA.
* User: zajac
* Date: 05.03.11
* Time: 10:40
* To change this template use File | Settings | File Templates.
*/
public class NextOccurrenceAction extends EditorHeaderAction implements DumbAware {
  public NextOccurrenceAction(EditorSearchComponent editorSearchComponent, EditorComboBox editorComboBox) {
    super(editorSearchComponent);
    copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_OCCURENCE));
    ArrayList<Shortcut> shortcuts = new ArrayList<Shortcut>();
    ContainerUtil.addAll(shortcuts, ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_NEXT).getShortcutSet().getShortcuts());
    ContainerUtil
      .addAll(shortcuts, ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN).getShortcutSet().getShortcuts());
    shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), null));

    registerShortcutsForComponent(shortcuts, editorComboBox, this);
  }

  public void actionPerformed(final AnActionEvent e) {
    getEditorSearchComponent().searchForward();
  }

  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(getEditorSearchComponent().hasMatches());
  }
}
