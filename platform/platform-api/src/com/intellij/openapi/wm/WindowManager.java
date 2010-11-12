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
package com.intellij.openapi.wm;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class WindowManager {
  public static WindowManager getInstance(){
    return ApplicationManager.getApplication().getComponent(WindowManager.class);
  }

  /**
   */
  public abstract void doNotSuggestAsParent(Window window);

  /**
   * Gets first window (starting from the active one) that can be parent for other windows.
   * Note, that this method returns only subclasses of dialog or frame.
   * @return <code>null</code> if there is no currently active window or there are any window
   * that can be parent.
   */
  public abstract Window suggestParentWindow(Project project);

  /**
   * Get the status bar for the project's main frame
   * @param project
   * @return
   */
  public abstract StatusBar getStatusBar(Project project);

  /**
   * Get the status bar for the component, it may be either the main status bar or the status bar for an undocked window
   * @param c
   * @return
   */
  public abstract StatusBar getStatusBar(@NotNull Component c);

  public abstract JFrame getFrame(Project project);

  public abstract IdeFrame getIdeFrame(Project project);

  /**
   * Tests whether the specified rectangle is inside of screen bounds. Method uses its own heuristic test.
   * Test passes if intersection of screen bounds and specified rectangle isn't empty and its height and
   * width are not less then some value. Note, that all parameters are in screen coordinate system.
   * The method properly works in mutlimonitor configuration.
   */
  public abstract boolean isInsideScreenBounds(int x, int y, int width);

  /**
   * Tests whether the specified point is inside of screen bounds. Note, that
   * all parameters are in screen coordinate system.
   * The method properly works in mutlimonitor configuration.
   */
  public abstract boolean isInsideScreenBounds(int x,int y);

  public abstract IdeFrame[] getAllFrames();

  public abstract void addListener(WindowManagerListener listener);
  public abstract void removeListener(WindowManagerListener listener);
}
