package org.zmlx.hg4idea.command;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Representation of the "hg init"
 */
public class HgInitCommand {

  private final Project myProject;

  public HgInitCommand(Project project) {
    myProject = project;
  }

  public void execute(@NotNull VirtualFile repositoryRoot) {
    HgCommandService.getInstance(myProject).execute(repositoryRoot, "init", null);
  }

}
