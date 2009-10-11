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
package org.jetbrains.idea.devkit.projectRoots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.AdditionalDataConfigurable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.TextFieldWithStoredHistory;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.devkit.DevKitBundle;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * User: anna
 * Date: Nov 22, 2004
 */
public class IdeaJdkConfigurable implements AdditionalDataConfigurable {
  private final JLabel mySandboxHomeLabel = new JLabel(DevKitBundle.message("sandbox.home.label"));
  private final TextFieldWithStoredHistory mySandboxHome = new TextFieldWithStoredHistory(SANDBOX_HISTORY);

  private final JLabel myInternalJreLabel = new JLabel("Internal Java Platform:");
  private final DefaultComboBoxModel myJdksModel = new DefaultComboBoxModel();
  private final JComboBox myInternalJres = new JComboBox(myJdksModel);

  private Sdk myIdeaJdk;

  private boolean myModified;
  @NonNls private static final String SANDBOX_HISTORY = "DEVKIT_SANDBOX_HISTORY";

  private final SdkModel mySdkModel;
  private final SdkModificator mySdkModificator;
  private boolean myFreeze = false;

  public IdeaJdkConfigurable(final SdkModel sdkModel, final SdkModificator sdkModificator) {
    mySdkModel = sdkModel;
    mySdkModificator = sdkModificator;
  }

  private void updateJdkList() {
    myJdksModel.removeAllElements();
    for (Sdk sdk : mySdkModel.getSdks()) {
      if (IdeaJdk.isValidInternalJdk(myIdeaJdk, sdk)) {
        myJdksModel.addElement(sdk);
      }
    }
  }

  public void setSdk(Sdk sdk) {
    myIdeaJdk = sdk;
  }

  public JComponent createComponent() {
    mySandboxHome.setHistorySize(5);
    JPanel wholePanel = new JPanel(new GridBagLayout());
    wholePanel.add(mySandboxHomeLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.WEST,
                                                              GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    wholePanel.add(GuiUtils.constructFieldWithBrowseButton(mySandboxHome,
                                                           new ActionListener() {
                                                             public void actionPerformed(ActionEvent e) {
                                                               FileChooserDescriptor descriptor = FileChooserDescriptorFactory
                                                                 .createSingleFolderDescriptor();
                                                               descriptor.setTitle(DevKitBundle.message("sandbox.home"));
                                                               descriptor.setDescription(
                                                                 DevKitBundle.message("sandbox.purpose"));
                                                               VirtualFile[] files = FileChooser.chooseFiles(mySandboxHome, descriptor);
                                                               if (files.length != 0) {
                                                                 mySandboxHome.setText(
                                                                   FileUtil.toSystemDependentName(files[0].getPath()));
                                                               }
                                                               myModified = true;
                                                             }
                                                           }),
                   new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.EAST,
                                          GridBagConstraints.HORIZONTAL, new Insets(0, 30, 0, 0), 0, 0));

    wholePanel.add(myInternalJreLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 1, GridBagConstraints.WEST,
                                                              GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    wholePanel.add(myInternalJres, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1, 1, GridBagConstraints.EAST,
                                                          GridBagConstraints.HORIZONTAL, new Insets(0, 30, 0, 0), 0, 0));
    myInternalJres.setRenderer(new DefaultListCellRenderer(){
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index, final boolean isSelected, final boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof Sdk) {
          setText(((Sdk)value).getName());
        }
        return rendererComponent;
      }
    });

    myInternalJres.addItemListener(new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        if (myFreeze) return;
        final Sdk javaJdk = (Sdk)e.getItem();
        for (OrderRootType type : OrderRootType.getAllTypes()) {
          final VirtualFile[] internalRoots = javaJdk.getSdkModificator().getRoots(type);
          final VirtualFile[] configuredRoots = mySdkModificator.getRoots(type);
          for (VirtualFile file : internalRoots) {
            if (e.getStateChange() == ItemEvent.DESELECTED) {
              mySdkModificator.removeRoot(file, type);
            } else {
              if (ArrayUtil.find(configuredRoots, file) == -1) {
                mySdkModificator.addRoot(file, type);
              }
            }
          }
        }
      }
    });

    mySandboxHome.addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        myModified = true;
      }
    });
    mySandboxHome.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myModified = true;
      }
    });
    mySandboxHome.setText("");
    myModified = true;
    return wholePanel;
  }

  public void internalJdkUpdate(final Sdk sdk) {
    final Sdk javaSdk = ((Sandbox)sdk.getSdkAdditionalData()).getJavaSdk();
    if (myJdksModel.getIndexOf(javaSdk) == -1) {
      myJdksModel.addElement(javaSdk);
    } else {
      myJdksModel.setSelectedItem(javaSdk);
    }
  }

  public boolean isModified() {
    return myModified;
  }

  public void apply() throws ConfigurationException {
    /*if (mySandboxHome.getText() == null || mySandboxHome.getText().length() == 0) {
      throw new ConfigurationException(DevKitBundle.message("sandbox.specification"));
    }*/
    mySandboxHome.addCurrentTextToHistory();
    final Sandbox additionalData = (Sandbox)myIdeaJdk.getSdkAdditionalData();
    if (additionalData != null) {
      additionalData.cleanupWatchedRoots();
    }
    Sandbox sandbox = new Sandbox(mySandboxHome.getText(), (Sdk)myInternalJres.getSelectedItem(), myIdeaJdk);
    final SdkModificator modificator = myIdeaJdk.getSdkModificator();
    modificator.setSdkAdditionalData(sandbox);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        modificator.commitChanges();
      }
    });
    myModified = false;
  }

  public void reset() {
    myFreeze = true;
    updateJdkList();
    myFreeze = false;
    mySandboxHome.reset();
    if (myIdeaJdk != null && myIdeaJdk.getSdkAdditionalData() instanceof Sandbox) {
      final Sandbox sandbox = (Sandbox)myIdeaJdk.getSdkAdditionalData();
      final String sandboxHome = sandbox.getSandboxHome();
      mySandboxHome.setText(sandboxHome);
      mySandboxHome.setSelectedItem(sandboxHome);
      final Sdk internalJava = sandbox.getJavaSdk();
      if (internalJava != null) {
        for (int i = 0; i < myJdksModel.getSize(); i++) {
          if (Comparing.strEqual(((Sdk)myJdksModel.getElementAt(i)).getName(), internalJava.getName())){
            myInternalJres.setSelectedIndex(i);
            break;
          }
        }
      }
      myModified = false;
    } else {
      mySandboxHome.setText(IdeaJdk.getDefaultSandbox());
    }
  }

  public void disposeUIResources() {
  }

  public void addJavaSdk(final Sdk sdk) {
    myJdksModel.addElement(sdk);
  }

  public void removeJavaSdk(final Sdk sdk) {
    myJdksModel.removeElement(sdk);
  }

  public void updateJavaSdkList(Sdk sdk, String previousName) {
    final Sdk[] sdks = mySdkModel.getSdks();
    for (Sdk currentSdk : sdks) {
      if (currentSdk.getSdkType() instanceof IdeaJdk){
        final Sandbox sandbox = (Sandbox)currentSdk.getSdkAdditionalData();
        final Sdk internalJava = sandbox.getJavaSdk();
        if (internalJava != null && Comparing.equal(internalJava.getName(), previousName)){
          sandbox.setJavaSdk(sdk);
        }
      }
    }
    updateJdkList();
  }
}
