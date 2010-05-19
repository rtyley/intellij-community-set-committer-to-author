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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.zmlx.hg4idea.HgFile;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class HgStatusCommand {

  private static final Logger LOG = Logger.getInstance(HgStatusCommand.class.getName());

  private static final int ITEM_COUNT = 3;
  private static final int STATUS_INDEX = 0;

  private final Project project;

  private boolean includeAdded = true;
  private boolean includeModified = true;
  private boolean includeRemoved = true;
  private boolean includeDeleted = true;
  private boolean includeUnknown = true;
  private boolean includeIgnored = true;
  private boolean includeCopySource = true;

  public HgStatusCommand(Project project) {
    this.project = project;
  }

  public void setIncludeAdded(boolean includeAdded) {
    this.includeAdded = includeAdded;
  }

  public void setIncludeModified(boolean includeModified) {
    this.includeModified = includeModified;
  }

  public void setIncludeRemoved(boolean includeRemoved) {
    this.includeRemoved = includeRemoved;
  }

  public void setIncludeDeleted(boolean includeDeleted) {
    this.includeDeleted = includeDeleted;
  }

  public void setIncludeUnknown(boolean includeUnknown) {
    this.includeUnknown = includeUnknown;
  }

  public void setIncludeIgnored(boolean includeIgnored) {
    this.includeIgnored = includeIgnored;
  }

  public void setIncludeCopySource(boolean includeCopySource) {
    this.includeCopySource = includeCopySource;
  }

  public Set<HgChange> execute(VirtualFile repo) {
    if (repo == null) {
      return Collections.emptySet();
    }

    HgCommandService service = HgCommandService.getInstance(project);

    List<String> arguments = new LinkedList<String>();
    if (includeAdded) {
      arguments.add("--added");
    }
    if (includeModified) {
      arguments.add("--modified");
    }
    if (includeRemoved) {
      arguments.add("--removed");
    }
    if (includeDeleted) {
      arguments.add("--deleted");
    }
    if (includeUnknown) {
      arguments.add("--unknown");
    }
    if (includeIgnored) {
      arguments.add("--ignored");
    }
    if (includeCopySource) {
      arguments.add("--copies");
    }

    HgCommandResult result = service.execute(repo, "status", arguments);
    Set<HgChange> changes = new HashSet<HgChange>();
    HgChange previous = null;
    for (String line : result.getOutputLines()) {
      if (StringUtils.isBlank(line) || line.length() < ITEM_COUNT) {
        LOG.warn("Unexpected line in status '" + line + '\'');
        continue;
      }
      HgFileStatusEnum status = HgFileStatusEnum.valueOf(line.charAt(STATUS_INDEX));
      File ioFile = new File(repo.getPath(), line.substring(2));
      if (HgFileStatusEnum.COPY == status && previous != null
        && previous.getStatus() == HgFileStatusEnum.ADDED) {
        previous.setStatus(HgFileStatusEnum.COPY);
        previous.setBeforeFile(new HgFile(repo, ioFile));
        previous = null;
      } else {
        previous = new HgChange(new HgFile(repo, ioFile), status);
        changes.add(previous);
      }
    }
    return changes;
  }

}
