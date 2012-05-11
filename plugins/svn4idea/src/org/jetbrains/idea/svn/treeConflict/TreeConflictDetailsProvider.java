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
package org.jetbrains.idea.svn.treeConflict;

import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.RefreshablePanel;
import com.intellij.openapi.vcs.changes.VcsChangeDetailsProvider;
import org.jetbrains.idea.svn.ConflictedSvnChange;

import javax.swing.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 4/25/12
 * Time: 5:04 PM
 */
public class TreeConflictDetailsProvider implements VcsChangeDetailsProvider {
  private final Project myProject;

  public TreeConflictDetailsProvider(Project project) {
    myProject = project;
  }

  @Override
  public String getName() {
    return "Subversion Tree Conflict";
  }

  @Override
  public boolean canComment(Change change) {
    if (change instanceof ConflictedSvnChange && ((ConflictedSvnChange)change).getConflictState().isTree()) return true;
    return false;
  }

  @Override
  public RefreshablePanel comment(Change change, JComponent parent, BackgroundTaskQueue queue) {
    return new TreeConflictRefreshablePanel(myProject, "Loading tree conflict details", queue, change);
  }
}
