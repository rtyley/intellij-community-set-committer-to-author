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
package com.intellij.xdebugger.impl.ui;

import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.runners.RestartAction;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.util.ArrayUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.frame.XDebugViewBase;
import com.intellij.xdebugger.impl.frame.XFramesView;
import com.intellij.xdebugger.impl.frame.XVariablesView;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author spleaner
 */
public class XDebugSessionTab extends DebuggerSessionTabBase {
  private final String mySessionName;
  private final RunnerLayoutUi myUi;
  private XWatchesView myWatchesView;
  private final List<XDebugViewBase> myViews = new ArrayList<XDebugViewBase>();

  public XDebugSessionTab(@NotNull final Project project, @NotNull final String sessionName) {
    super(project);
    mySessionName = sessionName;

    myUi = RunnerLayoutUi.Factory.getInstance(project).create("Debug", "unknown!", sessionName, this);
    myUi.getDefaults().initTabDefaults(0, "Debug", null);
  }

  private static ActionGroup getActionGroup(final String id) {
    return (ActionGroup)ActionManager.getInstance().getAction(id);
  }

  private Content createConsoleContent() {
    return myUi.createContent(DebuggerContentInfo.CONSOLE_CONTENT, myConsole.getComponent(),
                              XDebuggerBundle.message("debugger.session.tab.console.content.name"), XDebuggerUIConstants.CONSOLE_TAB_ICON,
                              myConsole.getPreferredFocusableComponent());
  }

  private Content createVariablesContent(final XDebugSession session) {
    final XVariablesView variablesView = new XVariablesView(session, this);
    myViews.add(variablesView);
    return myUi.createContent(DebuggerContentInfo.VARIABLES_CONTENT, variablesView.getPanel(),
                              XDebuggerBundle.message("debugger.session.tab.variables.title"), XDebuggerUIConstants.VARIABLES_TAB_ICON, null);
  }

  private Content createWatchesContent(final XDebugSession session, final XDebugSessionData sessionData) {
    myWatchesView = new XWatchesView(session, this, sessionData);
    myViews.add(myWatchesView);
    Content watchesContent = myUi.createContent(DebuggerContentInfo.WATCHES_CONTENT, myWatchesView.getMainPanel(),
                                         XDebuggerBundle.message("debugger.session.tab.watches.title"), XDebuggerUIConstants.WATCHES_TAB_ICON, null);

    ActionGroup group = getActionGroup(XDebuggerActions.WATCHES_TREE_TOOLBAR_GROUP);
    watchesContent.setActions(group, ActionPlaces.DEBUGGER_TOOLBAR, myWatchesView.getTree());
    return watchesContent;
  }

  private Content createFramesContent(final XDebugSession session) {
    final XFramesView framesView = new XFramesView(session, this);
    myViews.add(framesView);
    Content framesContent = myUi.createContent(DebuggerContentInfo.FRAME_CONTENT, framesView.getMainPanel(),
                                               XDebuggerBundle.message("debugger.session.tab.frames.title"), XDebuggerUIConstants.FRAMES_TAB_ICON, null);
    final DefaultActionGroup framesGroup = new DefaultActionGroup();

    CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    framesGroup.add(actionsManager.createPrevOccurenceAction(framesView.getFramesList()));
    framesGroup.add(actionsManager.createNextOccurenceAction(framesView.getFramesList()));

    framesContent.setActions(framesGroup, ActionPlaces.DEBUGGER_TOOLBAR, framesView.getFramesList());
    return framesContent;
  }

  public XDebugSessionData saveData() {
    final List<String> watchExpressions = myWatchesView.getWatchExpressions();
    return new XDebugSessionData(ArrayUtil.toStringArray(watchExpressions));
  }

  public ExecutionConsole getConsole() {
    return myConsole;
  }

  public String getSessionName() {
    return mySessionName;
  }

  public void rebuildViews() {
    for (XDebugViewBase view : myViews) {
      view.rebuildView();
    }
  }

