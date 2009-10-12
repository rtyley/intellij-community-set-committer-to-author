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

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;

/**
 * @author yole
 */
public class ChangesBrowserFileNode extends ChangesBrowserNode<VirtualFile> {
  private final Project myProject;

  public ChangesBrowserFileNode(Project project, VirtualFile userObject) {
    super(userObject);
    myProject = project;
    if (!userObject.isDirectory()) {
      myCount = 1;
    } else {
      myDirectoryCount = 1;
    }
  }

  @Override
  protected boolean isDirectory() {
    return (getUserObject()).isDirectory() &&
           FileStatusManager.getInstance(myProject).getStatus(getUserObject()) != FileStatus.NOT_CHANGED;
  }


  @Override
  public void render(final ChangesBrowserNodeRenderer renderer, final boolean selected, final boolean expanded, final boolean hasFocus) {
    final VirtualFile file = getUserObject();
    renderer.appendFileName(file, file.getName(), ChangeListManager.getInstance(myProject).getStatus(file).getColor());
    if (renderer.isShowFlatten() && file.isValid()) {
      final VirtualFile parentFile = file.getParent();
      assert parentFile != null;
      renderer.append(" (" + parentFile.getPresentableUrl() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
    else if (getCount() != 1 || getDirectoryCount() != 0) {
      appendCount(renderer);
    }
    if (file.isDirectory()) {
      renderer.setIcon(Icons.DIRECTORY_CLOSED_ICON);
    }
    else {
      renderer.setIcon(file.getFileType().getIcon());
    }
  }

  @Override
  public String getTextPresentation() {
    return getUserObject().getName();
  }

  @Override
  public String toString() {
    return getUserObject().getPresentableUrl();
  }

  public int getSortWeight() {
    return 7;
  }

  public int compareUserObjects(final Object o2) {
    if (o2 instanceof VirtualFile) {
      return getUserObject().getName().compareToIgnoreCase(((VirtualFile)o2).getName());
    }
    return 0;
  }
}
