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
package org.zmlx.hg4idea.action;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgErrorUtil;
import org.zmlx.hg4idea.command.HgPushCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgCommandResultHandler;
import org.zmlx.hg4idea.ui.HgPushDialog;

import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HgPushAction extends HgAbstractGlobalAction {
  private static final Logger LOG = Logger.getInstance(HgPushAction.class);
  private static Pattern PUSH_COMMITS_PATTERN = Pattern.compile(".*added (\\d+) changesets.*");

  protected HgGlobalCommandBuilder getHgGlobalCommandBuilder(final Project project) {
    return new HgGlobalCommandBuilder() {
      public HgGlobalCommand build(Collection<VirtualFile> repos) {
        HgPushDialog dialog = new HgPushDialog(project);
        dialog.setRoots(repos);
        dialog.show();
        if (dialog.isOK()) {
          return buildCommand(dialog, project);
        }
        return null;
      }
    };
  }

  private HgGlobalCommand buildCommand(final HgPushDialog dialog, final Project project) {
    return new HgGlobalCommand() {
      public VirtualFile getRepo() {
        return dialog.getRepository();
      }

      public void execute() {
        HgPushCommand command = new HgPushCommand(project, dialog.getRepository(), dialog.getTarget());
        command.setRevision(dialog.getRevision());
        command.setForce(dialog.isForce());
        command.setBranch(dialog.getBranch());
        command.execute(new HgCommandResultHandler() {
          @Override
          public void process(@Nullable HgCommandResult result) {
            int commitsNum = getNumberOfPushedCommits(result);
            String title = null;
            String description = null;
            if (commitsNum >= 0) {
              title = "Pushed successfully";
              description = "Pushed " + commitsNum + " " + StringUtil.pluralize("commit", commitsNum) + ".";
            }
            new HgCommandResultNotifier(project).process(result, title, description);
          }
        });
      }
    };
  }

  private static int getNumberOfPushedCommits(HgCommandResult result) {
    if (!HgErrorUtil.isAbort(result)) {
      final List<String> outputLines = result.getOutputLines();
      for (String outputLine : outputLines) {
        final Matcher matcher = PUSH_COMMITS_PATTERN.matcher(outputLine.trim());
        if (matcher.matches()) {
          try {
            return Integer.parseInt(matcher.group(1));
          }
          catch (NumberFormatException e) {
            LOG.info("getNumberOfPushedCommits ", e);
            return -1;
          }
        }
      }
    }
    return -1;
  }

}
