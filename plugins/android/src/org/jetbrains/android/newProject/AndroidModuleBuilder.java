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

package org.jetbrains.android.newProject;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ExternalChangeAction;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.AndroidFileTemplateProvider;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.run.testing.AndroidTestRunConfiguration;
import org.jetbrains.android.run.testing.AndroidTestRunConfigurationType;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static com.android.sdklib.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static org.jetbrains.android.util.AndroidUtils.createChildDirectoryIfNotExist;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Jun 26, 2009
 * Time: 7:32:27 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidModuleBuilder extends JavaModuleBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.newProject.AndroidModuleBuilder");

  private AndroidPlatform myPlatform;
  private String myPackageName;
  private String myApplicationName;
  private String myActivityName;
  private ProjectType myProjectType;
  private Module myTestedModule;

  public void setupRootModel(final ModifiableRootModel rootModel) throws ConfigurationException {
    super.setupRootModel(rootModel);
    PropertiesComponent.getInstance().setValue(AndroidSdkUtils.DEFAULT_PLATFORM_NAME_PROPERTY, myPlatform.getName());
    VirtualFile[] files = rootModel.getContentRoots();
    if (files.length > 0) {
      final VirtualFile contentRoot = files[0];
      final AndroidFacet facet = addAndroidFacetAndLibrary(rootModel);
      final Project project = rootModel.getProject();
      final VirtualFile sourceRoot = findSourceRoot(rootModel);

      if (myProjectType == ProjectType.TEST) {
        assert myTestedModule != null;
        ModuleOrderEntry entry = rootModel.addModuleOrderEntry(myTestedModule);
        entry.setScope(DependencyScope.PROVIDED);
      }

      StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
        public void run() {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  createProject(contentRoot, sourceRoot, facet);
                }
              });
            }
          });
        }
      });
    }
  }

  private void createProject(VirtualFile contentRoot, VirtualFile sourceRoot, AndroidFacet facet) {
    if (myProjectType == ProjectType.APPLICATION) {
      createDirectoryStructure(contentRoot, sourceRoot, facet);
    }
    else {
      createProjectByAndroidTool(contentRoot, sourceRoot, facet);
    }
  }

  private void createDirectoryStructure(VirtualFile contentRoot, VirtualFile sourceRoot, AndroidFacet facet) {
    if (isHelloAndroid()) {
      if (createProjectByAndroidTool(contentRoot, sourceRoot, facet)) {
        return;
      }
    }
    Project project = facet.getModule().getProject();
    createManifestFile(project, contentRoot);
    createResourcesAndLibs(project, contentRoot);
    PsiDirectory sourceDir = sourceRoot != null ? PsiManager.getInstance(project).findDirectory(sourceRoot) : null;
    createActivityAndSetupManifest(facet, sourceDir);
    addRunConfiguration(facet);
  }

  private boolean createProjectByAndroidTool(final VirtualFile contentRoot,
                                             final VirtualFile sourceRoot,
                                             final AndroidFacet facet) {
    AndroidFacetConfiguration configuration = facet.getConfiguration();
    AndroidPlatform platform = configuration.getAndroidPlatform();
    IAndroidTarget target = configuration.getAndroidTarget();

    final Module module = facet.getModule();

    if (platform != null && target != null) {
      final String androidToolPath =
        platform.getSdk().getLocation() + File.separator + AndroidUtils.toolPath(SdkConstants.androidCmdName());

      if (!new File(androidToolPath).exists()) {
        return false;
      }

      final GeneralCommandLine commandLine = new GeneralCommandLine();
      commandLine.setExePath(FileUtil.toSystemDependentName(androidToolPath));

      commandLine.addParameter("create");

      switch (myProjectType) {
        case APPLICATION:
          commandLine.addParameter("project");
          break;
        case LIBRARY:
          commandLine.addParameter("lib-project");
          break;
        case TEST:
          commandLine.addParameter("test-project");
          break;
      }

      commandLine.addParameters("--name");
      commandLine.addParameter(module.getName());

      commandLine.addParameters("--path");
      commandLine.addParameter(FileUtil.toSystemDependentName(contentRoot.getPath()));

      if (myProjectType == ProjectType.APPLICATION || myProjectType == ProjectType.LIBRARY) {
        String apiLevel = target.hashString();
        commandLine.addParameter("--target");
        commandLine.addParameter(apiLevel);
        commandLine.addParameter("--package");
        commandLine.addParameter(myPackageName);
      }

      if (myProjectType == ProjectType.APPLICATION) {
        commandLine.addParameter("--activity");
        commandLine.addParameter(myActivityName);
      }
      else if (myProjectType == ProjectType.TEST) {
        String moduleDirPath = new File(myTestedModule.getModuleFilePath()).getParent();
        assert moduleDirPath != null;
        commandLine.addParameter("--main");
        commandLine.addParameter(moduleDirPath);
      }

      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          final Project project = module.getProject();
          AndroidUtils.runExternalTool(project, commandLine, null);
          StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
            public void run() {
              FileDocumentManager.getInstance().saveAllDocuments();
            }
          });
          contentRoot.refresh(false, true);

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                  try {
                    if (project.isDisposed()) {
                      return;
                    }
                    if (myProjectType == ProjectType.APPLICATION) {
                      Manifest manifest = facet.getManifest();
                      if (manifest != null && myApplicationName.length() > 0) {
                        manifest.getApplication().getLabel().setValue(ResourceValue.literal(myApplicationName));
                      }
                      createChildDirectoryIfNotExist(project, contentRoot, SdkConstants.FD_ASSETS);
                      createChildDirectoryIfNotExist(project, contentRoot, SdkConstants.FD_NATIVE_LIBS);
                    }
                    VirtualFile srcDir = contentRoot.findChild(SdkConstants.FD_SOURCES);
                    if (srcDir != sourceRoot) {
                      moveContentAndRemoveDir(project, srcDir, sourceRoot);
                    }
                  }
                  catch (IOException e) {
                    LOG.error(e);
                  }
                }
              });
              if (myProjectType == ProjectType.APPLICATION) {
                addRunConfiguration(facet);
              }
              else if (myProjectType == ProjectType.TEST) {
                addTestRunConfiguration(facet);
              }
            }
          });
        }
      });
    }
    return true;
  }

  private static void moveContentAndRemoveDir(Project project, VirtualFile from, VirtualFile to) throws IOException {
    for (VirtualFile child : from.getChildren()) {
      child.move(project, to);
    }
    from.delete(project);
  }

  private static void createManifestFile(Project project, VirtualFile contentRoot) {
    VirtualFile existingManifestFile = contentRoot.findChild(FN_ANDROID_MANIFEST_XML);
    if (existingManifestFile != null) {
      return;
    }
    try {
      AndroidFileTemplateProvider
        .createFromTemplate(project, contentRoot, AndroidFileTemplateProvider.ANDROID_MANIFEST_TEMPLATE, FN_ANDROID_MANIFEST_XML);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private static boolean isFirstModule(@NotNull Module module) {
    Project project = module.getProject();
    Module[] modules = ModuleManager.getInstance(project).getModules();
    return modules.length == 0 || (modules.length == 1 && ArrayUtil.find(modules, module) >= 0);
  }

  private void addRunConfiguration(AndroidFacet facet) {
    if (isHelloAndroid()) {
      String activityClass = myPackageName + '.' + myActivityName;
      Module module = facet.getModule();
      AndroidUtils.addRunConfiguration(module.getProject(), facet, activityClass, !isFirstModule(module));
    }
  }

  private static void addTestRunConfiguration(final AndroidFacet facet) {
    String moduleName = facet.getModule().getName();
    Project project = facet.getModule().getProject();
    int result = Messages.showYesNoDialog(project, AndroidBundle.message("create.run.configuration.question", moduleName),
                                          AndroidBundle.message("create.run.configuration.title"), Messages.getQuestionIcon());
    if (result == 0) {
      RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
      Module module = facet.getModule();
      RunnerAndConfigurationSettings settings = runManager
        .createRunConfiguration(module.getName(), AndroidTestRunConfigurationType.getInstance().getFactory());
      AndroidTestRunConfiguration configuration = (AndroidTestRunConfiguration)settings.getConfiguration();
      configuration.setModule(module);
      runManager.addConfiguration(settings, false);
      runManager.setActiveConfiguration(settings);
    }
  }

  private boolean isHelloAndroid() {
    return myActivityName.length() > 0;
  }

  @Nullable
  private static VirtualFile findSourceRoot(ModifiableRootModel model) {
    VirtualFile genSourceRoot = AndroidRootUtil.getStandartGenDir(model.getModule());
    for (VirtualFile root : model.getSourceRoots()) {
      if (root != genSourceRoot) {
        return root;
      }
    }
    return null;
  }

  @Nullable
  private static PsiDirectory createPackageIfPossible(final PsiDirectory sourceDir, String packageName) {
    if (sourceDir != null) {
      final String[] ids = packageName.split("\\.");
      return ApplicationManager.getApplication().runWriteAction(new Computable<PsiDirectory>() {
        public PsiDirectory compute() {
          PsiDirectory dir = sourceDir;
          for (String id : ids) {
            PsiDirectory child = dir.findSubdirectory(id);
            dir = child == null ? dir.createSubdirectory(id) : child;
          }
          return dir;
        }
      });
    }
    return null;
  }

  private void createActivityAndSetupManifest(final AndroidFacet facet, final PsiDirectory sourceDir) {
    if (myPackageName != null) {
      CommandProcessor.getInstance().executeCommand(facet.getModule().getProject(), new ExternalChangeAction() {
        public void run() {
          Runnable action = new Runnable() {
            public void run() {
              PsiDirectory packageDir = createPackageIfPossible(sourceDir, myPackageName);
              if (packageDir == null) return;
              final Manifest manifest = facet.getManifest();
              if (manifest != null) {
                manifest.getPackage().setValue(myPackageName);
                final Project project = facet.getModule().getProject();
                StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
                  public void run() {
                    FileDocumentManager.getInstance().saveAllDocuments();
                  }
                });
                if (myApplicationName.length() > 0) {
                  manifest.getApplication().getLabel().setValue(ResourceValue.literal(myApplicationName));
                }
              }
            }
          };
          ApplicationManager.getApplication().runWriteAction(action);
        }
      }, AndroidBundle.message("build.android.module.process.title"), null);
    }
  }

  @NotNull
  private AndroidFacet addAndroidFacetAndLibrary(ModifiableRootModel rootModel) {
    Module module = rootModel.getModule();
    final FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel model = facetManager.createModifiableModel();
    AndroidFacet facet = facetManager.createFacet(AndroidFacet.getFacetType(), "Android", null);
    AndroidFacetConfiguration configuration = facet.getConfiguration();
    configuration.setAndroidPlatform(myPlatform);
    if (myProjectType == ProjectType.LIBRARY) {
      configuration.LIBRARY_PROJECT = true;
    }
    model.addFacet(facet);
    if (configuration.ADD_ANDROID_LIBRARY) {
      LibraryOrderEntry libraryEntry = rootModel.addLibraryEntry(myPlatform.getLibrary());
      libraryEntry.setScope(DependencyScope.PROVIDED);
    }
    model.commit();
    return facet;
  }

  private void createResourcesAndLibs(final Project project, final VirtualFile rootDir) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          createChildDirectoryIfNotExist(project, rootDir, SdkConstants.FD_ASSETS);
          createChildDirectoryIfNotExist(project, rootDir, SdkConstants.FD_NATIVE_LIBS);
          VirtualFile resDir = createChildDirectoryIfNotExist(project, rootDir, SdkConstants.FD_RES);
          VirtualFile drawableDir = createChildDirectoryIfNotExist(project, resDir, SdkConstants.FD_DRAWABLE);
          createFileFromResource(project, drawableDir, "icon.png", "/icons/androidLarge.png");
          if (isHelloAndroid()) {
            VirtualFile valuesDir = createChildDirectoryIfNotExist(project, resDir, SdkConstants.FD_VALUES);
            createFileFromResource(project, valuesDir, "strings.xml", "res/values/strings.xml");
            VirtualFile layoutDir = AndroidUtils.createChildDirectoryIfNotExist(project, resDir, SdkConstants.FD_LAYOUT);
            createFileFromResource(project, layoutDir, "main.xml", "res/layout/main.xml");
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    });
  }

  private static void createFileFromResource(Project project, VirtualFile drawableDir, String name, String resourceFilePath)
    throws IOException {
    if (drawableDir.findChild(name) != null) {
      return;
    }
    VirtualFile resFile = drawableDir.createChildData(project, name);
    InputStream stream = AndroidModuleBuilder.class.getResourceAsStream(resourceFilePath);
    try {
      byte[] bytes = FileUtil.adaptiveLoadBytes(stream);
      resFile.setBinaryContent(bytes);
    }
    finally {
      stream.close();
    }
  }

  public void setProjectType(ProjectType projectType) {
    myProjectType = projectType;
  }

  public void setActivityName(String activityName) {
    myActivityName = activityName;
  }

  public void setApplicationName(String applicationName) {
    myApplicationName = applicationName;
  }

  public void setPackageName(String packageName) {
    myPackageName = packageName;
  }

  public void setPlatform(@NotNull AndroidPlatform platform) {
    myPlatform = platform;
  }

  public ModuleType getModuleType() {
    return AndroidModuleType.getInstance();
  }

  public void setTestedModule(Module module) {
    myTestedModule = module;
  }
}
