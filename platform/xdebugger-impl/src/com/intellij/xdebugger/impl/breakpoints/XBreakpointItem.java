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
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.XLightBreakpointPropertiesPanel;

import javax.swing.*;

/**
* Created with IntelliJ IDEA.
* User: intendia
* Date: 10.05.12
* Time: 1:14
* To change this template use File | Settings | File Templates.
*/
class XBreakpointItem extends BreakpointItem {
  private final XBreakpoint<?> myBreakpoint;

  public XBreakpointItem(XBreakpoint<?> breakpoint) {
    myBreakpoint = breakpoint;
  }

  @Override
  public void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected) {
    setupGenericRenderer(renderer);
  }

  @Override
  public void setupRenderer(ColoredTreeCellRenderer renderer) {
    setupGenericRenderer(renderer);
  }

  protected void setupGenericRenderer(SimpleColoredComponent renderer) {
    //renderer.setIcon(getIcon());
    final SimpleTextAttributes attributes =
      myBreakpoint.isEnabled() ? SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES;
    renderer.append(XBreakpointUtil.getShortText(myBreakpoint), attributes);
  }

  private Icon getIcon() {
    return ((XBreakpointBase)myBreakpoint).getIcon();
  }

  @Override
  public String speedSearchText() {
    return ((XBreakpointBase)myBreakpoint).getType().getDisplayText(myBreakpoint);
  }

  @Override
  public String footerText() {
    return ((XBreakpointBase)myBreakpoint).getType().getDisplayText(myBreakpoint);
  }

  @Override
  public void updateDetailView(DetailView panel) {
    Project project = ((XBreakpointBase)myBreakpoint).getProject();

    XLightBreakpointPropertiesPanel<XBreakpoint<?>> propertiesPanel =
      new XLightBreakpointPropertiesPanel<XBreakpoint<?>>(project, getManager(), myBreakpoint, true);
    propertiesPanel.loadProperties();
    panel.setDetailPanel(propertiesPanel.getMainPanel());

    XSourcePosition sourcePosition = myBreakpoint.getSourcePosition();
    if (sourcePosition != null) {
      showInEditor(panel, sourcePosition.getFile(), sourcePosition.getLine());
    } else {
      panel.clearEditor();
    }
  }

  private XBreakpointManagerImpl getManager() {
    return ((XBreakpointBase)myBreakpoint).getBreakpointManager();
  }

  @Override
  public boolean allowedToRemove() {
    return !getManager().isDefaultBreakpoint(myBreakpoint);
  }

  @Override
  public void removed(Project project) {
    final XBreakpointManagerImpl breakpointManager = getManager();
    new WriteAction() {
      protected void run(final Result result) {
        breakpointManager.removeBreakpoint(myBreakpoint);
      }
    }.execute();

  }

  @Override
  public Object getBreakpoint() {
    return myBreakpoint;
  }

  @Override
  public boolean isEnabled() {
    return myBreakpoint.isEnabled();
  }

  @Override
  public void setEnabled(boolean state) {
    myBreakpoint.setEnabled(state);
  }
}
