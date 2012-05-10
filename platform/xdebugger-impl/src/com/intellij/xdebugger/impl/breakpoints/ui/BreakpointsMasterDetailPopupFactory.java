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
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.ide.actions.ShowPopupMenuAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.util.ItemWrapper;
import com.intellij.ui.popup.util.MasterDetailPopupBuilder;
import com.intellij.util.PlatformIcons;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointListener;
import com.intellij.xdebugger.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: zajac
 * Date: 5/9/12
 * Time: 3:11 AM
 * To change this template use File | Settings | File Templates.
 */
public class BreakpointsMasterDetailPopupFactory {

  private final List<BreakpointPanelProvider> myBreakpointPanelProviders;
  private Project myProject;

  public BreakpointsMasterDetailPopupFactory(Project project) {
    myProject = project;
    myBreakpointPanelProviders = new ArrayList<BreakpointPanelProvider>();
    for (DebuggerSupport debuggerSupport : DebuggerSupport.getDebuggerSupports()) {
      myBreakpointPanelProviders.add(debuggerSupport.getBreakpointPanelProvider());
    }
    Collections.sort(myBreakpointPanelProviders, new Comparator<BreakpointPanelProvider>() {
      @Override
      public int compare(BreakpointPanelProvider breakpointPanelProvider, BreakpointPanelProvider breakpointPanelProvider1) {
        return breakpointPanelProvider.getPriority() - breakpointPanelProvider1.getPriority();
      }
    });
  }

  public static BreakpointsMasterDetailPopupFactory getInstance(Project project) {
    return ServiceManager.getService(project, BreakpointsMasterDetailPopupFactory.class);
  }

  public JBPopup createPopup(@Nullable Object initialBreakpoint) {
    DefaultListModel model = createBreakpointsItemsList();
    final JBList list = new JBList(model);
    list.getEmptyText().setText("No Bookmarks");

    DefaultActionGroup actions = getActions();

    final JBPopup popup = new MasterDetailPopupBuilder(myProject).
      setActionsGroup(actions).
      setList(list).
      setDelegate(new MasterDetailPopupBuilder.Delegate() {
        @Override
        public String getTitle() {
          return "Breakpoints";
        }

        @Override
        public void handleMnemonic(KeyEvent e, Project project, JBPopup popup) {
          //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void itemRemoved(ItemWrapper item, Project project) {
          //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean hasItemsWithMnemonic(Project project) {
          return false;
        }

      }).createMasterDetailPopup();

    return popup;
  }

  private DefaultActionGroup getActions() {
    DefaultActionGroup actions = new DefaultActionGroup();
    final DefaultActionGroup breakpointTypes = new DefaultActionGroup();
    for (BreakpointPanelProvider provider : myBreakpointPanelProviders) {
      breakpointTypes.addAll(provider.getAddBreakpointActions(myProject));
    }
    actions.add(new AnAction("Add Breakpoint", null, PlatformIcons.ADD_ICON) {
      @Override
      public void actionPerformed(AnActionEvent e) {

        JBPopupFactory.getInstance()
          .createActionGroupPopup("Choose type", breakpointTypes, e.getDataContext(), JBPopupFactory.ActionSelectionAid.NUMBERING, false)
          .showInFocusCenter();
      }
    });

    return actions;
  }

  private DefaultListModel createBreakpointsItemsList() {
    DefaultListModel model = new DefaultListModel();
    ArrayList<BreakpointItem> items = new ArrayList<BreakpointItem>();
    for (BreakpointPanelProvider panelProvider : myBreakpointPanelProviders) {
      panelProvider.provideBreakpointItems(myProject, items);
    }
    for (BreakpointItem item : items) {
      model.addElement(item);
    }
    return model;
  }
}
