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
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.HelpID;
import com.intellij.debugger.ui.breakpoints.actions.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import org.jdom.Element;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 26, 2005
 */
public class LineBreakpointFactory extends BreakpointFactory{
  public Breakpoint createBreakpoint(Project project, final Element element) {
    return new LineBreakpoint(project);
  }

  public Icon getIcon() {
    return LineBreakpoint.ICON;
  }

  public Icon getDisabledIcon() {
    return LineBreakpoint.DISABLED_ICON;
  }

  public BreakpointPanel createBreakpointPanel(Project project, final DialogWrapper parentDialog) {
    final BreakpointPanel panel = new BreakpointPanel(project, new LineBreakpointPropertiesPanel(project), new BreakpointPanelAction[]{
      new SwitchViewAction(),
      new GotoSourceAction(project) {
        public void actionPerformed(ActionEvent e) {
          super.actionPerformed(e);
          parentDialog.close(DialogWrapper.OK_EXIT_CODE);
        }
      },
      new ViewSourceAction(project),
      new RemoveAction(project),
      new ToggleGroupByMethodsAction(),
      new ToggleGroupByClassesAction(),
      new ToggleFlattenPackagesAction(),
    }, getBreakpointCategory(), DebuggerBundle.message("line.breakpoints.tab.title"), HelpID.LINE_BREAKPOINTS);
    return panel;
  }
  
  public Key<LineBreakpoint> getBreakpointCategory() {
    return LineBreakpoint.CATEGORY;
  }
}
