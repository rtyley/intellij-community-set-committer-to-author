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
package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.util.Collections;

public class AddFileAsMavenProjectAction extends MavenAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    MavenProjectsManager manager = MavenActionUtil.getProjectsManager(e);
    manager.addManagedFiles(Collections.singletonList(getSelectedFile(e)));
  }

  @Override
  protected boolean isAvailable(AnActionEvent e) {
    VirtualFile file = getSelectedFile(e);
    return super.isAvailable(e)
           && MavenActionUtil.isMavenProjectFile(file)
           && !isExistingProjectFile(e, file);
  }

  @Override
  protected boolean isVisible(AnActionEvent e) {
    return super.isVisible(e) && isAvailable(e);
  }

  private boolean isExistingProjectFile(AnActionEvent e, VirtualFile file) {
    MavenProjectsManager manager = MavenActionUtil.getProjectsManager(e);
    return manager.findProject(file) != null;
  }

  private VirtualFile getSelectedFile(AnActionEvent e) {
    return e.getData(PlatformDataKeys.VIRTUAL_FILE);
  }
}
