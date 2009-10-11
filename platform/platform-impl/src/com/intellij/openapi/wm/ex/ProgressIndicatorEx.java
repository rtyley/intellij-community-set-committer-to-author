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
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.util.containers.DoubleArrayList;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

public interface ProgressIndicatorEx extends ProgressIndicator {

  void addStateDelegate(@NotNull ProgressIndicatorEx delegate);

  void initStateFrom(@NotNull ProgressIndicatorEx indicator);

  @NotNull Stack<String> getTextStack();

  @NotNull DoubleArrayList getFractionStack();

  @NotNull Stack<String> getText2Stack();

  int getNonCancelableCount();

  boolean isModalityEntered();

  void finish(@NotNull TaskInfo task);
  
  boolean isFinished(@NotNull TaskInfo task);

  boolean wasStarted();

  void processFinish();
}
