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
package com.intellij.ide;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Application;
import com.intellij.util.Alarm;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.FocusManager;
import javax.swing.*;
import javax.swing.plaf.basic.BasicPopupMenuUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.dnd.DragGestureRecognizer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * This class listens event from ProjectManager and cleanup some
 * internal Swing references.
 *
 * @author Vladimir Kondratyev
 */
public final class SwingCleanuper implements ApplicationComponent{
  private final Alarm myAlarm;

  /** Invoked by reflection
   * @param projectManager   */
  SwingCleanuper(ProjectManager projectManager){
    myAlarm=new Alarm();

    projectManager.addProjectManagerListener(
      new ProjectManagerAdapter(){
        public void projectOpened(final Project project) {
          myAlarm.cancelAllRequests();
        }
        // Swing keeps references to the last focused component inside DefaultKeyboardFocusManager.realOppositeComponent
        // which is used to compose next focus event. Actually this component could be an editors or a tool window. To fix this
        // memory leak we (if the project was closed and a new one was not opened yet) request focus to the status bar and after
        // the focus events have passed the queue, we put 'null' to the DefaultKeyboardFocusManager.realOppositeComponent field.
        public void projectClosed(final Project project){
          myAlarm.cancelAllRequests();
          myAlarm.addRequest(
            new Runnable() {
              public void run() {
                // request focus into some focusable somponent inside IdeFrame
                final IdeFrameImpl frame;
                final Window window=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
                if(window instanceof IdeFrameImpl){
                  frame=(IdeFrameImpl)window;
                }else{
                  frame=(IdeFrameImpl)SwingUtilities.getAncestorOfClass(IdeFrameImpl.class,window);
                }
                if(frame!=null){
                  final Application app = ApplicationManager.getApplication();
                  if (app != null && app.isActive()) {
                    ((JComponent)frame.getStatusBar()).requestFocus();
                  }
                }

                SwingUtilities.invokeLater(
                  new Runnable() {
                    public void run() {

                      // KeyboardFocusManager.newFocusOwner
                      try{
                        //noinspection HardCodedStringLiteral
                        final Field newFocusOwnerField = KeyboardFocusManager.class.getDeclaredField("newFocusOwner");
                        newFocusOwnerField.setAccessible(true);
                        newFocusOwnerField.set(null, null);
                      }
                      catch(final Exception exc){
                        // Ignore
                      }

                      // Clear "realOppositeComponent", "realOppositeWindow"
                      final KeyboardFocusManager focusManager = FocusManager.getCurrentKeyboardFocusManager();
                      resetField(focusManager, Component.class, "realOppositeComponent");
                      resetField(focusManager, Window.class, "realOppositeWindow");


                      // Memory leak on static field in BasicPopupMenuUI

                      try {
                        final Object helperObject = ReflectionUtil.getField(BasicPopupMenuUI.class, null, Object.class, "menuKeyboardHelper");
                        if (null != helperObject) {
                          resetField(helperObject, Component.class, "lastFocused");
                        }
                      }
                      catch (Exception e) {
                        // Ignore
                      }

                      // Memory leak on javax.swing.TransferHandler$SwingDragGestureRecognizer.component

                      try{
                        final Field recognizerField = TransferHandler.class.getDeclaredField("recognizer");
                        recognizerField.setAccessible(true);
                        final Object recognizerObject = recognizerField.get(null);
                        if(recognizerObject!=null){ // that is memory leak
                          final Method setComponentMethod = DragGestureRecognizer.class.getDeclaredMethod("setComponent", Component.class);
                          setComponentMethod.invoke(recognizerObject,new Object[]{null});
                        }
                      }
                      catch (Exception e){
                        // Ignore
                      }
                      try {
                        fixJTextComponentMemoryLeak();
                      } catch(NoSuchFieldException e) {
                        // JDK 1.5
                      } catch(Exception e) {
                        // Ignore
                      }

                      focusManager.setGlobalCurrentFocusCycleRoot(null); //Remove focus leaks

                      try {
                        final Method m = KeyboardFocusManager.class.getDeclaredMethod("setGlobalFocusOwner", Component.class);
                        m.setAccessible(true);
                        m.invoke(focusManager, new Object[]{null});
                      }
                      catch (Exception e) {
                        // Ignore
                      }
                      try {
                        final Field f = KeyboardFocusManager.class.getDeclaredField("newFocusOwner");
                        f.setAccessible(true);
                        f.set(null, null);
                      }
                      catch (Exception e) {
                        // Ignore
                      }

                      try {
                        final Field f = KeyboardFocusManager.class.getDeclaredField("permanentFocusOwner");
                        f.setAccessible(true);
                        f.set(null, null);
                      }
                      catch (Exception e) {
                        // Ignore
                      }

                      try {
                        final Field f = KeyboardFocusManager.class.getDeclaredField("currentFocusCycleRoot");
                        f.setAccessible(true);
                        f.set(null, null);
                      }
                      catch (Exception e) {
                        // Ignore
                      }

                    }
                  }
                );
              }
            },
            2500
          );
        }
      }
    );
  }

  private static void resetField(Object object, Class type, @NonNls String name) {
    try {
      ReflectionUtil.resetField(object, ReflectionUtil.findField(object.getClass(), type, name));
    }
    catch (Exception e) {
      // Ignore
    }
  }

  public final void disposeComponent(){}

  @NotNull
  public final String getComponentName(){
    return "SwingCleanuper";
  }

  public final void initComponent() { }

  private static void fixJTextComponentMemoryLeak() throws NoSuchFieldException, IllegalAccessException {
    //noinspection HardCodedStringLiteral
    final Field focusedComponentField = JTextComponent.class.getDeclaredField("focusedComponent");
    focusedComponentField.setAccessible(true);
    final JTextComponent component = (JTextComponent)focusedComponentField.get(null);
    if (component != null && !component.isDisplayable()){
      focusedComponentField.set(null, null);
    }
  }

}
