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
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.dialogs.SvnMapDialog;

public class ShowSvnMapAction extends AnAction {
  @Override
  public void update(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project) dataContext.getData(PlatformDataKeys.PROJECT.getName());

    final Presentation presentation = e.getPresentation();
    presentation.setVisible(project != null);
    presentation.setEnabled(project != null);

    presentation.setText(SvnBundle.message("action.show.svn.map.text"));
    presentation.setDescription(SvnBundle.message("action.show.svn.map.description"));
    presentation.setIcon(IconLoader.getIcon("/icons/ShowWorkingCopies.png"));
  }

  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project) dataContext.getData(PlatformDataKeys.PROJECT.getName());
    if (project == null) {
      return;
    }

    final SvnMapDialog dialog = new SvnMapDialog(project);
    dialog.show();
  }
}
