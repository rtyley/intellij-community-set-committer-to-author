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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
public final class ToggleFullScreenModeAction extends ToggleAction implements DumbAware {
  @NonNls
  private static final String PROP_BOUNDS_BEFORE_FULL_SCREEN="boundsBeforeFullScreen";

  private static IdeFrameImpl getFrame(AnActionEvent e){
    WindowManagerEx windowManagerEx=WindowManagerEx.getInstanceEx();
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    return windowManagerEx.getFrame(project);
  }

  public boolean isSelected(AnActionEvent e){
    IdeFrameImpl frame=getFrame(e);
    return frame != null && frame.getGraphicsConfiguration().getDevice().getFullScreenWindow()==frame;
  }

  public void setSelected(AnActionEvent e,boolean state){
    // Hide all all visible floating tool windows.
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if(project!=null){
      ToolWindowManager toolWindowManager=ToolWindowManager.getInstance(project);
      String[] ids=toolWindowManager.getToolWindowIds();
      for (String id : ids) {
        ToolWindow toolWindow = toolWindowManager.getToolWindow(id);
        if (ToolWindowType.FLOATING == toolWindow.getType() && toolWindow.isVisible()) {
          toolWindow.hide(null);
        }
      }
    }
    // Toggle full screen mode.
    IdeFrameImpl frame=getFrame(e);
    final Component focusedComponent=WindowManagerEx.getInstanceEx().getFocusedComponent(frame);
    GraphicsConfiguration graphicsConfiguration=frame.getGraphicsConfiguration();
    Rectangle bounds=graphicsConfiguration.getBounds();
    Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration);
    if(state){ // toggle full screen on
      frame.getRootPane().putClientProperty(PROP_BOUNDS_BEFORE_FULL_SCREEN,frame.getBounds());
      frame.dispose();
      frame.setUndecorated(true);
      frame.setBounds(bounds);
      frame.setVisible(true);
      graphicsConfiguration.getDevice().setFullScreenWindow(frame);
    }else{ // toggle full screen off
      Rectangle boundsBeforeFullScreen=(Rectangle)frame.getRootPane().getClientProperty(PROP_BOUNDS_BEFORE_FULL_SCREEN);
      frame.dispose();
      graphicsConfiguration.getDevice().setFullScreenWindow(null);
      frame.setUndecorated(false);
      if(boundsBeforeFullScreen!=null){
        frame.setBounds(boundsBeforeFullScreen);
        frame.setVisible(true);
      }else{
        frame.setBounds(
          bounds.x+insets.left,
          bounds.y+insets.top,
          bounds.width-insets.left-insets.right,
          bounds.height-insets.top-insets.bottom
        );
        frame.setVisible(true);
        frame.setExtendedState(Frame.MAXIMIZED_BOTH);
      }
    }
    if(focusedComponent!=null){
      SwingUtilities.invokeLater(
        new Runnable(){
          public void run(){
            focusedComponent.requestFocus();
          }
        }
      );
    }
  }

  public void update(AnActionEvent e){
    super.update(e);
    IdeFrameImpl frame = getFrame(e);
    final boolean operational = frame != null && frame.getGraphicsConfiguration().getDevice().isFullScreenSupported() &&
                                (!SystemInfo.isMac || SystemInfo.isMacOSSnowLeopard);
    e.getPresentation().setVisible(operational);
    e.getPresentation().setEnabled(operational);
  }
}
