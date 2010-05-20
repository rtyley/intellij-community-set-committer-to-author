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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import org.zmlx.hg4idea.HgFile;

import java.util.Arrays;

public class HgOutgoingCommand extends HgRevisionsCommand {

  public HgOutgoingCommand(Project project) {
    super(project);
  }

  protected HgCommandResult execute(HgCommandService service, VirtualFile repo,
    String template, int limit, HgFile hgFile) {
    return service.execute(repo, "outgoing",
      Arrays.asList("--newest-first", "--template", template, "--limit", String.valueOf(limit)));
  }

}
