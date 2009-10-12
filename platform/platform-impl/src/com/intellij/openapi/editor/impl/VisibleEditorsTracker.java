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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class VisibleEditorsTracker extends CommandAdapter implements ApplicationComponent{
  private final Set<Editor> myEditorsVisibleOnCommandStart = new HashSet<Editor>();
  private long myCurrentCommandStart;
  private long myLastCommandFinish;

  public static VisibleEditorsTracker getInstance() {
    return ApplicationManager.getApplication().getComponent(VisibleEditorsTracker.class);
  }


  public VisibleEditorsTracker(CommandProcessor commandProcessor) {
    commandProcessor.addCommandListener(this);
  }

  @NotNull
  public String getComponentName() {
    return "VisibleEditorsTracker";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public boolean wasEditorVisibleOnCommandStart(Editor editor){
    return myEditorsVisibleOnCommandStart.contains(editor);
  }

  public long getCurrentCommandStart() { return myCurrentCommandStart; }

  public long getLastCommandFinish() { return myLastCommandFinish; }

  public void commandStarted(CommandEvent event) {
    for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
      if (editor.getComponent().isShowing()) {
        myEditorsVisibleOnCommandStart.add(editor);
      }

      ((ScrollingModelImpl)editor.getScrollingModel()).commandStarted();
      myCurrentCommandStart = System.currentTimeMillis();
    }
  }

  public void commandFinished(CommandEvent event) {
    myEditorsVisibleOnCommandStart.clear();
    myLastCommandFinish = System.currentTimeMillis();
  }
}
