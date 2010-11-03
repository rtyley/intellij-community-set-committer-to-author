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
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import com.intellij.xdebugger.impl.DebuggerSupport;

/**
 * @author nik
 */
public class ToggleLineBreakpointAction extends XDebuggerActionBase implements DumbAware {
  public ToggleLineBreakpointAction() {
    super(true);
  }

  @NotNull
  protected DebuggerActionHandler getHandler(@NotNull final DebuggerSupport debuggerSupport) {
    return debuggerSupport.getToggleLineBreakpointHandler();
  }
}
