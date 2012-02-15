/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache Licensevent, Version 2.0 (the "License");
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
package com.intellij.designer.designSurface.tools;

import com.intellij.designer.designSurface.EditableArea;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * @author Alexander Lobas
 */
public abstract class InputTool {
  protected static final int STATE_NONE = 0;
  protected static final int STATE_INIT = 1;
  protected static final int STATE_DRAG = 2;
  protected static final int STATE_DRAG_IN_PROGRESS = 3;
  protected static final int STATE_INVALID = 4;

  private ToolProvider myToolProvider;
  protected EditableArea myArea;
  private Object myCommand;
  private boolean myActive;
  private boolean myCanUnload = true;
  protected int myState;
  protected int myCurrentScreenX;
  protected int myCurrentScreenY;
  protected InputEvent myInputEvent;
  protected int myButton;
  protected int myStartScreenX;
  protected int myStartScreenY;
  private boolean myCanPastThreshold;

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // State
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public void activate() {
    resetState();
    myState = STATE_INIT;
    myActive = true;
  }

  public void deactivate() {
    myActive = false;
    setCommand(null);
  }

  protected void resetState() {
    myCurrentScreenX = 0;
    myCurrentScreenY = 0;
    myInputEvent = null;
    myButton = 0;
    myStartScreenX = 0;
    myStartScreenY = 0;
    myCanPastThreshold = false;
  }

  public final void setToolProvider(ToolProvider provider) {
    myToolProvider = provider;
  }

  public final void setArea(@Nullable EditableArea area) {
    if (myArea != area) {
      setCursor(null);
      myArea = area;

      if (myArea != null) {
        Point mouseLocation = MouseInfo.getPointerInfo().getLocation();
        SwingUtilities.convertPointFromScreen(mouseLocation, myArea.getNativeComponent());
        myCurrentScreenX = mouseLocation.x;
        myCurrentScreenY = mouseLocation.y;
      }

      refreshCursor();
    }
  }

  protected final boolean unloadWhenFinished() {
    return myCanUnload;
  }

  public final void setUnloadWhenFinished(boolean value) {
    myCanUnload = value;
  }

  protected final void setCommand(@Nullable Object command) {
    myCommand = command;
    refreshCursor();
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Cursor
  //
  //////////////////////////////////////////////////////////////////////////////////////////
  private Cursor myDefaultCursor;
  private Cursor myDisabledCursor;

  protected final void setCursor(@Nullable Cursor cursor) {
    if (myArea != null) {
      myArea.setCursor(cursor);
    }
  }

  public void refreshCursor() {
    if (myActive) {
      setCursor(calculateCursor());
    }
  }

  @Nullable
  protected Cursor calculateCursor() {
    if (myState == STATE_NONE) {
      return null;
    }
    if (myCommand == null) {
      return getDisabledCursor();
    }
    return getDefaultCursor();
  }

  protected final Cursor getDefaultCursor() {
    return myDefaultCursor;
  }

  public final void setDefaultCursor(@Nullable Cursor cursor) {
    if (myDefaultCursor != cursor) {
      myDefaultCursor = cursor;
      refreshCursor();
    }
  }

  protected final Cursor getDisabledCursor() {
    return myDisabledCursor == null ? getDefaultCursor() : myDisabledCursor;
  }

  public final void setDisabledCursor(@Nullable Cursor cursor) {
    if (myDisabledCursor != cursor) {
      myDisabledCursor = cursor;
      refreshCursor();
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Keyboard
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public void keyTyped(KeyEvent event, EditableArea area) throws Exception {
  }

  public void keyPressed(KeyEvent event, EditableArea area) throws Exception {
  }

  public void keyReleased(KeyEvent event, EditableArea area) throws Exception {
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Mouse
  //
  //////////////////////////////////////////////////////////////////////////////////////////
  private static final int DRAG_THRESHOLD = 5;

  private void setEvent(MouseEvent event) {
    myCurrentScreenX = event.getX();
    myCurrentScreenY = event.getY();
    myButton = event.getButton();
    myInputEvent = event;
  }

  private boolean movedPastThreshold() {
    if (!myCanPastThreshold) {
      myCanPastThreshold =
        Math.abs(myStartScreenX - myCurrentScreenX) > DRAG_THRESHOLD
        || Math.abs(myStartScreenY - myCurrentScreenY) > DRAG_THRESHOLD;
    }
    return myCanPastThreshold;
  }

  protected void handleButtonDown(int button) {
  }

  protected void handleButtonUp(int button) {
  }

  protected void handleMove() {
  }

  protected void handleDrag() {
  }

  protected void handleDragStarted() {
  }

  protected void handleDragInProgress() {
  }

  protected void handleDoubleClick(int button) {
  }

  protected void handleAreaExited() {
  }

  protected void handleFinished() {
    if (myCanUnload) {
      myToolProvider.loadDefaultTool();
    }
    else {
      deactivate();
      activate();
    }
  }

  public void mouseDown(MouseEvent event, EditableArea area) throws Exception {
    setArea(area);
    setEvent(event);
    myStartScreenX = event.getX();
    myStartScreenY = event.getY();
    handleButtonDown(event.getButton());
  }

  public void mouseUp(MouseEvent event, EditableArea area) throws Exception {
    setArea(area);
    setEvent(event);
    handleButtonUp(event.getButton());
  }

  public void mouseMove(MouseEvent event, EditableArea area) throws Exception {
    setArea(area);
    setEvent(event);

    if (myState == STATE_DRAG_IN_PROGRESS) {
      handleDragInProgress();
    }
    else {
      handleMove();
    }
  }

  public void mouseDrag(MouseEvent event, EditableArea area) throws Exception {
    setArea(area);
    boolean wasDragging = movedPastThreshold();
    setEvent(event);
    handleDrag();

    if (movedPastThreshold()) {
      if (!wasDragging) {
        handleDragStarted();
      }
      handleDragInProgress();
    }
  }

  public void mouseDoubleClick(MouseEvent event, EditableArea area) throws Exception {
    setArea(area);
    setEvent(event);
    handleDoubleClick(event.getButton());
  }

  public void mouseEntered(MouseEvent event, EditableArea area) throws Exception {
    setEvent(event);

    if (myArea != null) {
      handleAreaExited();
    }

    setArea(area);
  }

  public void mouseExited(MouseEvent event, EditableArea area) throws Exception {
    if (myArea == area) {
      setEvent(event);
      handleAreaExited();
      setArea(null);
    }
  }
}