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
package org.zmlx.hg4idea;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.ui.HgConfigurationProjectPanel;

import javax.swing.*;

public class HgProjectConfigurable implements SearchableConfigurable {

  private final HgConfigurationProjectPanel myPanel;

  public HgProjectConfigurable(HgProjectSettings projectSettings) {
    myPanel = new HgConfigurationProjectPanel(projectSettings);
  }

  @Nls
  public String getDisplayName() {
    return HgVcsMessages.message("hg4idea.mercurial");
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "project.propVCSSupport.VCSs.Mercurial";
  }

  public JComponent createComponent() {
    return myPanel.getPanel();
  }

  public boolean isModified() {
    return myPanel.isModified();
  }

  public void apply() throws ConfigurationException {
    myPanel.validate();
    myPanel.saveSettings();
  }

  public void reset() {
    myPanel.loadSettings();
  }

  public void disposeUIResources() {
  }

  @NotNull
  public String getId() {
    return "Mercurial.Project";
  }

  public Runnable enableSearch(String option) {
    return null;
  }

}
