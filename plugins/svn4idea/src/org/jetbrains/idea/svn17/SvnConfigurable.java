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


package org.jetbrains.idea.svn17;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.MultiLineTooltipUI;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn17.config.ConfigureProxiesListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class SvnConfigurable implements Configurable {

  private final Project myProject;
  private JCheckBox myUseDefaultCheckBox;
  private TextFieldWithBrowseButton myConfigurationDirectoryText;
  private JButton myClearAuthButton;
  private JCheckBox myUseCommonProxy;
  private JButton myEditProxiesButton;
  private JPanel myComponent;

  private JLabel myConfigurationDirectoryLabel;
  private JLabel myClearCacheLabel;
  private JLabel myUseCommonProxyLabel;
  private JLabel myEditProxyLabel;
  private JCheckBox myLockOnDemand;
  private JCheckBox myCheckNestedInQuickMerge;
  private JCheckBox myDetectNestedWorkingCopiesCheckBox;
  private JCheckBox myIgnoreWhitespaceDifferenciesInCheckBox;
  private JCheckBox myShowMergeSourceInAnnotate;
  private JSpinner myNumRevsInAnnotations;
  private JCheckBox myMaximumNumberOfRevisionsCheckBox;
  private JSpinner mySSHConnectionTimeout;
  private JSpinner mySSHReadTimeout;
  private JBLabel myWarningLabel;
  private HyperlinkLabel myLinkLabel;
  private JRadioButton myJavaHLAcceleration;
  private JRadioButton myNoAcceleration;
  private JLabel myJavaHLInfo;
  private JRadioButton myWithCommandLineClient;

  @NonNls private static final String HELP_ID = "project.propSubversion";

  public SvnConfigurable(Project project) {
    myProject = project;
    myLinkLabel.setHyperlinkTarget("http://confluence.jetbrains.net/display/IDEADEV/Subversion+1.7+in+IntelliJ+IDEA+11");
    myLinkLabel.setHyperlinkText("More information");

    myUseDefaultCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        boolean enabled = !myUseDefaultCheckBox.isSelected();
        myConfigurationDirectoryText.setEnabled(enabled);
        myConfigurationDirectoryLabel.setEnabled(enabled);
        SvnConfiguration17 configuration = SvnConfiguration17.getInstance(myProject);
        String path = configuration.getConfigurationDirectory();
        if (!enabled || path == null) {
          myConfigurationDirectoryText.setText(IdeaSubversionConfigurationDirectory.getPath());
        }
        else {
          myConfigurationDirectoryText.setText(path);
        }
      }
    });

    myClearAuthButton.addActionListener(new ActionListener(){
      public void actionPerformed(final ActionEvent e) {
        String path = myConfigurationDirectoryText.getText();
        if (path != null) {
          int result = Messages.showYesNoDialog(myComponent, SvnBundle.message("confirmation.text.delete.stored.authentication.information"),
                                                SvnBundle.message("confirmation.title.clear.authentication.cache"),
                                                             Messages.getWarningIcon());
          if (result == 0) {
            SvnConfiguration17.RUNTIME_AUTH_CACHE.clear();
            SvnConfiguration17.getInstance(myProject).clearAuthenticationDirectory();
          }
        }

      }
    });


    final FileChooserDescriptor descriptor = createFileDescriptor();

    myConfigurationDirectoryText.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        @NonNls String path = myConfigurationDirectoryText.getText().trim();
        path = "file://" + path.replace(File.separatorChar, '/');
        VirtualFile root = VirtualFileManager.getInstance().findFileByUrl(path);

        String oldValue = PropertiesComponent.getInstance().getValue("FileChooser.showHiddens");
        PropertiesComponent.getInstance().setValue("FileChooser.showHiddens", Boolean.TRUE.toString());
        VirtualFile file = FileChooser.chooseFile(myComponent, descriptor, root);
        PropertiesComponent.getInstance().setValue("FileChooser.showHiddens", oldValue);
        if (file == null) {
          return;
        }
        myConfigurationDirectoryText.setText(file.getPath().replace('/', File.separatorChar));
      }
    });
    myConfigurationDirectoryText.setEditable(false);

    myConfigurationDirectoryLabel.setLabelFor(myConfigurationDirectoryText);

    myUseCommonProxy.setText(SvnBundle.message("use.idea.proxy.as.default", ApplicationNamesInfo.getInstance().getProductName()));
    myEditProxiesButton.addActionListener(new ConfigureProxiesListener(myProject));

    myMaximumNumberOfRevisionsCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myNumRevsInAnnotations.setEnabled(myMaximumNumberOfRevisionsCheckBox.isSelected());
      }
    });
    myNumRevsInAnnotations.setEnabled(myMaximumNumberOfRevisionsCheckBox.isSelected());

    ButtonGroup bg = new ButtonGroup();
    bg.add(myNoAcceleration);
    bg.add(myJavaHLAcceleration);
    bg.add(myWithCommandLineClient);
  }

  private static FileChooserDescriptor createFileDescriptor() {
    final FileChooserDescriptor descriptor =  FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setShowFileSystemRoots(true);
    descriptor.setTitle(SvnBundle.message("dialog.title.select.configuration.directory"));
    descriptor.setDescription(SvnBundle.message("dialog.description.select.configuration.directory"));
    descriptor.setHideIgnored(false);
    return descriptor;
  }

  public JComponent createComponent() {

    return myComponent;
  }

  public String getDisplayName() {
    return null;
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return HELP_ID;
  }

  public boolean isModified() {
    if (myComponent == null) {
      return false;
    }
    SvnConfiguration17 configuration = SvnConfiguration17.getInstance(myProject);
    if (configuration.isUseDefaultConfiguation() != myUseDefaultCheckBox.isSelected()) {
      return true;
    }
    if (configuration.isIsUseDefaultProxy() != myUseCommonProxy.isSelected()) {
      return true;
    }
    if (configuration.UPDATE_LOCK_ON_DEMAND != myLockOnDemand.isSelected()) {
      return true;
    }
    if (configuration.DETECT_NESTED_COPIES != myDetectNestedWorkingCopiesCheckBox.isSelected()) {
      return true;
    }
    if (configuration.CHECK_NESTED_FOR_QUICK_MERGE != myCheckNestedInQuickMerge.isSelected()) {
      return true;
    }
    if (configuration.IGNORE_SPACES_IN_ANNOTATE != myIgnoreWhitespaceDifferenciesInCheckBox.isSelected()) {
      return true;
    }
    if (configuration.SHOW_MERGE_SOURCES_IN_ANNOTATE != myShowMergeSourceInAnnotate.isSelected()) {
      return true;
    }
    if (! configuration.myUseAcceleration.equals(acceleration())) return true;
    final int annotateRevisions = configuration.getMaxAnnotateRevisions();
    final boolean useMaxInAnnot = annotateRevisions != -1;
    if (useMaxInAnnot != myMaximumNumberOfRevisionsCheckBox.isSelected()) {
      return true;
    }
    if (myMaximumNumberOfRevisionsCheckBox.isSelected()) {
      if (annotateRevisions != ((SpinnerNumberModel) myNumRevsInAnnotations.getModel()).getNumber().intValue()) {
        return true;
      }
    }
    if (configuration.mySSHConnectionTimeout/1000 != ((SpinnerNumberModel) mySSHConnectionTimeout.getModel()).getNumber().longValue()) {
      return true;
    }
    if (configuration.mySSHReadTimeout/1000 != ((SpinnerNumberModel) mySSHReadTimeout.getModel()).getNumber().longValue()) {
      return true;
    }
    return !configuration.getConfigurationDirectory().equals(myConfigurationDirectoryText.getText().trim());
  }
  
  private SvnConfiguration17.UseAcceleration acceleration() {
    if (myNoAcceleration.isSelected()) return SvnConfiguration17.UseAcceleration.nothing;
    if (myJavaHLAcceleration.isSelected()) return SvnConfiguration17.UseAcceleration.javaHL;
    if (myWithCommandLineClient.isSelected()) return SvnConfiguration17.UseAcceleration.commandLine;
    return SvnConfiguration17.UseAcceleration.nothing;
  }

  private void setAcceleration(SvnConfiguration17.UseAcceleration acceleration) {
    if (! CheckJavaHL.isPresent()) {
      myJavaHLInfo.setText(CheckJavaHL.getProblemDescription());
      myJavaHLInfo.setForeground(Color.red);
      myJavaHLInfo.setEnabled(true);
      myJavaHLAcceleration.setEnabled(false);
      /*myJavaHLAcceleration.setText(myJavaHLAcceleration.getText() + ". " + CheckJavaHL.getProblemDescription());
      myJavaHLAcceleration.setEnabled(false);
      myJavaHLAcceleration.setForeground(Color.red);*/
    } else {
      myJavaHLInfo.setText("You need to have JavaHL 1.7.2");
      myJavaHLInfo.setForeground(UIUtil.getInactiveTextColor());
      myJavaHLAcceleration.setEnabled(true);
    }

    if (SvnConfiguration17.UseAcceleration.javaHL.equals(acceleration)) {
      myJavaHLAcceleration.setSelected(true);
      return;
    } else if (SvnConfiguration17.UseAcceleration.commandLine.equals(acceleration)) {
      myWithCommandLineClient.setSelected(true);
      return;
    }
    myNoAcceleration.setSelected(true);
  }

  public void apply() throws ConfigurationException {
    SvnConfiguration17 configuration = SvnConfiguration17.getInstance(myProject);
    configuration.setConfigurationDirectory(myConfigurationDirectoryText.getText());
    configuration.setUseDefaultConfiguation(myUseDefaultCheckBox.isSelected());
    configuration.setIsUseDefaultProxy(myUseCommonProxy.isSelected());
    if ((! configuration.DETECT_NESTED_COPIES) && (configuration.DETECT_NESTED_COPIES != myDetectNestedWorkingCopiesCheckBox.isSelected())) {
      SvnVcs17.getInstance(myProject).invokeRefreshSvnRoots(true);
    }
    configuration.DETECT_NESTED_COPIES = myDetectNestedWorkingCopiesCheckBox.isSelected();
    configuration.CHECK_NESTED_FOR_QUICK_MERGE = myCheckNestedInQuickMerge.isSelected();
    configuration.UPDATE_LOCK_ON_DEMAND = myLockOnDemand.isSelected();
    configuration.setIgnoreSpacesInAnnotate(myIgnoreWhitespaceDifferenciesInCheckBox.isSelected());
    configuration.SHOW_MERGE_SOURCES_IN_ANNOTATE = myShowMergeSourceInAnnotate.isSelected();
    if (! myMaximumNumberOfRevisionsCheckBox.isSelected()) {
      configuration.setMaxAnnotateRevisions(-1);
    } else {
      configuration.setMaxAnnotateRevisions(((SpinnerNumberModel) myNumRevsInAnnotations.getModel()).getNumber().intValue());
    }
    configuration.mySSHConnectionTimeout = ((SpinnerNumberModel) mySSHConnectionTimeout.getModel()).getNumber().longValue() * 1000;
    configuration.mySSHReadTimeout = ((SpinnerNumberModel) mySSHReadTimeout.getModel()).getNumber().longValue() * 1000;
    configuration.myUseAcceleration = acceleration();
  }

  public void reset() {
    SvnConfiguration17 configuration = SvnConfiguration17.getInstance(myProject);
    String path = configuration.getConfigurationDirectory();
    if (configuration.isUseDefaultConfiguation() || path == null) {
      path = IdeaSubversionConfigurationDirectory.getPath();
    }
    myConfigurationDirectoryText.setText(path);
    myUseDefaultCheckBox.setSelected(configuration.isUseDefaultConfiguation());
    myUseCommonProxy.setSelected(configuration.isIsUseDefaultProxy());
    myDetectNestedWorkingCopiesCheckBox.setSelected(configuration.DETECT_NESTED_COPIES);
    myCheckNestedInQuickMerge.setSelected(configuration.CHECK_NESTED_FOR_QUICK_MERGE);

    boolean enabled = !myUseDefaultCheckBox.isSelected();
    myConfigurationDirectoryText.setEnabled(enabled);
    myConfigurationDirectoryLabel.setEnabled(enabled);
    myLockOnDemand.setSelected(configuration.UPDATE_LOCK_ON_DEMAND);
    myIgnoreWhitespaceDifferenciesInCheckBox.setSelected(configuration.IGNORE_SPACES_IN_ANNOTATE);
    myShowMergeSourceInAnnotate.setSelected(configuration.SHOW_MERGE_SOURCES_IN_ANNOTATE);

    final int annotateRevisions = configuration.getMaxAnnotateRevisions();
    if (annotateRevisions == -1) {
      myMaximumNumberOfRevisionsCheckBox.setSelected(false);
      myNumRevsInAnnotations.setValue(SvnConfiguration17.ourMaxAnnotateRevisionsDefault);
    } else {
      myMaximumNumberOfRevisionsCheckBox.setSelected(true);
      myNumRevsInAnnotations.setValue(annotateRevisions);
    }
    myNumRevsInAnnotations.setEnabled(myMaximumNumberOfRevisionsCheckBox.isSelected());
    mySSHConnectionTimeout.setValue(Long.valueOf(configuration.mySSHConnectionTimeout / 1000));
    mySSHReadTimeout.setValue(Long.valueOf(configuration.mySSHReadTimeout / 1000));
    setAcceleration(configuration.myUseAcceleration);
  }

  public void disposeUIResources() {
  }

  private void createUIComponents() {
    myLockOnDemand = new JCheckBox() {
      @Override
      public JToolTip createToolTip() {
        JToolTip toolTip = new JToolTip(){{
          setUI(new MultiLineTooltipUI());
        }};
        toolTip.setComponent(this);
        return toolTip;
      }
    };

    final SvnConfiguration17 configuration = SvnConfiguration17.getInstance(myProject);
    int value = configuration.getMaxAnnotateRevisions();
    value = (value == -1) ? SvnConfiguration17.ourMaxAnnotateRevisionsDefault : value;
    myNumRevsInAnnotations = new JSpinner(new SpinnerNumberModel(value, 10, 100000, 100));

    final Long maximum = 30 * 60 * 1000L;
    final long connection = configuration.mySSHConnectionTimeout <= maximum ? configuration.mySSHConnectionTimeout : maximum;
    final long read = configuration.mySSHReadTimeout <= maximum ? configuration.mySSHReadTimeout : maximum;
    mySSHConnectionTimeout = new JSpinner(new SpinnerNumberModel(Long.valueOf(connection / 1000), Long.valueOf(0L), maximum, Long.valueOf(10L)));
    mySSHReadTimeout = new JSpinner(new SpinnerNumberModel(Long.valueOf(read / 1000), Long.valueOf(0L), maximum, Long.valueOf(10L)));
  }
}

