/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.android.maven;

import com.android.sdklib.IAndroidTarget;
import com.intellij.facet.FacetType;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.io.ZipUtil;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidFacetType;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.*;
import org.jetbrains.android.util.AndroidNativeLibData;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.FacetImporter;
import org.jetbrains.idea.maven.importing.MavenModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenModuleImporter;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;
import java.io.IOException;
import java.util.*;


/**
 * @author Eugene.Kudelevsky
 */
public abstract class AndroidFacetImporterBase extends FacetImporter<AndroidFacet, AndroidFacetConfiguration, AndroidFacetType> {
  public static volatile String ANDROID_SDK_PATH_TEST = null;

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.maven.AndroidFacetImporterBase");

  private static final Key<Boolean> MODULE_IMPORTED = Key.create("ANDROID_NEWLY_CREATED_KEY");
  @NonNls private static final String DEFAULT_NATIVE_ARCHITECTURE = "armeabi";

  private static final Key<Boolean> DELETE_OBSOLETE_MODULE_TASK_KEY = Key.create("DELETE_OBSOLETE_MODULE_TASK");

  public AndroidFacetImporterBase(@NotNull String pluginId) {
    super("com.jayway.maven.plugins.android.generation2", pluginId, FacetType.findInstance(AndroidFacetType.class));
  }

  @Override
  public boolean isApplicable(MavenProject mavenProject) {
    return ArrayUtil.find(getSupportedPackagingTypes(), mavenProject.getPackaging()) >= 0 &&
           super.isApplicable(mavenProject);
  }


  @Override
  public void getSupportedPackagings(Collection<String> result) {
    result.addAll(Arrays.asList(getSupportedPackagingTypes()));
  }

  @NotNull
  private static String[] getSupportedPackagingTypes() {
    return new String[]{AndroidMavenUtil.APK_PACKAGING_TYPE, AndroidMavenUtil.APKLIB_DEPENDENCY_AND_PACKAGING_TYPE};
  }

  @Override
  public void getSupportedDependencyTypes(Collection<String> result, SupportedRequestType type) {
    result.add(AndroidMavenUtil.APKSOURCES_DEPENDENCY_TYPE);
    result.add(AndroidMavenUtil.APKLIB_DEPENDENCY_AND_PACKAGING_TYPE);
  }

  @Override
  protected void setupFacet(AndroidFacet facet, MavenProject mavenProject) {
    String mavenProjectDirPath = FileUtil.toSystemIndependentName(mavenProject.getDirectory());
    facet.getConfiguration().init(facet.getModule(), mavenProjectDirPath);
    AndroidMavenProviderImpl.setPathsToDefault(mavenProject, facet.getModule(), facet.getConfiguration());

    final boolean hasApkSources = AndroidMavenProviderImpl.hasApkSourcesDependency(mavenProject);
    AndroidMavenProviderImpl.configureAaptCompilation(mavenProject, facet.getModule(), facet.getConfiguration(), hasApkSources);

    if (AndroidMavenUtil.APKLIB_DEPENDENCY_AND_PACKAGING_TYPE.equals(mavenProject.getPackaging())) {
      facet.getProperties().LIBRARY_PROJECT = true;
    }
    facet.getConfiguration().setIncludeAssetsFromLibraries(true);

    if (hasApkSources) {
      AndroidUtils.reportImportErrorToEventLog("'apksources' dependency is deprecated and can be poorly supported by IDE. " +
                                               "It is strongly recommended to use 'apklib' dependency instead.",
                                               facet.getModule().getName());
    }
  }

  @Override
  protected void reimportFacet(MavenModifiableModelsProvider modelsProvider,
                               Module module,
                               MavenRootModelAdapter rootModel,
                               AndroidFacet facet,
                               MavenProjectsTree mavenTree,
                               MavenProject mavenProject,
                               MavenProjectChanges changes,
                               Map<MavenProject, String> mavenProjectToModuleName,
                               List<MavenProjectsProcessorTask> postTasks) {
    configurePaths(facet, mavenProject);
    configureAndroidPlatform(facet, mavenProject, modelsProvider);
    final Project project = module.getProject();
    importExternalApklibDependencies(project, rootModel, modelsProvider, mavenTree, mavenProject, mavenProjectToModuleName,
                                     postTasks);

    if (hasApklibDependencies(mavenProject) &&
        MavenProjectsManager.getInstance(project).getImportingSettings().isUseMavenOutput()) {
      // IDEA's apklibs building model is different from Maven's one, so we cannot use the same
      rootModel.useModuleOutput(mavenProject.getBuildDirectory() + "/idea-classes",
                                mavenProject.getBuildDirectory() + "/idea-test-classes");
    }
    project.putUserData(DELETE_OBSOLETE_MODULE_TASK_KEY, Boolean.TRUE);
    postTasks.add(new MyDeleteObsoleteApklibModulesTask(project));
  }

