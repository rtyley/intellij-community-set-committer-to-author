/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.mac.MacMainFrameDecorator;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public abstract class IdeFrameDecorator implements Disposable {
  protected IdeFrameImpl myFrame;

  protected IdeFrameDecorator(IdeFrameImpl frame) {
    myFrame = frame;
  }

  public abstract boolean isInFullScreen();

  public abstract void toggleFullScreen(boolean state);

  @Override
  public void dispose() {
    myFrame = null;
  }

  @Nullable
  public static IdeFrameDecorator decorate(@NotNull IdeFrameImpl frame) {
    if (SystemInfo.isMac) {
      return new MacMainFrameDecorator(frame, PlatformUtils.isAppCode());
    }
    else if (SystemInfo.isWindows) {
      return new WinMainFrameDecorator(frame);
    }
    else if (SystemInfo.isXWindow) {
      if (XlibUiUtil.isFullScreenSupported()) {
        return new EWMHFrameDecorator(frame);
      }
    }

    return null;
  }

  // Swing-based decorator
  private static class WinMainFrameDecorator extends IdeFrameDecorator {
    private WinMainFrameDecorator(@NotNull IdeFrameImpl frame) {
      super(frame);
    }

    @Override
    public boolean isInFullScreen() {
      if (myFrame == null) return false;

      Rectangle frameBounds = myFrame.getBounds();
      GraphicsDevice device = ScreenUtil.getScreenDevice(frameBounds);
      return device != null && device.getDefaultConfiguration().getBounds().equals(frameBounds) && myFrame.isUndecorated();
    }

    @Override
    public void toggleFullScreen(boolean state) {
      if (myFrame == null) return;

      GraphicsDevice device = ScreenUtil.getScreenDevice(myFrame.getBounds());
      if (device == null) return;

      try {
        myFrame.getRootPane().putClientProperty(ScreenUtil.DISPOSE_TEMPORARY, Boolean.TRUE);
        if (state) {
          myFrame.getRootPane().putClientProperty("oldBounds", myFrame.getBounds());
        }
        myFrame.dispose();
        myFrame.setUndecorated(state);
      }
      finally {
        if (state) {
          myFrame.setBounds(device.getDefaultConfiguration().getBounds());
        }
        else {
          Object o = myFrame.getRootPane().getClientProperty("oldBounds");
          if (o instanceof Rectangle) {
            myFrame.setBounds((Rectangle)o);
          }
        }
        myFrame.setVisible(true);
        myFrame.getRootPane().putClientProperty(ScreenUtil.DISPOSE_TEMPORARY, null);
      }
    }
  }

  // Extended WM Hints-based decorator
  private static class EWMHFrameDecorator extends IdeFrameDecorator {
    private EWMHFrameDecorator(IdeFrameImpl frame) {
      super(frame);
    }

    @Override
    public boolean isInFullScreen() {
      return myFrame != null && XlibUiUtil.isInFullScreenMode(myFrame);
    }

    @Override
    public void toggleFullScreen(boolean state) {
      if (myFrame != null) {
        XlibUiUtil.toggleFullScreenMode(myFrame);
      }
    }
  }
}
