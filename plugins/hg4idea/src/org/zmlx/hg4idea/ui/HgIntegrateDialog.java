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
package org.zmlx.hg4idea.ui;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.Nls;
import org.zmlx.hg4idea.command.HgTagBranch;
import org.zmlx.hg4idea.command.HgTagBranchCommand;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.List;
import java.util.LinkedList;

public class HgIntegrateDialog implements Configurable {

  private final Project project;

  private JRadioButton revisionOption;
  private JTextField revisionTxt;
  private JRadioButton branchOption;
  private JRadioButton tagOption;
  private JComboBox branchSelector;
  private JComboBox tagSelector;
  private JPanel contentPanel;
  private HgRepositorySelectorComponent hgRepositorySelectorComponent;

  public HgIntegrateDialog(Project project, Collection<FilePath> roots) {
    this.project = project;
    hgRepositorySelectorComponent.setRoots(pathsToFiles(roots));
    hgRepositorySelectorComponent.setTitle("Select repository to integrate");
    hgRepositorySelectorComponent.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateRepository();
      }
    });

    ChangeListener changeListener = new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        updateOptions();
      }
    };
    branchOption.addChangeListener(changeListener);
    tagOption.addChangeListener(changeListener);
    revisionOption.addChangeListener(changeListener);

    updateRepository();
  }

  public VirtualFile getRepository() {
    return hgRepositorySelectorComponent.getRepository();
  }

  public HgTagBranch getBranch() {
    return branchOption.isSelected() ? (HgTagBranch) branchSelector.getSelectedItem() : null;
  }

  public HgTagBranch getTag() {
    return tagOption.isSelected() ? (HgTagBranch) tagSelector.getSelectedItem() : null;
  }

  public String getRevision() {
    return revisionOption.isSelected() ? revisionTxt.getText() : null;
  }

  @Nls
  public String getDisplayName() {
    return null;
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return contentPanel;
  }

  public boolean isModified() {
    return true;
  }

  public void apply() throws ConfigurationException {
  }

  public void reset() {
  }

  private void updateRepository() {
    VirtualFile repo = getRepository();
    loadBranches(repo);
    loadTags(repo);
  }

  private void updateOptions() {
    revisionTxt.setEnabled(revisionOption.isSelected());
    branchSelector.setEnabled(branchOption.isSelected());
    tagSelector.setEnabled(tagOption.isSelected());
  }

  private void loadBranches(VirtualFile root) {
    List<HgTagBranch> branches = new HgTagBranchCommand(project, root).listBranches();
    branchSelector.setModel(new DefaultComboBoxModel(branches.toArray()));
  }

  private void loadTags(VirtualFile root) {
    List<HgTagBranch> tags = new HgTagBranchCommand(project, root).listTags();
    tagSelector.setModel(new DefaultComboBoxModel(tags.toArray()));
  }

  private List<VirtualFile> pathsToFiles(Collection<FilePath> paths) {
    List<VirtualFile> files = new LinkedList<VirtualFile>();
    for (FilePath path : paths) {
      files.add(path.getVirtualFile());
    }
    return files;
  }

  public void disposeUIResources() {
  }

  {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
    $$$setupUI$$$();
  }

  /**
   * Method generated by IntelliJ IDEA GUI Designer
   * >>> IMPORTANT!! <<<
   * DO NOT edit this method OR call it in your code!
   *
   * @noinspection ALL
   */
  private void $$$setupUI$$$() {
    contentPanel = new JPanel();
    contentPanel.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
    contentPanel.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    panel1.setBorder(BorderFactory.createTitledBorder("Merge with:"));
    branchOption = new JRadioButton();
    branchOption.setSelected(true);
    branchOption.setText("Branch");
    branchOption.setMnemonic('B');
    branchOption.setDisplayedMnemonicIndex(0);
    panel1.add(branchOption, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    tagOption = new JRadioButton();
    tagOption.setText("Tag");
    tagOption.setMnemonic('T');
    tagOption.setDisplayedMnemonicIndex(0);
    panel1.add(tagOption, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    branchSelector = new JComboBox();
    branchSelector.setEnabled(true);
    panel1.add(branchSelector, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    tagSelector = new JComboBox();
    tagSelector.setEnabled(false);
    panel1.add(tagSelector, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    panel1.add(spacer1, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    revisionTxt = new JTextField();
    revisionTxt.setEnabled(false);
    panel1.add(revisionTxt, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    revisionOption = new JRadioButton();
    revisionOption.setSelected(false);
    revisionOption.setText("Revision");
    revisionOption.setMnemonic('R');
    revisionOption.setDisplayedMnemonicIndex(0);
    panel1.add(revisionOption, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer2 = new Spacer();
    contentPanel.add(spacer2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    hgRepositorySelectorComponent = new HgRepositorySelectorComponent();
    contentPanel.add(hgRepositorySelectorComponent.$$$getRootComponent$$$(), new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    ButtonGroup buttonGroup;
    buttonGroup = new ButtonGroup();
    buttonGroup.add(revisionOption);
    buttonGroup.add(branchOption);
    buttonGroup.add(tagOption);
  }

  /**
   * @noinspection ALL
   */
  public JComponent $$$getRootComponent$$$() {
    return contentPanel;
  }
}