  private void importNativeDependencies(@NotNull AndroidFacet facet, @NotNull MavenProject mavenProject, @NotNull String moduleDirPath) {
    final List<AndroidNativeLibData> additionalNativeLibs = new ArrayList<AndroidNativeLibData>();
    final String localRepository = MavenProjectsManager.getInstance(facet.getModule().getProject()).getLocalRepository().getPath();

    String defaultArchitecture = getPathFromConfig(facet.getModule(), mavenProject, moduleDirPath,
                                                   "nativeLibrariesDependenciesHardwareArchitectureDefault", false, true);
    if (defaultArchitecture == null) {
      defaultArchitecture = DEFAULT_NATIVE_ARCHITECTURE;
    }
    final String forcedArchitecture = getPathFromConfig(facet.getModule(), mavenProject, moduleDirPath,
                                                        "nativeLibrariesDependenciesHardwareArchitectureOverride", false, true);

    for (MavenArtifact depArtifact : mavenProject.getDependencies()) {
      if (AndroidMavenUtil.SO_PACKAGING_AND_DEPENDENCY_TYPE.equals(depArtifact.getType())) {
        final String architecture;
        if (forcedArchitecture != null) {
          architecture = forcedArchitecture;
        }
        else {
          final String classifier = depArtifact.getClassifier();
          architecture = classifier != null ? classifier : defaultArchitecture;
        }
        final String path = FileUtil.toSystemIndependentName(localRepository + '/' + depArtifact.getRelativePath());
        final String artifactId = depArtifact.getArtifactId();
        final String targetFileName = artifactId.startsWith("lib") ? artifactId + ".so" : "lib" + artifactId + ".so";
        additionalNativeLibs.add(new AndroidNativeLibData(architecture, path, targetFileName));
      }
    }
    facet.getConfiguration().setAdditionalNativeLibraries(additionalNativeLibs);
  }

  private static boolean hasApklibDependencies(@NotNull MavenProject mavenProject) {
    for (MavenArtifact depArtifact : mavenProject.getDependencies()) {
      if (AndroidMavenUtil.APKLIB_DEPENDENCY_AND_PACKAGING_TYPE.equals(depArtifact.getType())) {
        return true;
      }
    }
    return false;
  }

  private static void importExternalApklibDependencies(Project project,
                                                       MavenRootModelAdapter rootModelAdapter,
                                                       MavenModifiableModelsProvider modelsProvider,
                                                       MavenProjectsTree mavenTree,
                                                       MavenProject mavenProject,
                                                       Map<MavenProject, String> mavenProject2ModuleName,
                                                       List<MavenProjectsProcessorTask> tasks) {
    final ModifiableRootModel rootModel = rootModelAdapter.getRootModel();
    removeExtApklibDependencies(rootModel, modelsProvider);

    for (MavenArtifact depArtifact : mavenProject.getDependencies()) {
      if (AndroidMavenUtil.APKLIB_DEPENDENCY_AND_PACKAGING_TYPE.equals(depArtifact.getType()) &&
          mavenTree.findProject(depArtifact) == null) {

        final ModifiableModuleModel moduleModel = modelsProvider.getModuleModel();
        final String apklibModuleName = AndroidMavenUtil.getModuleNameForExtApklibArtifact(depArtifact.getMavenId());
        Module apklibModule = moduleModel.findModuleByName(apklibModuleName);

        if ((apklibModule == null || apklibModule.getUserData(MODULE_IMPORTED) == null) &&
            MavenConstants.SCOPE_COMPILE.equals(depArtifact.getScope())) {
          apklibModule =
            importExternalApklibArtifact(project, apklibModule, modelsProvider, mavenProject, mavenTree, depArtifact,
                                         moduleModel, mavenProject2ModuleName);
          if (apklibModule != null) {
            apklibModule.putUserData(MODULE_IMPORTED, Boolean.TRUE);
            final Module finalGenModule = apklibModule;

            tasks.add(new MavenProjectsProcessorTask() {
              @Override
              public void perform(Project project,
                                  MavenEmbeddersManager embeddersManager,
                                  MavenConsole console,
                                  MavenProgressIndicator indicator)
                throws MavenProcessCanceledException {
                finalGenModule.putUserData(MODULE_IMPORTED, null);
              }
            });
          }
        }

        if (ArrayUtil.find(rootModel.getDependencyModuleNames(), apklibModuleName) < 0) {
          final DependencyScope scope = getApklibModuleDependencyScope(depArtifact);

          if (scope != null) {
            addModuleDependency(modelsProvider, rootModel, apklibModuleName, scope);
          }
        }
      }
    }
  }

  @Nullable
  private static DependencyScope getApklibModuleDependencyScope(@NotNull MavenArtifact apklibArtifact) {
    final String scope = apklibArtifact.getScope();

    if (MavenConstants.SCOPE_COMPILE.equals(scope)) {
      return DependencyScope.COMPILE;
    }
    else if (MavenConstants.SCOPE_PROVIDEED.equals(scope)) {
      return DependencyScope.PROVIDED;
    }
    else if (MavenConstants.SCOPE_TEST.equals(scope)) {
      return DependencyScope.TEST;
    }
    return null;
  }

