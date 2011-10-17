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
package com.intellij.cvsSupport2.config.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.ui.CvsRootChangeListener;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * author: lesya
 */
public class CvsConfigurationsListEditor extends DialogWrapper implements DataProvider{
  private final JList myList = new JBList();
  private final DefaultListModel myModel = new DefaultListModel();
  private CvsRootConfiguration mySelection;

  private final Cvs2SettingsEditPanel myCvs2SettingsEditPanel;
  @NonNls private static final String SAMPLE_CVSROOT = ":pserver:user@host/server/home/user/cvs";

  public CvsConfigurationsListEditor(List<CvsRootConfiguration> configs, Project project) {
    this(configs, project, false);
  }

  public CvsConfigurationsListEditor(List<CvsRootConfiguration> configs, Project project, boolean readOnly) {
    super(true);
    myCvs2SettingsEditPanel = new Cvs2SettingsEditPanel(project, readOnly);
    setTitle(CvsBundle.message("operation.name.edit.configurations"));
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    selectNone();
    fillModel(configs);

    myCvs2SettingsEditPanel.addCvsRootChangeListener(new CvsRootChangeListener() {
      @Override
      public void onCvsRootChanged() {
        if (mySelection == null) return;
        myCvs2SettingsEditPanel.saveTo(mySelection);
        myList.repaint();
      }
    });

    setTitle(CvsBundle.message("dialog.title.cvs.roots"));

    if (!configs.isEmpty()) {
      myList.setSelectedIndex(0);
    }
    if (readOnly) {
      myList.setEnabled(false);
    }
    init();
  }

  @Nullable
  public static CvsRootConfiguration reconfigureCvsRoot(String root, Project project){
    final CvsApplicationLevelConfiguration configuration = CvsApplicationLevelConfiguration.getInstance();
    final CvsRootConfiguration selectedConfig = configuration.getConfigurationForCvsRoot(root);
    final ArrayList<CvsRootConfiguration> modifiableList = new ArrayList<CvsRootConfiguration>(configuration.CONFIGURATIONS);
    final CvsConfigurationsListEditor editor = new CvsConfigurationsListEditor(modifiableList, project, true);
    editor.select(selectedConfig);
    editor.show();
    if (editor.isOK()){
      configuration.CONFIGURATIONS = modifiableList;
      return configuration.getConfigurationForCvsRoot(root);
    } else {
      return null;
    }
  }

  @Override
  protected Action[] createLeftSideActions() {
    final AbstractAction globalSettingsAction = new AbstractAction(CvsBundle.message("button.text.global.settings")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        new ConfigureCvsGlobalSettingsDialog().show();
      }
    };
    return new Action[]{globalSettingsAction};
  }

  @Override
  protected void doOKAction() {
    if (saveSelectedConfiguration()) {
      super.doOKAction();
    }
  }

  private void fillModel(List<CvsRootConfiguration> configurations) {
    for (final CvsRootConfiguration configuration : configurations) {
      myModel.addElement(configuration.getMyCopy());
    }
  }

  private JComponent createListPanel() {
    final AnActionButton duplicateButton =
      new AnActionButton(CvsBundle.message("action.name.copy"), IconLoader.getIcon("/general/copy.png")) {

        @Override
        public void updateButton(AnActionEvent e) {
          e.getPresentation().setEnabled(getSelectedConfiguration() != null);
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
          copySelectedConfiguration();
        }
      };
    duplicateButton.setShortcut(new CustomShortcutSet(
      KeyStroke.getKeyStroke(KeyEvent.VK_D, SystemInfo.isMac ? KeyEvent.META_MASK : KeyEvent.CTRL_MASK)));
    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myList).setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton anActionButton) {
        createNewConfiguration();
      }
    }).addExtraAction(duplicateButton);
    return decorator.createPanel();
  }

  @Override
  protected JComponent createCenterPanel() {
    myList.setCellRenderer(new CvsListCellRenderer());
    final BorderLayout layout = new BorderLayout();
    layout.setHgap(6);

    final JPanel centerPanel = new JPanel(layout);
    final JComponent listPanel = createListPanel();
    centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
    centerPanel.add(listPanel, BorderLayout.CENTER);
    centerPanel.add(createCvsConfigurationPanel(), BorderLayout.EAST);

    myList.setModel(myModel);
    addSelectionListener();


    final int minWidth = myList.getFontMetrics(myList.getFont()).stringWidth(SAMPLE_CVSROOT) + 40;
    final Dimension minSize = new Dimension(minWidth, myList.getMaximumSize().height);
    listPanel.setMinimumSize(minSize);
    listPanel.setPreferredSize(minSize);
    return centerPanel;
  }

  private JComponent createCvsConfigurationPanel() {
    return myCvs2SettingsEditPanel.getPanel();
  }

  private boolean saveSelectedConfiguration() {
    if (getSelectedConfiguration() == null) return true;
    return myCvs2SettingsEditPanel.saveTo(getSelectedConfiguration());
  }

  private void copySelectedConfiguration() {
    if (!saveSelectedConfiguration()) return;
    final CvsRootConfiguration newConfig = mySelection.getMyCopy();
    myModel.addElement(newConfig);
    myList.setSelectedIndex(myModel.getSize() - 1);
  }

  private void editSelectedConfiguration() {
    editConfiguration(mySelection);
    myList.repaint();
  }

  private void createNewConfiguration() {
    if (!saveSelectedConfiguration()) return;
    myList.setSelectedValue(null, false);
    final CvsRootConfiguration newConfig =
      CvsApplicationLevelConfiguration.createNewConfiguration(CvsApplicationLevelConfiguration.getInstance());
    myModel.addElement(newConfig);
    myList.setSelectedValue(newConfig, true);
  }

  private void editConfiguration(CvsRootConfiguration newConfig) {
    myCvs2SettingsEditPanel.updateFrom(newConfig);
  }

  private void addSelectionListener() {
    myList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        final int selectedIndex = myList.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= myModel.getSize()) {
          selectNone();
        }
        else {
          final CvsRootConfiguration newSelection = (CvsRootConfiguration)myModel.getElementAt(selectedIndex);
          if (newSelection == mySelection) return;
          if (!select(newSelection)) {
            myList.setSelectedValue(mySelection, true);
          }
        }
      }
    });
  }

  private boolean select(CvsRootConfiguration cvs2Configuration) {
    if (mySelection != null && !myCvs2SettingsEditPanel.saveTo(mySelection)) return false;
    mySelection = cvs2Configuration;
    editSelectedConfiguration();
    return true;
  }

  private void selectNone() {
    mySelection = null;
    myCvs2SettingsEditPanel.disable();
  }

  public List<CvsRootConfiguration> getConfigurations() {
    final ArrayList<CvsRootConfiguration> result = new ArrayList<CvsRootConfiguration>();
    final Enumeration each = myModel.elements();
    while (each.hasMoreElements()) result.add((CvsRootConfiguration)each.nextElement());
    return result;
  }

  public CvsRootConfiguration getSelectedConfiguration() {
    return mySelection;
  }

  public void selectConfiguration(CvsRootConfiguration selectedConfiguration) {
    myList.setSelectedValue(selectedConfiguration, true);
  }

  @Override
  @NonNls
  public Object getData(String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)){
      return "reference.versioncontrol.cvs.roots";
    }
    return null;
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("reference.versioncontrol.cvs.roots");
  }

  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myModel.isEmpty()) return null;
    return myCvs2SettingsEditPanel.getPreferredFocusedComponent();
  }
}
