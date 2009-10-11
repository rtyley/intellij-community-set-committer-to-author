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

package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class SdkConfigurationUtil {
  private SdkConfigurationUtil() {
  }

  @Nullable
  public static Sdk addSdk(final Project project, final SdkType... sdkTypes) {
    if (sdkTypes.length == 0) return null;
    final FileChooserDescriptor descriptor = createCompositeDescriptor(sdkTypes);
    final FileChooserDialog dialog = FileChooserFactory.getInstance().createFileChooser(descriptor, project);
    String suggestedPath = sdkTypes [0].suggestHomePath();
    VirtualFile suggestedDir = suggestedPath == null
                               ? null
                               :  LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(suggestedPath));
    final VirtualFile[] selection = dialog.choose(suggestedDir, project);
    if (selection.length > 0) {
      for (SdkType sdkType : sdkTypes) {
        if (sdkType.isValidSdkHome(selection[0].getPath())) {
          return setupSdk(selection[0], sdkType);
        }
      }
    }
    return null;
  }

  private static FileChooserDescriptor createCompositeDescriptor(final SdkType... sdkTypes) {
    FileChooserDescriptor descriptor0 = sdkTypes [0].getHomeChooserDescriptor();
    FileChooserDescriptor descriptor = new FileChooserDescriptor(descriptor0.isChooseFiles(), descriptor0.isChooseFolders(),
                                                                 descriptor0.isChooseJars(), descriptor0.isChooseJarsAsFiles(),
                                                                 descriptor0.isChooseJarContents(), descriptor0.isChooseMultiple()) {

      @Override
      public void validateSelectedFiles(final VirtualFile[] files) throws Exception {
        if (files.length > 0) {
          for (SdkType type : sdkTypes) {
            if (type.isValidSdkHome(files[0].getPath())) {
              return;
            }
          }
        }
        String message = files.length > 0 && files[0].isDirectory()
                         ? ProjectBundle.message("sdk.configure.home.invalid.error", sdkTypes [0].getPresentableName())
                         : ProjectBundle.message("sdk.configure.home.file.invalid.error", sdkTypes [0].getPresentableName());
        throw new Exception(message);
      }
    };
    descriptor.setTitle(descriptor0.getTitle());
    return descriptor;
  }

  public static void removeSdk(final Sdk sdk) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ProjectJdkTable.getInstance().removeJdk(sdk);
      }
    });
  }


  public static Sdk setupSdk(final VirtualFile homeDir, final SdkType sdkType) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<Sdk>() {
        public Sdk compute(){
          final Sdk[] sdks = ProjectJdkTable.getInstance().getAllJdks();
          final String sdkName = createUniqueSdkName(sdkType, homeDir.getPath(), Arrays.asList(sdks));
          final ProjectJdkImpl projectJdk = new ProjectJdkImpl(sdkName, sdkType);
          projectJdk.setHomePath(homeDir.getPath());
          sdkType.setupSdkPaths(projectJdk);
          ProjectJdkTable.getInstance().addJdk(projectJdk);
          return projectJdk;
        }
    });
  }

  public static void setDirectoryProjectSdk(final Project project, final Sdk sdk) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ProjectRootManager.getInstance(project).setProjectJdk(sdk);
        final Module[] modules = ModuleManager.getInstance(project).getModules();
        if (modules.length > 0) {
          final ModifiableRootModel model = ModuleRootManager.getInstance(modules[0]).getModifiableModel();
          model.inheritSdk();
          model.commit();
        }
      }
    });
  }

  public static void configureDirectoryProjectSdk(final Project project, final SdkType... sdkTypes) {
    Sdk existingSdk = ProjectRootManager.getInstance(project).getProjectJdk();
    if (existingSdk != null && ArrayUtil.contains(existingSdk.getSdkType(), sdkTypes)) {
      return;
    }

    Sdk sdk = findOrCreateSdk(sdkTypes);
    if (sdk != null) {
      setDirectoryProjectSdk(project, sdk);
    }
  }

  @Nullable
  public static Sdk findOrCreateSdk(final SdkType... sdkTypes) {
    final Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    final Sdk sdk = ProjectRootManager.getInstance(defaultProject).getProjectJdk();
    if (sdk != null) {
      for (SdkType type : sdkTypes) {
        if (sdk.getSdkType() == type) {
          return sdk;
        }
      }
    }
    for (SdkType type : sdkTypes) {
      List<Sdk> sdks = ProjectJdkTable.getInstance().getSdksOfType(type);
      if (sdks.size() > 0) {
        return sdks.get(0);
      }
    }
    for (SdkType sdkType : sdkTypes) {
      final String suggestedHomePath = sdkType.suggestHomePath();
      if (suggestedHomePath != null && sdkType.isValidSdkHome(suggestedHomePath)) {
        VirtualFile sdkHome = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
          public VirtualFile compute() {
            return LocalFileSystem.getInstance().refreshAndFindFileByPath(suggestedHomePath);
          }
        });
        if (sdkHome != null) {
          return setupSdk(sdkHome, sdkType);
        }
      }
    }
    return null;
  }

  public static String createUniqueSdkName(SdkType type, String home, final Collection<Sdk> sdks) {
    final Set<String> names = new HashSet<String>();
    for (Sdk jdk : sdks) {
      names.add(jdk.getName());
    }
    final String suggestedName = type.suggestSdkName(null, home);
    String newSdkName = suggestedName;
    int i = 0;
    while (names.contains(newSdkName)) {
      newSdkName = suggestedName + " (" + (++i) + ")";
    }
    return newSdkName;
  }
}