  private static void removeExtApklibDependencies(ModifiableRootModel modifiableRootModel,
                                                  MavenModifiableModelsProvider modelsProvider) {
    for (OrderEntry entry : modifiableRootModel.getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        final Module depModule = ((ModuleOrderEntry)entry).getModule();
        if (depModule != null && AndroidMavenUtil.isExtApklibModule(depModule)) {
          modifiableRootModel.removeOrderEntry(entry);
        }
      }
      else if (entry instanceof LibraryOrderEntry &&
               containsDependencyOnApklibFile((LibraryOrderEntry)entry, modelsProvider)) {
        modifiableRootModel.removeOrderEntry(entry);
      }
    }
  }

  private static boolean containsDependencyOnApklibFile(@NotNull LibraryOrderEntry libraryOrderEntry,
                                                        @NotNull MavenModifiableModelsProvider modelsProvider) {
    final Library library = libraryOrderEntry.getLibrary();
    final Library.ModifiableModel libraryModel = modelsProvider.getLibraryModel(library);
    final String[] urls = libraryModel.getUrls(OrderRootType.CLASSES);

    for (String url : urls) {
      final String fileName = PathUtil.getFileName(PathUtil.toPresentableUrl(url));

      if (FileUtilRt.extensionEquals(fileName, "apklib")) {
        return true;
      }
    }
    return false;
  }

  private static void addModuleDependency(@NotNull MavenModifiableModelsProvider modelsProvider,
                                          @NotNull ModifiableRootModel rootModel,
                                          @NotNull final String moduleName,
                                          @NotNull DependencyScope compile) {
    if (findModuleDependency(rootModel, moduleName) != null) {
      return;
    }

    final Module module = modelsProvider.getModuleModel().findModuleByName(moduleName);

    final ModuleOrderEntry entry = module != null
                                   ? rootModel.addModuleOrderEntry(module)
                                   : rootModel.addInvalidModuleEntry(moduleName);
    entry.setScope(compile);
  }

  private static ModuleOrderEntry findModuleDependency(ModifiableRootModel rootModel, final String moduleName) {
    final Ref<ModuleOrderEntry> result = Ref.create(null);

    rootModel.orderEntries().forEach(new Processor<OrderEntry>() {
      @Override
      public boolean process(OrderEntry entry) {
        if (entry instanceof ModuleOrderEntry) {
          final ModuleOrderEntry moduleEntry = (ModuleOrderEntry)entry;
          final String name = moduleEntry.getModuleName();
          if (moduleName.equals(name)) {
            result.set(moduleEntry);
          }
        }
        return true;
      }
    });

    return result.get();
  }

  @Nullable
  private static Module importExternalApklibArtifact(Project project,
                                                     Module apklibModule,
                                                     MavenModifiableModelsProvider modelsProvider,
                                                     MavenProject mavenProject,
                                                     MavenProjectsTree mavenTree,
                                                     MavenArtifact artifact,
                                                     ModifiableModuleModel moduleModel,
                                                     Map<MavenProject, String> mavenProject2ModuleName) {
    final MavenId artifactMavenId = artifact.getMavenId();

    final String genModuleName = AndroidMavenUtil.getModuleNameForExtApklibArtifact(artifactMavenId);
    String genExternalApklibsDirPath = null;
    String targetDirPath = null;

    if (apklibModule == null) {
      genExternalApklibsDirPath =
        AndroidMavenUtil.computePathForGenExternalApklibsDir(artifactMavenId, mavenProject, mavenTree.getProjects());

      targetDirPath = genExternalApklibsDirPath != null
                      ? genExternalApklibsDirPath + '/' + AndroidMavenUtil.getMavenIdStringForFileName(artifactMavenId)
                      : null;
    }
    else {
      final VirtualFile[] contentRoots = ModuleRootManager.getInstance(apklibModule).getContentRoots();
      if (contentRoots.length == 1) {
        targetDirPath = contentRoots[0].getPath();
      }
      else {
        final String moduleDir = new File(apklibModule.getModuleFilePath()).getParent();
        if (moduleDir != null) {
          targetDirPath = moduleDir + '/' + AndroidMavenUtil.getMavenIdStringForFileName(artifactMavenId);
        }
      }
    }

    if (targetDirPath == null) {
      return null;
    }

    final File targetDir = new File(targetDirPath);
    if (targetDir.exists()) {
      if (!FileUtil.delete(targetDir)) {
        LOG.error("Cannot delete old " + targetDirPath);
        return null;
      }
    }

    if (!targetDir.mkdirs()) {
      LOG.error("Cannot create directory " + targetDirPath);
      return null;
    }

    final File artifactFile = artifact.getFile();

    final AndroidExternalApklibDependenciesManager adm = AndroidExternalApklibDependenciesManager.getInstance(project);

    adm.setArtifactFilePath(artifactMavenId, FileUtil.toSystemIndependentName(artifactFile.getPath()));

    if (artifactFile.exists()) {
      try {
        ZipUtil.extract(artifactFile, targetDir, null);
      }
      catch (IOException e) {
        final String message = e.getMessage();

        if (message == null) {
          LOG.error(e);
        }
        else {
          AndroidUtils.reportImportErrorToEventLog("I/O error: " + message, genModuleName);
        }
        return null;
      }
    }
    else {
      AndroidUtils.reportImportErrorToEventLog("Cannot find file " + artifactFile.getPath(), genModuleName);
    }

    final VirtualFile vApklibDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(targetDirPath);
    if (vApklibDir == null) {
      LOG.error("Cannot find file " + targetDirPath + " in VFS");
      return null;
    }

    if (apklibModule == null) {
      final String genModuleFilePath = genExternalApklibsDirPath + '/' + genModuleName + ModuleFileType.DOT_DEFAULT_EXTENSION;
      apklibModule = moduleModel.newModule(genModuleFilePath, StdModuleTypes.JAVA.getId());
    }

    final ModifiableRootModel apklibModuleModel = modelsProvider.getRootModel(apklibModule);
    final ContentEntry contentEntry = apklibModuleModel.addContentEntry(vApklibDir);

    final VirtualFile sourceRoot = vApklibDir.findChild(AndroidMavenUtil.APK_LIB_ARTIFACT_SOURCE_ROOT);
    if (sourceRoot != null) {
      contentEntry.addSourceFolder(sourceRoot, false);
    }
    else {
      AndroidUtils.reportImportErrorToEventLog(
        "Cannot find " + AndroidMavenUtil.APK_LIB_ARTIFACT_SOURCE_ROOT + " directory in " + vApklibDir.getPath(),
        genModuleName);
    }

    final AndroidFacet facet = AndroidUtils.addAndroidFacet(apklibModuleModel.getModule(), vApklibDir, true);

    final AndroidFacetConfiguration configuration = facet.getConfiguration();
    String s = AndroidRootUtil.getPathRelativeToModuleDir(apklibModule, vApklibDir.getPath());
    if (s != null) {
      s = s.length() > 0 ? '/' + s + '/' : "/";
      configuration.getState().RES_FOLDER_RELATIVE_PATH = s + AndroidMavenUtil.APK_LIB_ARTIFACT_RES_DIR;
      configuration.getState().LIBS_FOLDER_RELATIVE_PATH = s + AndroidMavenUtil.APK_LIB_ARTIFACT_NATIVE_LIBS_DIR;
      configuration.getState().MANIFEST_FILE_RELATIVE_PATH = s + AndroidMavenUtil.APK_LIB_ARTIFACT_MANIFEST_FILE;
    }

    importSdkAndDependenciesForApklibArtifact(project, apklibModuleModel, modelsProvider, mavenTree,
                                              artifactMavenId, mavenProject2ModuleName);
    return apklibModule;
  }

  private static void importSdkAndDependenciesForApklibArtifact(Project project,
                                                                ModifiableRootModel apklibModuleModel,
                                                                MavenModifiableModelsProvider modelsProvider,
                                                                MavenProjectsTree mavenTree,
                                                                MavenId artifactMavenId,
                                                                Map<MavenProject, String> mavenProject2ModuleName) {
    final String apklibModuleName = apklibModuleModel.getModule().getName();
    final AndroidExternalApklibDependenciesManager adm = AndroidExternalApklibDependenciesManager.getInstance(project);
    final MavenArtifactResolvedInfo resolvedInfo =
      adm.getResolvedInfoForArtifact(artifactMavenId);

    for (OrderEntry entry : apklibModuleModel.getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry || entry instanceof LibraryOrderEntry) {
        apklibModuleModel.removeOrderEntry(entry);
      }
    }

    if (resolvedInfo != null) {
      final String apiLevel = resolvedInfo.getApiLevel();
      final Sdk sdk = findOrCreateAndroidPlatform(apiLevel, null);

      if (sdk != null) {
        apklibModuleModel.setSdk(sdk);
      }
      else {
        reportCannotFindAndroidPlatformError(apklibModuleName, apiLevel);
      }

      for (AndroidExternalApklibDependenciesManager.MavenDependencyInfo depArtifactInfo : resolvedInfo.getDependencies()) {
        final MavenId depMavenId = new MavenId(depArtifactInfo.getGroupId(), depArtifactInfo.getArtifactId(),
                                               depArtifactInfo.getVersion());

        final String type = depArtifactInfo.getType();
        final String scope = depArtifactInfo.getScope();
        final String path = depArtifactInfo.getPath();
        final String libName = depArtifactInfo.getLibName();

        if (AndroidMavenUtil.APKLIB_DEPENDENCY_AND_PACKAGING_TYPE.equals(type) &&
            MavenConstants.SCOPE_COMPILE.equals(scope)) {
          final MavenProject depProject = mavenTree.findProject(depMavenId);

          if (depProject != null) {
            final String depModuleName = mavenProject2ModuleName.get(depProject);

            if (depModuleName != null) {
              addModuleDependency(modelsProvider, apklibModuleModel, depModuleName, DependencyScope.COMPILE);
            }
          }
          else {
            final String depApklibGenModuleName = AndroidMavenUtil.getModuleNameForExtApklibArtifact(depMavenId);
            addModuleDependency(modelsProvider, apklibModuleModel, depApklibGenModuleName, DependencyScope.COMPILE);
          }
        }
        else {
          final DependencyScope depScope = MavenModuleImporter.selectScope(scope);

          if (scope != null) {
            addLibraryDependency(libName, depScope, modelsProvider, apklibModuleModel, path);
          }
          else {
            LOG.info("Unknown Maven scope " + depScope);
          }
        }
      }
    }
    else {
      AndroidUtils.reportImportErrorToEventLog("Cannot find sdk info for artifact " + artifactMavenId.getKey(), apklibModuleName);
    }
  }

  private static void addLibraryDependency(@NotNull String libraryName,
                                           @NotNull DependencyScope scope,
                                           @NotNull MavenModifiableModelsProvider provider,
                                           @NotNull ModifiableRootModel model,
                                           @NotNull String path) {
    Library library = provider.getLibraryByName(libraryName);

    if (library == null) {
      library = provider.createLibrary(libraryName);
    }
    Library.ModifiableModel libraryModel = provider.getLibraryModel(library);
    updateUrl(libraryModel, path);
    final LibraryOrderEntry entry = model.addLibraryEntry(library);
    entry.setScope(scope);
  }

  private static void updateUrl(@NotNull Library.ModifiableModel library, @NotNull String path) {
    final OrderRootType type = OrderRootType.CLASSES;
    final String newUrl = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, path) + JarFileSystem.JAR_SEPARATOR;
    boolean urlExists = false;

    for (String url : library.getUrls(type)) {
      if (newUrl.equals(url)) {
        urlExists = true;
      }
      else {
        library.removeRoot(url, type);
      }
    }

    if (!urlExists) {
      library.addRoot(newUrl, type);
    }
  }

  private static void reportCannotFindAndroidPlatformError(String moduleName, @Nullable String apiLevel) {
    AndroidUtils
      .reportImportErrorToEventLog("Cannot find appropriate Android platform" + (apiLevel != null ? " for API level " + apiLevel : ""),
                                   moduleName);
  }

  @Override
  public void resolve(final Project project,
                      MavenProject mavenProject,
                      NativeMavenProjectHolder nativeMavenProject,
                      MavenEmbedderWrapper embedder)
    throws MavenProcessCanceledException {
    final AndroidExternalApklibDependenciesManager adm =
      AndroidExternalApklibDependenciesManager.getInstance(project);

    for (MavenArtifact depArtifact : mavenProject.getDependencies()) {
      final MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(project);

      if (AndroidMavenUtil.APKLIB_DEPENDENCY_AND_PACKAGING_TYPE.equals(depArtifact.getType()) &&
          mavenProjectsManager.findProject(depArtifact) == null &&
          MavenConstants.SCOPE_COMPILE.equals(depArtifact.getScope())) {

        if (adm.getResolvedInfoForArtifact(depArtifact.getMavenId()) == null) {
          doResolveApklibArtifact(project, depArtifact, embedder, mavenProjectsManager, mavenProject.getName(), adm);
        }
      }
    }
  }

  private void doResolveApklibArtifact(Project project,
                                       MavenArtifact artifact,
                                       MavenEmbedderWrapper embedder,
                                       MavenProjectsManager mavenProjectsManager,
                                       String moduleName,
                                       AndroidExternalApklibDependenciesManager adm) throws MavenProcessCanceledException {
    final File depArtifacetFile = new File(FileUtil.getNameWithoutExtension(artifact.getPath()) + ".pom");
    if (!depArtifacetFile.exists()) {
      AndroidUtils.reportImportErrorToEventLog("Cannot find file " + depArtifacetFile.getPath(), moduleName);
      return;
    }

    final VirtualFile vDepArtifactFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(depArtifacetFile);
    if (vDepArtifactFile == null) {
      AndroidUtils.reportImportErrorToEventLog("Cannot find file " + depArtifacetFile.getPath() + " in VFS", moduleName);
      return;
    }

    final MavenProject projectForExternalApklib = new MavenProject(vDepArtifactFile);
    final MavenGeneralSettings generalSettings = mavenProjectsManager.getGeneralSettings();
    final MavenProjectReader mavenProjectReader = new MavenProjectReader();

    final MavenProjectReaderProjectLocator locator = new MavenProjectReaderProjectLocator() {
      @Nullable
      @Override
      public VirtualFile findProjectFile(MavenId coordinates) {
        return null;
      }
    };
    final MavenArtifactResolvedInfo info = new MavenArtifactResolvedInfo();
    adm.setResolvedInfoForArtifact(artifact.getMavenId(), info);

    projectForExternalApklib.read(generalSettings, mavenProjectsManager.getAvailableProfiles(), mavenProjectReader, locator);
    projectForExternalApklib.resolve(project, generalSettings, embedder, mavenProjectReader, locator);

    final String apiLevel = getPlatformFromConfig(projectForExternalApklib);

    final List<AndroidExternalApklibDependenciesManager.MavenDependencyInfo> dependencies =
      new ArrayList<AndroidExternalApklibDependenciesManager.MavenDependencyInfo>();

    for (MavenArtifact depArtifact : projectForExternalApklib.getDependencies()) {
      dependencies.add(AndroidExternalApklibDependenciesManager.MavenDependencyInfo.create(depArtifact));
    }
    info.setApiLevel(apiLevel != null ? apiLevel : "");
    info.setDependencies(dependencies);
  }

  private void configureAndroidPlatform(AndroidFacet facet, MavenProject project, MavenModifiableModelsProvider modelsProvider) {
    final ModifiableRootModel model = modelsProvider.getRootModel(facet.getModule());
    configureAndroidPlatform(project, model);
  }

  private void configureAndroidPlatform(MavenProject project, ModifiableRootModel model) {
    final Sdk currentSdk = model.getSdk();

    if (currentSdk == null || !isAppropriateSdk(currentSdk, project)) {
      final String apiLevel = getPlatformFromConfig(project);
      final String predefinedSdkPath = getSdkPathFromConfig(project);
      final Sdk platformLib = findOrCreateAndroidPlatform(apiLevel, predefinedSdkPath);

      if (platformLib != null) {
        model.setSdk(platformLib);
      }
      else {
        reportCannotFindAndroidPlatformError(model.getModule().getName(), apiLevel);
      }
    }
  }

  private boolean isAppropriateSdk(@NotNull Sdk sdk, MavenProject mavenProject) {
    if (!(sdk.getSdkType() == AndroidSdkType.getInstance())) {
      return false;
    }
    final String platformId = getPlatformFromConfig(mavenProject);

    final AndroidSdkAdditionalData sdkAdditionalData = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
    if (sdkAdditionalData == null) {
      return false;
    }

    final AndroidPlatform androidPlatform = sdkAdditionalData.getAndroidPlatform();
    if (androidPlatform == null) {
      return false;
    }

    final IAndroidTarget target = androidPlatform.getTarget();
    return (platformId == null || AndroidSdkUtils.targetHasId(target, platformId)) &&
           AndroidSdkUtils.checkSdkRoots(sdk, target, true);
  }

  @Nullable
  private static Sdk findOrCreateAndroidPlatform(String apiLevel, String predefinedSdkPath) {
    if (predefinedSdkPath != null) {
      final Sdk sdk = doFindOrCreateAndroidPlatform(predefinedSdkPath, apiLevel);
      if (sdk != null) {
        return sdk;
      }
    }
    String sdkPath;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      sdkPath = ANDROID_SDK_PATH_TEST;
    }
    else {
      sdkPath = System.getenv(AndroidSdkUtils.ANDROID_HOME_ENV);
    }
    LOG.info("android home: " + sdkPath);

    if (sdkPath != null) {
      final Sdk sdk = doFindOrCreateAndroidPlatform(sdkPath, apiLevel);
      if (sdk != null) {
        return sdk;
      }
    }

    final Collection<String> candidates = AndroidSdkUtils.getAndroidSdkPathsFromExistingPlatforms();
    LOG.info("suggested sdks: " + candidates);

    for (String candidate : candidates) {
      final Sdk sdk = doFindOrCreateAndroidPlatform(candidate, apiLevel);
      if (sdk != null) {
        return sdk;
      }
    }
    return null;
  }

  @Nullable
  private static Sdk doFindOrCreateAndroidPlatform(@Nullable String sdkPath, @Nullable String apiLevel) {
    if (sdkPath != null) {
      AndroidSdkData sdkData = AndroidSdkData.parse(sdkPath, new EmptySdkLog());
      if (sdkData != null) {
        IAndroidTarget target = apiLevel != null
                                ? sdkData.findTargetByApiLevel(apiLevel)
                                : findNewestPlatformTarget(sdkData);
        if (target != null) {
          Sdk library = AndroidSdkUtils.findAppropriateAndroidPlatform(target, sdkData, true);
          if (library == null) {
            library = createNewAndroidSdkForMaven(sdkPath, target);
          }
          return library;
        }
      }
    }
    return null;
  }

  @Nullable
  private static IAndroidTarget findNewestPlatformTarget(AndroidSdkData data) {
    IAndroidTarget result = null;

    for (IAndroidTarget target : data.getTargets()) {
      if (target.isPlatform() && (result == null || result.getVersion().compareTo(target.getVersion()) < 0)) {
        result = target;
      }
    }
    return result;
  }

  @Nullable
  private static Sdk createNewAndroidSdkForMaven(String sdkPath, IAndroidTarget target) {
    final String sdkName = "Maven " + AndroidSdkUtils.chooseNameForNewLibrary(target);
    final Sdk sdk = AndroidSdkUtils.createNewAndroidPlatform(target, sdkPath, false, sdkName);

    if (sdk == null) {
      return null;
    }
    final SdkModificator modificator = sdk.getSdkModificator();

    for (OrderRoot root : AndroidSdkUtils.getLibraryRootsForTarget(target, sdkPath, false)) {
      modificator.addRoot(root.getFile(), root.getType());
    }
    final AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();

    if (data != null) {
      final Sdk javaSdk = data.getJavaSdk();

      if (javaSdk != null) {
        for (VirtualFile file : javaSdk.getRootProvider().getFiles(OrderRootType.CLASSES)) {
          modificator.addRoot(file, OrderRootType.CLASSES);
        }
      }
      else {
        LOG.error("AndroidSdkUtils.createNewAndroidPlatform should return " +
                  "Android SDK with a valid JDK reference, or return null");
      }
    }
    modificator.commitChanges();
    return sdk;
  }

  @Nullable
  private String getPlatformFromConfig(MavenProject project) {
    Element sdkRoot = getConfig(project, "sdk");
    if (sdkRoot != null) {
      Element platform = sdkRoot.getChild("platform");
      if (platform != null) {
        return platform.getValue();
      }
    }
    final String platformFromProperty =
      project.getProperties().getProperty("android.sdk.platform");

    if (platformFromProperty != null) {
      return platformFromProperty;
    }
    return null;
  }

  @Nullable
  private String getSdkPathFromConfig(MavenProject project) {
    Element sdkRoot = getConfig(project, "sdk");
    if (sdkRoot != null) {
      Element path = sdkRoot.getChild("path");
      if (path != null) {
        return path.getValue();
      }
    }
    final String pathFromProperty =
      project.getProperties().getProperty("android.sdk.path");

    if (pathFromProperty != null) {
      return pathFromProperty;
    }
    return null;
  }

  private void configurePaths(AndroidFacet facet, MavenProject project) {
    Module module = facet.getModule();
    String moduleDirPath = AndroidRootUtil.getModuleDirPath(module);
    if (moduleDirPath == null) {
      return;
    }
    AndroidFacetConfiguration configuration = facet.getConfiguration();

    String resFolderRelPath = getPathFromConfig(module, project, moduleDirPath, "resourceDirectory", true, true);
    if (resFolderRelPath != null && isFullyResolved(resFolderRelPath)) {
      configuration.getState().RES_FOLDER_RELATIVE_PATH = '/' + resFolderRelPath;
    }

    Element resourceOverlayDirectories = getConfig(project, "resourceOverlayDirectories");
    if (resourceOverlayDirectories != null) {
      List<String> dirs = new ArrayList<String>();
      for (Object child : resourceOverlayDirectories.getChildren()) {
        String dir = ((Element)child).getTextTrim();
        if (dir != null && dir.length() > 0) {
          String relativePath = getRelativePath(moduleDirPath, makePath(project, dir));
          if (relativePath != null && relativePath.length() > 0) {
            dirs.add('/' + relativePath);
          }
        }
      }
      if (dirs.size() > 0) {
        configuration.getState().RES_OVERLAY_FOLDERS = dirs;
      }
    }
    else {
      String resOverlayFolderRelPath = getPathFromConfig(module, project, moduleDirPath, "resourceOverlayDirectory", true, true);
      if (resOverlayFolderRelPath != null && isFullyResolved(resOverlayFolderRelPath)) {
        configuration.getState().RES_OVERLAY_FOLDERS = Arrays.asList('/' + resOverlayFolderRelPath);
      }
    }

    String resFolderForCompilerRelPath = getPathFromConfig(module, project, moduleDirPath, "resourceDirectory", false, true);
    if (resFolderForCompilerRelPath != null &&
        !resFolderForCompilerRelPath.equals(resFolderRelPath)) {
      if (!configuration.getState().USE_CUSTOM_APK_RESOURCE_FOLDER) {
        // it may be already configured in setupFacet()
        configuration.getState().USE_CUSTOM_APK_RESOURCE_FOLDER = true;
        configuration.getState().CUSTOM_APK_RESOURCE_FOLDER = '/' + resFolderForCompilerRelPath;
      }
      configuration.getState().RUN_PROCESS_RESOURCES_MAVEN_TASK = true;
    }

    String assetsFolderRelPath = getPathFromConfig(module, project, moduleDirPath, "assetsDirectory", false, true);
    if (assetsFolderRelPath != null && isFullyResolved(assetsFolderRelPath)) {
      configuration.getState().ASSETS_FOLDER_RELATIVE_PATH = '/' + assetsFolderRelPath;
    }

    String manifestFileRelPath =  getPathFromConfig(module, project, moduleDirPath, "androidManifestFile", true, false);
    if (manifestFileRelPath != null && isFullyResolved(manifestFileRelPath)) {
      configuration.getState().MANIFEST_FILE_RELATIVE_PATH = '/' + manifestFileRelPath;
    }

    String manifestFileForCompilerRelPath = getPathFromConfig(module, project, moduleDirPath, "androidManifestFile", false, false);
    if (manifestFileForCompilerRelPath != null &&
        !manifestFileForCompilerRelPath.equals(manifestFileRelPath) &&
        isFullyResolved(manifestFileForCompilerRelPath)) {
      configuration.getState().USE_CUSTOM_COMPILER_MANIFEST = true;
      configuration.getState().CUSTOM_COMPILER_MANIFEST = '/' + manifestFileForCompilerRelPath;
      configuration.getState().RUN_PROCESS_RESOURCES_MAVEN_TASK = true;
    }

    String nativeLibsFolderRelPath = getPathFromConfig(module, project, moduleDirPath, "nativeLibrariesDirectory", false, true);
    if (nativeLibsFolderRelPath != null && isFullyResolved(nativeLibsFolderRelPath)) {
      configuration.getState().LIBS_FOLDER_RELATIVE_PATH = '/' + nativeLibsFolderRelPath;
    }

    importNativeDependencies(facet, project, moduleDirPath);
  }

  private static boolean isFullyResolved(@NotNull String s) {
    return !s.contains("${");
  }

  @Nullable
  private String getPathFromConfig(Module module,
                                   MavenProject project,
                                   String moduleDirPath,
                                   String configTagName,
                                   boolean inResourceDir,
                                   boolean directory) {
    String resourceDir = findConfigValue(project, configTagName);
    if (resourceDir != null) {
      String path = makePath(project, resourceDir);
      if (inResourceDir) {
        MyResourceProcessor processor = new MyResourceProcessor(path, directory);
        AndroidMavenProviderImpl.processResources(module, project, processor);
        if (processor.myResult != null) {
          path = processor.myResult.getPath();
        }
      }
      String resFolderRelPath = getRelativePath(moduleDirPath, path);
      if (resFolderRelPath != null) {
        return resFolderRelPath;
      }
    }
    return null;
  }

  @Nullable
  private static String getRelativePath(String basePath, String absPath) {
    absPath = FileUtil.toSystemIndependentName(absPath);
    return FileUtil.getRelativePath(basePath, absPath, '/');
  }

  @Override
  public void collectExcludedFolders(MavenProject mavenProject, List<String> result) {
    result.add(mavenProject.getGeneratedSourcesDirectory(false) + "/combined-resources");
    result.add(mavenProject.getGeneratedSourcesDirectory(false) + "/combined-assets");
    result.add(mavenProject.getGeneratedSourcesDirectory(false) + "/extracted-dependencies");
  }

  private static class MyResourceProcessor implements AndroidMavenProviderImpl.ResourceProcessor {
    private final String myResourceOutputPath;
    private final boolean myDirectory;

    private VirtualFile myResult;

    private MyResourceProcessor(String resourceOutputPath, boolean directory) {
      myResourceOutputPath = resourceOutputPath;
      myDirectory = directory;
    }

    @Override
    public boolean process(@NotNull VirtualFile resource, @NotNull String outputPath) {
      if (!myDirectory && resource.isDirectory()) {
        return false;
      }
      if (outputPath.endsWith("/")) {
        outputPath = outputPath.substring(0, outputPath.length() - 1);
      }
      if (FileUtil.pathsEqual(outputPath, myResourceOutputPath)) {
        myResult = resource;
        return true;
      }

      if (myDirectory) {
        // we're looking for for resource directory

        if (outputPath.toLowerCase().startsWith(myResourceOutputPath.toLowerCase())) {
          final String parentPath = outputPath.substring(0, myResourceOutputPath.length());
          if (FileUtil.pathsEqual(parentPath, myResourceOutputPath)) {

            if (resource.isDirectory()) {
              // copying of directory that is located in resource dir, so resource dir is parent
              final VirtualFile parent = resource.getParent();
              if (parent != null) {
                myResult = parent;
                return true;
              }
            }
            else {
              // copying of resource file, we have to skip resource-type specific directory
              final VirtualFile parent = resource.getParent();
              final VirtualFile gp = parent != null ? parent.getParent() : null;
              if (gp != null) {
                myResult = gp;
                return true;
              }
            }
          }
        }
        return false;
      }

      return false;
    }
  }

  private static class MyDeleteObsoleteApklibModulesTask implements MavenProjectsProcessorTask {
    private final Project myProject;

    public MyDeleteObsoleteApklibModulesTask(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void perform(final Project project,
                        MavenEmbeddersManager embeddersManager,
                        MavenConsole console,
                        MavenProgressIndicator indicator)
      throws MavenProcessCanceledException {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          if (project.isDisposed() || project.getUserData(DELETE_OBSOLETE_MODULE_TASK_KEY) != Boolean.TRUE) {
            return;
          }
          project.putUserData(DELETE_OBSOLETE_MODULE_TASK_KEY, null);
          final ModifiableModuleModel moduleModel = ModuleManager.getInstance(project).getModifiableModel();
          final Set<Module> referredModules = new HashSet<Module>();

          for (Module module : moduleModel.getModules()) {
            if (!AndroidMavenUtil.isExtApklibModule(module)) {
              collectDependenciesRecursively(module, referredModules);
            }
          }

          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              boolean modelChanged = false;

              for (final Module module : moduleModel.getModules()) {
                if (AndroidMavenUtil.isExtApklibModule(module) && !referredModules.contains(module)) {
                  final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();

                  if (contentRoots.length > 0) {
                    final VirtualFile contentRoot = contentRoots[0];
                    try {
                      contentRoot.delete(myProject);
                    }
                    catch (IOException e) {
                      LOG.error(e);
                    }
                  }
                  moduleModel.disposeModule(module);
                  modelChanged = true;
                }
              }
              if (modelChanged) {
                moduleModel.commit();
              }
              else {
                moduleModel.dispose();
              }
            }
          });
        }
      });
    }

    private static void collectDependenciesRecursively(@NotNull Module root, @NotNull Set<Module> result) {
      if (!result.add(root)) {
        return;
      }

      for (Module depModule : ModuleRootManager.getInstance(root).getDependencies()) {
        collectDependenciesRecursively(depModule, result);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;

      MyDeleteObsoleteApklibModulesTask task = (MyDeleteObsoleteApklibModulesTask)o;

      if (!myProject.equals(task.myProject)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + myProject.hashCode();
      return result;
    }
  }
}
