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
package com.intellij.execution;

import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@State(name = "ExecutionTargetManager", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE, scheme = StorageScheme.DEFAULT)})
public class ExecutionTargetManagerImpl extends ExecutionTargetManager implements ProjectComponent, PersistentStateComponent<Element> {
  @NotNull private final Project myProject;
  @NotNull private final Object myActiveTargetLock = new Object();
  @Nullable private ExecutionTarget myActiveTarget;

  @Nullable private String mySavedActiveTargetId;

  public ExecutionTargetManagerImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  public Element getState() {
    synchronized (myActiveTargetLock) {
      Element state = new Element("state");

      String id = myActiveTarget == null ? mySavedActiveTargetId : myActiveTarget.getId();
      if (id != null) state.setAttribute("SELECTED_TARGET", id);
      return state;
    }
  }

  @Override
  public void loadState(Element state) {
    synchronized (myActiveTargetLock) {
      if (myActiveTarget == null && mySavedActiveTargetId == null) {
        mySavedActiveTargetId = state.getAttributeValue("SELECTED_TARGET");
      }
    }
  }

  @Override
  public void initComponent() {
    RunManagerImpl.getInstanceImpl(myProject).addRunManagerListener(new RunManagerAdapter() {
      @Override
      public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
        if (settings == RunManager.getInstance(myProject).getSelectedConfiguration()) {
          updateActiveTarget(settings);
        }
      }

      @Override
      public void runConfigurationSelected() {
        updateActiveTarget();
      }
    });
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return ExecutionTargetManager.class.getName();
  }

  @NotNull
  @Override
  public ExecutionTarget getActiveTarget() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    synchronized (myActiveTargetLock) {
      if (myActiveTarget == null) {
        updateActiveTarget();
      }
      return myActiveTarget;
    }
  }

  @Override
  public void setActiveTarget(@NotNull ExecutionTarget target) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    synchronized (myActiveTargetLock) {
      updateActiveTarget(RunManager.getInstance(myProject).getSelectedConfiguration(), target);
    }
  }

  private void updateActiveTarget() {
    updateActiveTarget(RunManager.getInstance(myProject).getSelectedConfiguration());
  }

  private void updateActiveTarget(@Nullable RunnerAndConfigurationSettings settings) {
    updateActiveTarget(settings, null);
  }

  private void updateActiveTarget(@Nullable RunnerAndConfigurationSettings settings, @Nullable ExecutionTarget toSelect) {
    List<ExecutionTarget> suitable = settings == null ? Collections.singletonList(DefaultExecutionTarget.INSTANCE)
                                                      : getTargetsFor(settings);
    synchronized (myActiveTargetLock) {
      if (toSelect == null) toSelect = myActiveTarget;

      int index = -1;
      if (toSelect != null) {
        index = suitable.indexOf(toSelect);
      }
      else if (mySavedActiveTargetId != null) {
        for (int i = 0, size = suitable.size(); i < size; i++) {
          if (suitable.get(i).getId().equals(mySavedActiveTargetId)) {
            index = i;
            break;
          }
        }
      }
      doSetActiveTarget(index >= 0 ? suitable.get(index) : ContainerUtil.getFirstItem(suitable, DefaultExecutionTarget.INSTANCE));
    }
  }

  private void doSetActiveTarget(@NotNull ExecutionTarget newTarget) {
    mySavedActiveTargetId = null;

    ExecutionTarget prev = myActiveTarget;
    myActiveTarget = newTarget;
    if (prev != null && !prev.equals(myActiveTarget)) {
      myProject.getMessageBus().syncPublisher(TOPIC).activeTargetChanged(myActiveTarget);
    }
  }

  @NotNull
  @Override
  public List<ExecutionTarget> getTargetsFor(@Nullable RunnerAndConfigurationSettings settings) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (settings == null) return Collections.emptyList();

    List<ExecutionTarget> result = new ArrayList<ExecutionTarget>();
    for (ExecutionTargetProvider eachTargetProvider : Extensions.getExtensions(ExecutionTargetProvider.EXTENSION_NAME)) {
      for (ExecutionTarget eachTarget : eachTargetProvider.getTargets(myProject, settings)) {
        if (canRun(settings, eachTarget)) result.add(eachTarget);
      }
    }
    return Collections.unmodifiableList(result);
  }

  @Override
  public void update() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    updateActiveTarget();
  }
}
