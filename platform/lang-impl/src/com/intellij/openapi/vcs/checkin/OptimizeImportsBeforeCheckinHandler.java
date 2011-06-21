/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.checkin;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

public class OptimizeImportsBeforeCheckinHandler extends CheckinHandler implements CheckinMetaHandler {

  public static final String COMMAND_NAME = CodeInsightBundle.message("process.optimize.imports.before.commit");
  
  protected final Project myProject;
  private final CheckinProjectPanel myPanel;

  public OptimizeImportsBeforeCheckinHandler(final Project project, final CheckinProjectPanel panel) {
    myProject = project;
    myPanel = panel;
  }

  @Nullable
  public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
    final JCheckBox optimizeBox = new JCheckBox(VcsBundle.message("checkbox.checkin.options.optimize.imports"));

    return new RefreshableOnComponent() {
      public JComponent getComponent() {
        final JPanel panel = new JPanel(new GridLayout(1, 0));
        panel.add(optimizeBox);
        return panel;
      }

      public void refresh() {
      }

      public void saveState() {
        getSettings().OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT = optimizeBox.isSelected();
      }

      public void restoreState() {
        optimizeBox.setSelected(getSettings().OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT);
      }
    };

  }

  protected VcsConfiguration getSettings() {
    return VcsConfiguration.getInstance(myProject);
  }

  public void runCheckinHandlers(final Runnable finishAction) {
    final VcsConfiguration configuration = VcsConfiguration.getInstance(myProject);
    final Collection<VirtualFile> files = myPanel.getVirtualFiles();

    final Runnable performCheckoutAction = new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
        finishAction.run();
      }
    };

    if (configuration.OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT) {
      new OptimizeImportsProcessor(myProject, BeforeCheckinHandlerUtil.getPsiFiles(myProject, files), COMMAND_NAME, performCheckoutAction).run();
    }  else {
      finishAction.run();
    }

  }
}
