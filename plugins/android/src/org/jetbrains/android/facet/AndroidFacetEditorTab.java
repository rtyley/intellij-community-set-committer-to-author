/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.facet;

import com.android.sdklib.SdkConstants;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.GlobalLibrariesConfigurable;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.android.compiler.AndroidAptCompiler;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.compiler.AndroidIdlCompiler;
import org.jetbrains.android.maven.AndroidMavenProvider;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidPlatformChooser;
import org.jetbrains.android.sdk.AndroidPlatformChooserListener;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class AndroidFacetEditorTab extends FacetEditorTab {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.facet.AndroidFacetEditorTab");

  private final AndroidPlatformChooser myPlatformChooser;
  private final AndroidFacetConfiguration myConfiguration;
  private final FacetEditorContext myContext;
  private AndroidPlatform myAppliedPlatform;
  private JPanel myPlatformChooserWrapper;
  private JPanel myContentPanel;
  private JCheckBox myAddAndroidLibrary;
  private TextFieldWithBrowseButton myRGenPathField;
  private TextFieldWithBrowseButton myAidlGenPathField;
  private JButton myResetPathsButton;
  private TextFieldWithBrowseButton myResFolderField;
  private TextFieldWithBrowseButton myAssetsFolderField;
  private TextFieldWithBrowseButton myNativeLibsFolder;
  private TextFieldWithBrowseButton myManifestFileField;
  private JRadioButton myUseAptResDirectoryFromPathRadio;
  private JRadioButton myUseCustomSourceDirectoryRadio;
  private TextFieldWithBrowseButton myCustomAptSourceDirField;
  private JCheckBox myGenerateRJavaWhenChanged;
  private JCheckBox myGenerateIdlWhenChanged;
  private JCheckBox myIsLibraryProjectCheckbox;
  private JCheckBox myCopyResourcesFromArtifacts;
  private JCheckBox myEnableAaptCompiler;
  private JPanel myAaptCompilerPanel;
  private JBList myResOverlayList;
  private JButton myAddResOverlayButton;
  private JPanel myResOverlayPanel;
  private JButton myRemoveResOverlayButton;
  private JCheckBox myGenerateUnsignedApk;
  private ComboboxWithBrowseButton myApkPathCombo;
  private JLabel myApkPathLabel;

  public AndroidFacetEditorTab(FacetEditorContext context, AndroidFacetConfiguration androidFacetConfiguration) {
    final Project project = context.getProject();
    LibraryTable.ModifiableModel model = GlobalLibrariesConfigurable.getInstance(project).getModelProvider().getModifiableModel();
    myPlatformChooser = new AndroidPlatformChooser(model, project);
    myConfiguration = androidFacetConfiguration;
    myContext = context;
    myPlatformChooserWrapper.add(myPlatformChooser.getComponent());

    AndroidFacet facet = (AndroidFacet)myContext.getFacet();

    myRGenPathField.getButton().addActionListener(new MyGenSourceFieldListener(myRGenPathField, facet.getAptGenSourceRootPath()));
    myAidlGenPathField.getButton().addActionListener(new MyGenSourceFieldListener(myAidlGenPathField, facet.getAidlGenSourceRootPath()));

    Module module = myContext.getModule();
    myManifestFileField.getButton().addActionListener(new MyFolderFieldListener(myManifestFileField,
                                                                                AndroidRootUtil.getManifestFile(module), true));
    myResFolderField.getButton().addActionListener(new MyFolderFieldListener(myResFolderField,
                                                                             AndroidRootUtil.getResourceDir(module), false));
    myAssetsFolderField.getButton().addActionListener(new MyFolderFieldListener(myAssetsFolderField,
                                                                                AndroidRootUtil.getAssetsDir(module), false));
    myNativeLibsFolder.getButton().addActionListener(new MyFolderFieldListener(myNativeLibsFolder,
                                                                               AndroidRootUtil.getLibsDir(module), false));

    myCustomAptSourceDirField.getButton().addActionListener(new MyFolderFieldListener(myCustomAptSourceDirField,
                                                                                      AndroidAptCompiler.getCustomResourceDirForApt(facet),
                                                                                      false));

    myPlatformChooser.addListener(new AndroidPlatformChooserListener() {
      @Override
      public void platformChanged(AndroidPlatform oldPlatform) {
        if (myAddAndroidLibrary.isSelected()) {
          updateAndroidLibrary(oldPlatform, myPlatformChooser.getSelectedPlatform());
        }
      }
    });
    myAddAndroidLibrary.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        AndroidPlatform platform = myPlatformChooser.getSelectedPlatform();
        if (myAddAndroidLibrary.isSelected()) {
          addAndroidLibrary(platform);
        }
        else {
          removeAndroidLibrary(platform);
        }
      }
    });

    myResetPathsButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        AndroidFacetConfiguration configuration = new AndroidFacetConfiguration();
        Module module = myContext.getModule();
        if (AndroidMavenUtil.isMavenizedModule(module)) {
          AndroidMavenProvider mavenProvider = AndroidMavenUtil.getMavenProvider();
          if (mavenProvider != null) {
            mavenProvider.setPathsToDefault(module, configuration);
          }
        }
        resetOptions(configuration);
      }
    });

    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCustomAptSourceDirField.setEnabled(myUseCustomSourceDirectoryRadio.isSelected());
      }
    };
    myUseCustomSourceDirectoryRadio.addActionListener(listener);
    myUseAptResDirectoryFromPathRadio.addActionListener(listener);

    myIsLibraryProjectCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean lib = myIsLibraryProjectCheckbox.isSelected();
        myAssetsFolderField.setEnabled(!lib);
        myAidlGenPathField.setEnabled(!lib);
        myGenerateIdlWhenChanged.setEnabled(!lib);
      }
    });

    listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        UIUtil.setEnabled(myAaptCompilerPanel, myEnableAaptCompiler.isSelected(), true);
        myEnableAaptCompiler.setEnabled(true);
        if (myCopyResourcesFromArtifacts.isVisible() && myCopyResourcesFromArtifacts.isSelected()) {
          UIUtil.setEnabled(myAaptCompilerPanel, false, true);
        }
      }
    };
    myEnableAaptCompiler.addActionListener(listener);
    myCopyResourcesFromArtifacts.addActionListener(listener);

    myAddResOverlayButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        CollectionListModel model = (CollectionListModel)myResOverlayList.getModel();
        Set<Object> currentItems = new HashSet<Object>();
        for (int i = 0; i < model.getSize(); i++) {
          currentItems.add(model.getElementAt(i));
        }
        VirtualFile[] files = chooserDirsUnderModule(null, false, true);
        for (VirtualFile file : files) {
          String newItem = FileUtil.toSystemDependentName(file.getPath());
          if (!currentItems.contains(newItem)) {
            model.add(newItem);
          }
        }
        myResOverlayList.setModel(model);
      }
    });

    myRemoveResOverlayButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        List<String> newItems = new ArrayList<String>();
        TIntHashSet selectedIndices = new TIntHashSet(myResOverlayList.getSelectedIndices());
        ListModel model = myResOverlayList.getModel();
        for (int i = 0; i < model.getSize(); i++) {
          String item = (String)model.getElementAt(i);
          if (!selectedIndices.contains(i)) {
            newItems.add(item);
          }
        }
        myResOverlayList.setModel(new CollectionListModel(newItems));
      }
    });

    myApkPathLabel.setLabelFor(myApkPathCombo);
    myApkPathCombo.getComboBox().setEditable(true);
    myApkPathCombo.getComboBox().setModel(new DefaultComboBoxModel(getDefaultApks(module)));
    myApkPathCombo.addBrowseFolderListener(project, new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        if (!super.isFileVisible(file, showHiddenFiles)) {
          return false;
        }
        return file.isDirectory() || "apk".equals(file.getExtension());
      }
    });
  }

  private static String[] getDefaultApks(@NotNull Module module) {
    List<String> result = new ArrayList<String>();
    String path = AndroidFacet.getOutputPackage(module);
    if (path != null) {
      result.add(path);
    }
    AndroidMavenProvider mavenProvider = AndroidMavenUtil.getMavenProvider();
    if (mavenProvider != null && mavenProvider.isMavenizedModule(module)) {
      String buildDirectory = mavenProvider.getBuildDirectory(module);
      if (buildDirectory != null) {
        result.add(FileUtil.toSystemDependentName(buildDirectory + '/' + AndroidFacet.getApkName(module)));
      }
    }
    return result.toArray(new String[result.size()]);
  }

  private boolean isUnderModuleDir(VirtualFile vFile) {
    if (vFile == null) return false;
    File file = new File(vFile.getPath());
    String moduleDirPath = new File(myContext.getModule().getModuleFilePath()).getParent();
    if (moduleDirPath != null) {
      moduleDirPath = FileUtil.toSystemIndependentName(moduleDirPath);
    }
    return moduleDirPath != null && VfsUtil.isAncestor(new File(moduleDirPath), file, true);
  }

  @Nls
  public String getDisplayName() {
    return "Android SDK Settings";
  }

  public JComponent createComponent() {
    return myContentPanel;
  }

  public boolean isModified() {
    AndroidPlatform selectedPlatform = myPlatformChooser.getSelectedPlatform();
    AndroidPlatform currentPlatform = myAppliedPlatform != null ? myAppliedPlatform : myConfiguration.getAndroidPlatform();
    if (!Comparing.equal(selectedPlatform, currentPlatform)) return true;
    if (myAddAndroidLibrary.isSelected() != myConfiguration.ADD_ANDROID_LIBRARY) return true;
    if (myIsLibraryProjectCheckbox.isSelected() != myConfiguration.LIBRARY_PROJECT) return true;

    if (checkRelativePath(myConfiguration.GEN_FOLDER_RELATIVE_PATH_APT, myRGenPathField.getText())) {
      return true;
    }

    if (checkRelativePath(myConfiguration.GEN_FOLDER_RELATIVE_PATH_AIDL, myAidlGenPathField.getText())) {
      return true;
    }

    if (checkRelativePath(myConfiguration.MANIFEST_FILE_RELATIVE_PATH, myManifestFileField.getText())) {
      return true;
    }

    if (checkRelativePath(myConfiguration.RES_FOLDER_RELATIVE_PATH, myResFolderField.getText())) {
      return true;
    }

    if (checkRelativePath(myConfiguration.ASSETS_FOLDER_RELATIVE_PATH, myAssetsFolderField.getText())) {
      return true;
    }

    if (checkRelativePath(myConfiguration.LIBS_FOLDER_RELATIVE_PATH, myNativeLibsFolder.getText())) {
      return true;
    }

    if (checkRelativePath(myConfiguration.APK_PATH, (String)myApkPathCombo.getComboBox().getEditor().getItem())) {
      return true;
    }

    if (myGenerateRJavaWhenChanged.isSelected() != myConfiguration.REGENERATE_R_JAVA) {
      return true;
    }

    if (myGenerateIdlWhenChanged.isSelected() != myConfiguration.REGENERATE_JAVA_BY_AIDL) {
      return true;
    }

    if (myUseCustomSourceDirectoryRadio.isSelected() != myConfiguration.USE_CUSTOM_APK_RESOURCE_FOLDER) {
      return true;
    }

    if (checkRelativePath(myConfiguration.CUSTOM_APK_RESOURCE_FOLDER, myCustomAptSourceDirField.getText())) {
      return true;
    }
    if (myCopyResourcesFromArtifacts.isSelected() != myConfiguration.COPY_RESOURCES_FROM_ARTIFACTS) {
      return true;
    }
    if (myGenerateUnsignedApk.isSelected() != myConfiguration.GENERATE_UNSIGNED_APK) {
      return true;
    }

    if (myEnableAaptCompiler.isSelected() != myConfiguration.ENABLE_AAPT_COMPILER) {
      return true;
    }

    List<String> currentResOverlayFolders = new ArrayList<String>();
    for (String folder : myConfiguration.RES_OVERLAY_FOLDERS) {
      String absPath = toAbsolutePath(folder);
      if (absPath != null) {
        currentResOverlayFolders.add(absPath);
      }
    }
    Collections.sort(currentResOverlayFolders);

    List<String> newResFolders = new ArrayList<String>();
    ListModel model = myResOverlayList.getModel();
    for (int i = 0; i < model.getSize(); i++) {
      String element = (String)model.getElementAt(i);
      newResFolders.add(element);
    }
    Collections.sort(newResFolders);

    return !currentResOverlayFolders.equals(newResFolders);
  }

  private boolean checkRelativePath(String relativePathFromConfig, String absPathFromTextField) {
    String pathFromConfig = relativePathFromConfig;
    if (pathFromConfig != null && pathFromConfig.length() > 0) {
      pathFromConfig = toAbsolutePath(pathFromConfig);
    }
    String pathFromTextField = absPathFromTextField.trim();
    return !Comparing.equal(pathFromConfig, pathFromTextField);
  }

  // if library was removed in the same project-structure dialog

  @Nullable
  private static LibraryOrderEntry findLibraryOrderEntryByName(@NotNull ModifiableRootModel model, @NotNull String name) {
    for (OrderEntry entry : model.getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        if (name.equals(((LibraryOrderEntry)entry).getLibraryName())) {
          return (LibraryOrderEntry)entry;
        }
      }
    }
    return null;
  }

  @Nullable
  private String toRelativePath(String absPath) {
    absPath = FileUtil.toSystemIndependentName(absPath);
    String moduleDirPath = new File(myContext.getModule().getModuleFilePath()).getParent();
    if (moduleDirPath != null) {
      moduleDirPath = FileUtil.toSystemIndependentName(moduleDirPath);
      //if (VfsUtil.isAncestor(new File(moduleDirPath), new File(absPath), true)) {
        return FileUtil.getRelativePath(moduleDirPath, absPath, '/');
      //}
    }
    return null;
  }

  @Override
  public String getHelpTopic() {
    return "reference.settings.project.modules.android.facet";
  }

  public void apply() throws ConfigurationException {
    if (!isModified()) return;
    String absGenPathR = myRGenPathField.getText().trim();
    String absGenPathAidl = myAidlGenPathField.getText().trim();

    boolean runApt = false;
    boolean runIdl = false;

    if (absGenPathR == null || absGenPathR.length() == 0 || absGenPathAidl == null || absGenPathAidl.length() == 0) {
      throw new ConfigurationException("Please specify source root for autogenerated files");
    }
    else {
      String relativeGenPathR = getAndCheckRelativePath(absGenPathR, false);
      String newAptDestDir = '/' + relativeGenPathR;
      if (!newAptDestDir.equals(myConfiguration.GEN_FOLDER_RELATIVE_PATH_APT)) {
        runApt = true;
      }
      myConfiguration.GEN_FOLDER_RELATIVE_PATH_APT = newAptDestDir;

      String relativeGenPathAidl = getAndCheckRelativePath(absGenPathAidl, false);
      String newIdlDestDir = '/' + relativeGenPathAidl;
      if (!newIdlDestDir.equals(myConfiguration.GEN_FOLDER_RELATIVE_PATH_AIDL)) {
        runIdl = true;
      }
      myConfiguration.GEN_FOLDER_RELATIVE_PATH_AIDL = newIdlDestDir;
    }

    String absManifestPath = myManifestFileField.getText().trim();
    if (absManifestPath.length() == 0) {
      throw new ConfigurationException("Manifest file not specified");
    }
    String manifestRelPath = getAndCheckRelativePath(absManifestPath, true);
    if (!SdkConstants.FN_ANDROID_MANIFEST_XML.equals(AndroidUtils.getSimpleNameByRelativePath(manifestRelPath))) {
      throw new ConfigurationException("Manifest file must have name AndroidManifest.xml");
    }
    myConfiguration.MANIFEST_FILE_RELATIVE_PATH = '/' + manifestRelPath;

    String absResPath = myResFolderField.getText().trim();
    if (absResPath.length() == 0) {
      throw new ConfigurationException("Resources folder not specified");
    }
    myConfiguration.RES_FOLDER_RELATIVE_PATH = '/' + getAndCheckRelativePath(absResPath, false);

    String absAssetsPath = myAssetsFolderField.getText().trim();
    if (absResPath.length() == 0) {
      throw new ConfigurationException("Assets folder not specified");
    }
    myConfiguration.ASSETS_FOLDER_RELATIVE_PATH = '/' + getAndCheckRelativePath(absAssetsPath, false);

    String absApkPath = (String)myApkPathCombo.getComboBox().getEditor().getItem();
    if (absApkPath.length() == 0) {
      myConfiguration.APK_PATH = "";
    }
    else {
      myConfiguration.APK_PATH = '/' + getAndCheckRelativePath(absApkPath, false);
    }

    String absLibsPath = myNativeLibsFolder.getText().trim();
    if (absLibsPath.length() == 0) {
      throw new ConfigurationException("Native libs folder not specified");
    }
    myConfiguration.LIBS_FOLDER_RELATIVE_PATH = '/' + getAndCheckRelativePath(absLibsPath, false);

    myConfiguration.ADD_ANDROID_LIBRARY = myAddAndroidLibrary.isSelected();

    if (myConfiguration.LIBRARY_PROJECT != myIsLibraryProjectCheckbox.isSelected()) {
      runApt = true;
    }

    myConfiguration.LIBRARY_PROJECT = myIsLibraryProjectCheckbox.isSelected();

    myConfiguration.COPY_RESOURCES_FROM_ARTIFACTS = myCopyResourcesFromArtifacts.isSelected();

    myConfiguration.GENERATE_UNSIGNED_APK = myGenerateUnsignedApk.isSelected();

    ListModel model = myResOverlayList.getModel();
    String[] newResOverlayValue = new String[model.getSize()];
    for (int i = 0; i < model.getSize(); i++) {
      String element = (String)model.getElementAt(i);
      newResOverlayValue[i] = '/' + getAndCheckRelativePath(element, false);
    }
    myConfiguration.RES_OVERLAY_FOLDERS = newResOverlayValue;

    boolean useCustomAptSrc = myUseCustomSourceDirectoryRadio.isSelected();

    if (myConfiguration.USE_CUSTOM_APK_RESOURCE_FOLDER != useCustomAptSrc) {
      runApt = true;
    }
    myConfiguration.USE_CUSTOM_APK_RESOURCE_FOLDER = useCustomAptSrc;

    if (myConfiguration.REGENERATE_R_JAVA != myGenerateRJavaWhenChanged.isSelected()) {
      runApt = true;
    }
    myConfiguration.REGENERATE_R_JAVA = myGenerateRJavaWhenChanged.isSelected();

    if (myConfiguration.ENABLE_AAPT_COMPILER != myEnableAaptCompiler.isSelected()) {
      runApt = true;
    }
    myConfiguration.ENABLE_AAPT_COMPILER = myEnableAaptCompiler.isSelected();

    if (myConfiguration.REGENERATE_JAVA_BY_AIDL != myGenerateIdlWhenChanged.isSelected()) {
      runIdl = true;
    }
    myConfiguration.REGENERATE_JAVA_BY_AIDL = myGenerateIdlWhenChanged.isSelected();

    String absAptSourcePath = myCustomAptSourceDirField.getText().trim();
    if (useCustomAptSrc) {
      if (absAptSourcePath.length() == 0) {
        throw new ConfigurationException("Resources folder not specified");
      }
      String newCustomAptSourceFolder = '/' + getAndCheckRelativePath(absAptSourcePath, false);
      if (!newCustomAptSourceFolder.equals(myConfiguration.CUSTOM_APK_RESOURCE_FOLDER)) {
        runApt = true;
      }
      myConfiguration.CUSTOM_APK_RESOURCE_FOLDER = newCustomAptSourceFolder;
    }
    else {
      String relPath = toRelativePath(absAptSourcePath);
      myConfiguration.CUSTOM_APK_RESOURCE_FOLDER = relPath != null ? '/' + relPath : "";
    }

    final AndroidPlatform platform = myPlatformChooser.getSelectedPlatform();
    myConfiguration.setAndroidPlatform(platform);
    final AndroidFacet facet = myConfiguration.getFacet();
    if (facet != null) {
      myPlatformChooser.apply();
      myAppliedPlatform = platform;
    }

    runApt = runApt && myConfiguration.REGENERATE_R_JAVA && AndroidAptCompiler.isToCompileModule(myContext.getModule(), myConfiguration);
    runIdl = runIdl && myConfiguration.REGENERATE_JAVA_BY_AIDL;
    if (runApt || runIdl) {
      final boolean finalRunApt = runApt;
      final boolean finalRunIdl = runIdl;
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          Module module = myContext.getModule();
          Project project = module.getProject();
          if (finalRunApt) {
            AndroidCompileUtil.generate(module, new AndroidAptCompiler(), true);
          }
          if (finalRunIdl) {
            AndroidCompileUtil.generate(module, new AndroidIdlCompiler(project));
          }
        }
      });
    }
  }

  @NotNull
  private String[] getNewResOverlayValue() throws ConfigurationException {
    ListModel model = myResOverlayList.getModel();
    String[] newResOverlayValue = new String[model.getSize()];
    for (int i = 0; i < model.getSize(); i++) {
      String element = (String)model.getElementAt(i);
      newResOverlayValue[i] = '/' + getAndCheckRelativePath(element, false);
    }
    return newResOverlayValue;
  }

  private String getAndCheckRelativePath(String absPath, boolean checkExists) throws ConfigurationException {
    if (absPath.indexOf('/') < 0 && absPath.indexOf(File.separatorChar) < 0) {
      throw new ConfigurationException(AndroidBundle.message("file.must.be.under.module.error", FileUtil.toSystemDependentName(absPath)));
    }
    String relativeGenPathR = toRelativePath(absPath);
    if (relativeGenPathR == null || relativeGenPathR.length() == 0) {
      throw new ConfigurationException(AndroidBundle.message("file.must.be.under.module.error", FileUtil.toSystemDependentName(absPath)));
    }
    if (checkExists && LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(absPath)) == null) {
      throw new ConfigurationException(AndroidBundle.message("android.file.not.exist.error", FileUtil.toSystemDependentName(absPath)));
    }
    return relativeGenPathR;
  }

  private void updateAndroidLibrary(AndroidPlatform oldPlatform, AndroidPlatform platform) {
    removeAndroidLibrary(oldPlatform);
    addAndroidLibrary(platform);
  }

  private void addAndroidLibrary(AndroidPlatform platform) {
    if (platform != null) {
      Library library = platform.getLibrary();
      // library can be removed in the same session
      if (ArrayUtil.find(myPlatformChooser.getLibraryTableModel().getLibraries(), library) >= 0) {
        LibraryOrderEntry entry = myContext.getModifiableRootModel().addLibraryEntry(library);
        entry.setScope(DependencyScope.PROVIDED);
      }
    }
  }

  private void removeAndroidLibrary(AndroidPlatform platform) {
    if (platform != null) {
      ModifiableRootModel model = myContext.getModifiableRootModel();
      LibraryOrderEntry entry = model.findLibraryOrderEntry(platform.getLibrary());
      if (entry == null) {
        entry = findLibraryOrderEntryByName(model, platform.getLibrary().getName());
      }
      if (entry != null) {
        model.removeOrderEntry(entry);
      }
    }
  }

  public void reset() {
    myAppliedPlatform = null;
    myPlatformChooser.rebuildPlatforms();
    AndroidPlatform platform = myConfiguration.getAndroidPlatform();
    myPlatformChooser.setSelectedPlatform(platform);

    resetOptions(myConfiguration);

    myAddAndroidLibrary.setSelected(myConfiguration.ADD_ANDROID_LIBRARY);
    myIsLibraryProjectCheckbox.setSelected(myConfiguration.LIBRARY_PROJECT);
  }

  private void resetOptions(AndroidFacetConfiguration configuration) {
    String aptGenPath = configuration.GEN_FOLDER_RELATIVE_PATH_APT;
    String aptAbspath = aptGenPath.length() > 0 ? toAbsolutePath(aptGenPath) : "";
    myRGenPathField.setText(aptAbspath != null ? aptAbspath : "");

    String aidlGenPath = configuration.GEN_FOLDER_RELATIVE_PATH_AIDL;
    String aidlAbsPath = aidlGenPath.length() > 0 ? toAbsolutePath(aidlGenPath) : "";
    myAidlGenPathField.setText(aidlAbsPath != null ? aidlAbsPath : "");

    String manifestPath = configuration.MANIFEST_FILE_RELATIVE_PATH;
    String manifestAbsPath = manifestPath.length() > 0 ? toAbsolutePath(manifestPath) : "";
    myManifestFileField.setText(manifestAbsPath != null ? manifestAbsPath : "");

    String resPath = configuration.RES_FOLDER_RELATIVE_PATH;
    String resAbsPath = resPath.length() > 0 ? toAbsolutePath(resPath) : "";
    myResFolderField.setText(resAbsPath != null ? resAbsPath : "");

    String assetsPath = configuration.ASSETS_FOLDER_RELATIVE_PATH;
    String assetsAbsPath = assetsPath.length() > 0 ? toAbsolutePath(assetsPath) : "";
    myAssetsFolderField.setText(assetsAbsPath != null ? assetsAbsPath : "");

    String libsPath = configuration.LIBS_FOLDER_RELATIVE_PATH;
    String libsAbsPath = libsPath.length() > 0 ? toAbsolutePath(libsPath) : "";
    myNativeLibsFolder.setText(libsAbsPath != null ? libsAbsPath : "");

    myGenerateRJavaWhenChanged.setSelected(configuration.REGENERATE_R_JAVA);
    myGenerateIdlWhenChanged.setSelected(configuration.REGENERATE_JAVA_BY_AIDL);

    myUseCustomSourceDirectoryRadio.setSelected(configuration.USE_CUSTOM_APK_RESOURCE_FOLDER);
    myUseAptResDirectoryFromPathRadio.setSelected(!configuration.USE_CUSTOM_APK_RESOURCE_FOLDER);

    String aptSourcePath = configuration.CUSTOM_APK_RESOURCE_FOLDER;
    String aptSourceAbsPath = aptSourcePath.length() > 0 ? toAbsolutePath(aptSourcePath) : "";
    myCustomAptSourceDirField.setText(aptSourceAbsPath != null ? aptSourceAbsPath : "");
    myCustomAptSourceDirField.setEnabled(configuration.USE_CUSTOM_APK_RESOURCE_FOLDER);

    String apkPath = configuration.APK_PATH;
    String apkAbsPath = apkPath.length() > 0 ? toAbsolutePath(apkPath) : "";
    myApkPathCombo.getComboBox().getEditor().setItem(apkAbsPath != null ? apkAbsPath : "");

    boolean mavenizedModule = AndroidMavenUtil.isMavenizedModule(myContext.getModule());
    myCopyResourcesFromArtifacts.setVisible(mavenizedModule);
    myCopyResourcesFromArtifacts.setSelected(myConfiguration.COPY_RESOURCES_FROM_ARTIFACTS);

    myGenerateUnsignedApk.setSelected(myConfiguration.GENERATE_UNSIGNED_APK);

    myResOverlayPanel.setVisible(mavenizedModule);

    String[] resOverlayFolders = configuration.RES_OVERLAY_FOLDERS;
    List<String> items = new ArrayList<String>();
    for (int i = 0, n = resOverlayFolders.length; i < n; i++) {
      String relPath = resOverlayFolders[i];
      if (relPath.length() > 0) {
        String absPath = toAbsolutePath(relPath);
        if (absPath != null && absPath.length() > 0) {
          items.add(absPath);
        }
      }
    }
    myResOverlayList.setModel(new CollectionListModel(items));

    myEnableAaptCompiler.setSelected(myConfiguration.ENABLE_AAPT_COMPILER);
    UIUtil.setEnabled(myAaptCompilerPanel, myEnableAaptCompiler.isSelected(), true);
    myEnableAaptCompiler.setEnabled(true);
    if (myCopyResourcesFromArtifacts.isSelected() && myCopyResourcesFromArtifacts.isVisible()) {
      UIUtil.setEnabled(myAaptCompilerPanel, false, true);
    }
  }

  @Nullable
  private String toAbsolutePath(String genRelativePath) {
    String moduleDirPath = new File(myContext.getModule().getModuleFilePath()).getParent();
    if (moduleDirPath == null) return null;
    try {
      return new File(moduleDirPath + genRelativePath).getCanonicalPath();
    }
    catch (IOException e) {
      LOG.info(e);
      return moduleDirPath + genRelativePath;
    }
  }

  public void disposeUIResources() {
    Disposer.dispose(myPlatformChooser);
  }

  private void createUIComponents() {
    // TODO: place custom component creation code here
  }

  private class MyGenSourceFieldListener implements ActionListener {
    private final TextFieldWithBrowseButton myTextField;
    private final String myDefaultPath;

    private MyGenSourceFieldListener(TextFieldWithBrowseButton textField, String defaultPath) {
      myTextField = textField;
      myDefaultPath = defaultPath;
    }

    public void actionPerformed(ActionEvent e) {
      VirtualFile initialFile = null;
      String path = myTextField.getText().trim();
      if (path.length() == 0) {
        path = myDefaultPath;
      }
      if (path != null) {
        initialFile = LocalFileSystem.getInstance().findFileByPath(path);
      }
      if (initialFile == null) {
        Module module = myContext.getModule();
        ModuleRootManager manager = ModuleRootManager.getInstance(module);
        VirtualFile[] sourceRoots = manager.getSourceRoots();
        if (sourceRoots.length > 0) {
          initialFile = sourceRoots[0];
        }
        else {
          initialFile = module.getModuleFile();
          if (initialFile == null) {
            String p = new File(myContext.getModule().getModuleFilePath()).getParent();
            initialFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(p));
          }
        }
      }
      VirtualFile[] files = FileChooser.chooseFiles(myContentPanel, new FileChooserDescriptor(false, true, false, false, false, false) /*{
        @Override
        public void validateSelectedFiles(VirtualFile[] files) throws Exception {
          assert files.length == 1;
          VirtualFile file = files[0];
          if (!isUnderModuleDir(file)) {
            throw new Exception(AndroidBundle.message("file.must.be.under.module.error", FileUtil.toSystemDependentName(file.getPath())));
          }
        }
      }*/, initialFile);
      if (files.length > 0) {
        assert files.length == 1;
        myTextField.setText(FileUtil.toSystemDependentName(files[0].getPath()));
      }
    }
  }

  private class MyFolderFieldListener implements ActionListener {
    private final TextFieldWithBrowseButton myTextField;
    private final VirtualFile myDefaultDir;
    private final boolean myManifest;

    public MyFolderFieldListener(TextFieldWithBrowseButton textField, VirtualFile defaultDir, boolean manifest) {
      myTextField = textField;
      myDefaultDir = defaultDir;
      myManifest = manifest;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      VirtualFile initialFile = null;
      String path = myTextField.getText().trim();
      if (path.length() == 0) {
        VirtualFile dir = myDefaultDir;
        path = dir != null ? dir.getPath() : null;
      }
      if (path != null) {
        initialFile = LocalFileSystem.getInstance().findFileByPath(path);
      }
      VirtualFile[] files = chooserDirsUnderModule(initialFile, myManifest, false);
      if (files.length > 0) {
        assert files.length == 1;
        myTextField.setText(FileUtil.toSystemDependentName(files[0].getPath()));
      }
    }
  }

  private VirtualFile[] chooserDirsUnderModule(@Nullable VirtualFile initialFile, final boolean chooseManifest, boolean chooseMultiple) {
    if (initialFile == null) {
      initialFile = myContext.getModule().getModuleFile();
    }
    if (initialFile == null) {
      String p = new File(myContext.getModule().getModuleFilePath()).getParent();
      initialFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(p));
    }
    return FileChooser
      .chooseFiles(myContentPanel, new FileChooserDescriptor(chooseManifest, !chooseManifest, false, false, false, chooseMultiple) {
        @Override
        public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
          if (!super.isFileVisible(file, showHiddenFiles)) {
            return false;
          }
          return file.isDirectory() || !chooseManifest || SdkConstants.FN_ANDROID_MANIFEST_XML.equals(file.getName());
        }

        /*@Override
        public void validateSelectedFiles(VirtualFile[] files) throws Exception {
          for (VirtualFile file : files) {
            if (!isUnderModuleDir(file)) {
              throw new Exception(AndroidBundle.message("file.must.be.under.module.error", FileUtil.toSystemDependentName(file.getPath())));
            }
          }
        }*/
      }, initialFile);
  }
}
