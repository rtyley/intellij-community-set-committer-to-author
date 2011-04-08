// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.command;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.execution.HgCommandExecutor;

import java.util.List;

public class HgMQCommand {

  private final Project project;

  public HgMQCommand(Project project) {
    this.project = project;
  }

  public List<String> qapplied(@NotNull VirtualFile repo) {
    return new HgCommandExecutor(project).executeInCurrentThread(repo, "qapplied", null).getOutputLines();
  }

}
