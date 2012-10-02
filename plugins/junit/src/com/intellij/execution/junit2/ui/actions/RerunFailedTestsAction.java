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

package com.intellij.execution.junit2.ui.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.actions.JavaRerunFailedTestsAction;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.TestMethods;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.ComponentContainer;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexey
 */
public class RerunFailedTestsAction extends JavaRerunFailedTestsAction {

  public RerunFailedTestsAction(@NotNull ComponentContainer componentContainer) {
    super(componentContainer);
  }

  @Override
  public MyRunProfile getRunProfile() {
    final JUnitConfiguration configuration = (JUnitConfiguration)getModel().getProperties().getConfiguration();
    final TestMethods testMethods = new TestMethods(configuration.getProject(), configuration, myEnvironment, getFailedTests(configuration.getProject()));
    return new MyRunProfile(configuration) {
      @NotNull
      public Module[] getModules() {
        return testMethods.getModulesToCompile();
      }

      public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
        testMethods.clear();
        return testMethods;
      }

      @Override
      public void clear() {
        testMethods.clear();
        super.clear();
      }
    };
  }
}
