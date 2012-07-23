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
package com.intellij.debugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: michael.golubev
 */
public interface DebugEnvironment {

  @Nullable
  ExecutionResult createExecutionResult() throws ExecutionException;

  GlobalSearchScope getSearchScope();

  boolean isRemote();

  RemoteConnection getRemoteConnection();

  boolean isPollConnection();

  String getSessionName();
}
