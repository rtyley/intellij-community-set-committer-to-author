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
package git4idea.test

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import git4idea.Notificator
import git4idea.PlatformFacade
import git4idea.tests.TestDialogManager
import org.jetbrains.annotations.NotNull

/**
 * 
 * @author Kirill Likhodedov
 */
public class GitTestPlatformFacade implements PlatformFacade {

  GitMockVcsManager myVcsManager
  TestNotificator myNotificator
  TestDialogManager myTestDialogManager

  public GitTestPlatformFacade() {
    myVcsManager = new GitMockVcsManager()
    myTestDialogManager = new TestDialogManager()
  }

  @NotNull
  @Override
  ProjectLevelVcsManager getVcsManager(@NotNull Project project) {
    return myVcsManager
  }

  @NotNull
  @Override
  Notificator getNotificator(@NotNull Project project) {
    if (!myNotificator) {
      myNotificator = new TestNotificator(project)
    }
    myNotificator
  }

  @Override
  void showDialog(@NotNull DialogWrapper dialog) {
    myTestDialogManager.show(dialog)
  }

  @NotNull
  @Override
  AbstractVcs getVcs(@NotNull Project project) {
    new MockAbstractVcs(project, "Git")
  }

  @NotNull
  TestDialogManager getDialogManager() {
    myTestDialogManager
  }

}