  public RunContentDescriptor attachToSession(final @NotNull XDebugSession session, final @Nullable ProgramRunner runner,
                                              final @Nullable  ExecutionEnvironment env,
                                              final @NotNull XDebugSessionData sessionData) {
    return initUI(session, sessionData, env, runner);
  }

  @NotNull
  private static ExecutionResult createExecutionResult(@NotNull final XDebugSession session) {
    final XDebugProcess debugProcess = session.getDebugProcess();
    ProcessHandler processHandler = debugProcess.getProcessHandler();
    processHandler.addProcessListener(new ProcessAdapter() {
      public void processTerminated(final ProcessEvent event) {
        ((XDebugSessionImpl)session).stopImpl();
      }
    });
    return new DefaultExecutionResult(debugProcess.createConsole(), processHandler);
  }

  public XWatchesView getWatchesView() {
    return myWatchesView;
  }

  private RunContentDescriptor initUI(final @NotNull XDebugSession session, final @NotNull XDebugSessionData sessionData,
                                      final @Nullable ExecutionEnvironment environment, final @Nullable ProgramRunner runner) {
    ExecutionResult executionResult = createExecutionResult(session);
    myConsole = executionResult.getExecutionConsole();
    myRunContentDescriptor = new RunContentDescriptor(myConsole, executionResult.getProcessHandler(), myUi.getComponent(), getSessionName());

    myUi.addContent(createFramesContent(session), 0, PlaceInGrid.left, false);
    myUi.addContent(createVariablesContent(session), 0, PlaceInGrid.center, false);
    myUi.addContent(createWatchesContent(session, sessionData), 0, PlaceInGrid.right, false);
    final Content consoleContent = createConsoleContent();
    myUi.addContent(consoleContent, 1, PlaceInGrid.bottom, false);
    attachNotificationTo(consoleContent);

    session.getDebugProcess().registerAdditionalContent(myUi);
    RunContentBuilder.addAdditionalConsoleEditorActions(myConsole, consoleContent);
    myUi.addContent(consoleContent, 0, PlaceInGrid.bottom, false);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myRunContentDescriptor;
    }

    DefaultActionGroup leftToolbar = new DefaultActionGroup();
    final Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();
    if (runner != null && environment != null) {
      RestartAction restartAction = new RestartAction(executor, runner, myRunContentDescriptor.getProcessHandler(), XDebuggerUIConstants.DEBUG_AGAIN_ICON,
                                                     myRunContentDescriptor, environment);
      leftToolbar.add(restartAction);
      restartAction.registerShortcut(myUi.getComponent());
    }

    leftToolbar.addAll(getActionGroup(XDebuggerActions.TOOL_WINDOW_LEFT_TOOLBAR_GROUP));

    //group.addSeparator();
    //addAction(group, DebuggerActions.EXPORT_THREADS);
    leftToolbar.addSeparator();

    leftToolbar.add(myUi.getOptions().getLayoutActions());

    leftToolbar.addSeparator();

    leftToolbar.add(PinToolwindowTabAction.getPinAction());
    leftToolbar.add(new CloseAction(executor, myRunContentDescriptor, getProject()));
    leftToolbar.add(new ContextHelpAction(executor.getHelpId()));

    DefaultActionGroup topToolbar = new DefaultActionGroup();
    topToolbar.addAll(getActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_GROUP));

    session.getDebugProcess().registerAdditionalActions(leftToolbar, topToolbar);
    myUi.getOptions().setLeftToolbar(leftToolbar, ActionPlaces.DEBUGGER_TOOLBAR);
    myUi.getOptions().setTopToolbar(topToolbar, ActionPlaces.DEBUGGER_TOOLBAR);

    if (environment != null) {
      final RunProfile runConfiguration = environment.getRunProfile();
      registerFileMatcher(runConfiguration);
      initLogConsoles(runConfiguration, myRunContentDescriptor.getProcessHandler());
    }

    rebuildViews();

    return myRunContentDescriptor;
  }

  public RunnerLayoutUi getUi() {
    return myUi;
  }

  @Nullable
  public RunContentDescriptor getRunContentDescriptor() {
    return myRunContentDescriptor;
  }
}
