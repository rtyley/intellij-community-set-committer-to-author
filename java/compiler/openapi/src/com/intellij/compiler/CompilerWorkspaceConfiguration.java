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

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.compiler;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;

@State(
  name = "CompilerWorkspaceConfiguration",
  storages = {
    @Storage(
      file = StoragePathMacros.WORKSPACE_FILE
    )}
)
public class CompilerWorkspaceConfiguration implements PersistentStateComponent<CompilerWorkspaceConfiguration> {
  public static final int DEFAULT_COMPILE_PROCESS_HEAP_SIZE = 700;
  public static final String DEFAULT_COMPILE_PROCESS_VM_OPTIONS = "-ea";

  public boolean AUTO_SHOW_ERRORS_IN_EDITOR = true;
  @Deprecated public boolean CLOSE_MESSAGE_VIEW_IF_SUCCESS = true;
  public boolean CLEAR_OUTPUT_DIRECTORY = true;
  public boolean USE_COMPILE_SERVER = true;
  public boolean MAKE_PROJECT_ON_SAVE = false;
  public boolean PARALLEL_COMPILATION = true;
  public int COMPILER_PROCESS_HEAP_SIZE = DEFAULT_COMPILE_PROCESS_HEAP_SIZE;
  public String COMPILER_PROCESS_ADDITIONAL_VM_OPTIONS = DEFAULT_COMPILE_PROCESS_VM_OPTIONS;

  public static CompilerWorkspaceConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, CompilerWorkspaceConfiguration.class);
  }

  public CompilerWorkspaceConfiguration getState() {
    return this;
  }

  public void loadState(CompilerWorkspaceConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public boolean useOutOfProcessBuild() {
    return USE_COMPILE_SERVER;
  }

  public boolean allowAutoMakeWhileRunningApplication() {
    return false;/*ALLOW_AUTOMAKE_WHILE_RUNNING_APPLICATION*/
  }
}
