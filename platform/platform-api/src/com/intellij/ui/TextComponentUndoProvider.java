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

package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapManager;

import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.JTextComponent;
import javax.swing.undo.UndoManager;

/**
 * @author yole
 */
public class TextComponentUndoProvider implements Disposable {
  private final JTextComponent myTextComponent;
  private final UndoManager myUndoManager = new UndoManager();
  private UndoableEditListener myUndoableEditListener;

  public TextComponentUndoProvider(final JTextComponent textComponent) {
    myTextComponent = textComponent;

    myUndoableEditListener = new UndoableEditListener() {
      public void undoableEditHappened(UndoableEditEvent e) {
        myUndoManager.addEdit(e.getEdit());
      }
    };
    myTextComponent.getDocument().addUndoableEditListener(myUndoableEditListener);
    com.intellij.openapi.keymap.Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
    Shortcut[] undoShortcuts = activeKeymap.getShortcuts(IdeActions.ACTION_UNDO);
    Shortcut[] redoShortcuts = activeKeymap.getShortcuts(IdeActions.ACTION_REDO);

    AnAction undoAction = new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myUndoManager.canUndo()) {
          myUndoManager.undo();
        }
      }
    };

    AnAction redoAction = new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myUndoManager.canRedo()) {
          myUndoManager.redo();
        }
      }
    };

    undoAction.registerCustomShortcutSet(new CustomShortcutSet(undoShortcuts), myTextComponent);
    redoAction.registerCustomShortcutSet(new CustomShortcutSet(redoShortcuts), myTextComponent);


  }

  public void dispose() {
    if (myUndoableEditListener != null) {
      myTextComponent.getDocument().removeUndoableEditListener(myUndoableEditListener);
      myUndoableEditListener = null;
    }
  }
}
