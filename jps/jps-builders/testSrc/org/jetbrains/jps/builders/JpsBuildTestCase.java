package org.jetbrains.jps.builders;

import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.io.TestFileSystemBuilder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.util.JpsPathUtil;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.impl.BuildRootIndexImpl;
import org.jetbrains.jps.builders.impl.BuildTargetIndexImpl;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuilderLoggerImpl;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.java.JavaBuilderLoggerImpl;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.BuildTargetsState;
import org.jetbrains.jps.incremental.storage.ProjectTimestamps;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.indices.impl.IgnoredFileIndexImpl;
import org.jetbrains.jps.indices.impl.ModuleExcludeIndexImpl;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author nik
 */
public abstract class JpsBuildTestCase extends UsefulTestCase {
  private File myProjectDir;
  protected JpsProject myProject;
  protected JpsModel myModel;
  private JpsSdk<JpsDummyElement> myJdk;
  private File myDataStorageRoot;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myModel = JpsElementFactory.getInstance().createModel();
    myProject = myModel.getProject();
    myDataStorageRoot = FileUtil.createTempDirectory("compile-server-" + getProjectName(), null);
  }

  @Override
  protected void tearDown() throws Exception {
    myProjectDir = null;
    super.tearDown();
  }

  protected static void assertOutput(final String outputPath, TestFileSystemBuilder expected) {
    expected.build().assertDirectoryEqual(new File(FileUtil.toSystemDependentName(outputPath)));
  }

  protected static void assertOutput(JpsModule module, TestFileSystemBuilder expected) {
    String outputUrl = JpsJavaExtensionService.getInstance().getOutputUrl(module, false);
    assertNotNull(outputUrl);
    assertOutput(JpsPathUtil.urlToPath(outputUrl), expected);
  }

  protected static void change(String filePath) {
    change(filePath, null);
  }

  protected static void change(String filePath, final @Nullable String newContent) {
    try {
      File file = new File(FileUtil.toSystemDependentName(filePath));
      assertTrue("File " + file.getAbsolutePath() + " doesn't exist", file.exists());
      if (newContent != null) {
        FileUtil.writeToFile(file, newContent);
      }
      boolean updated = file.setLastModified(FileSystemUtil.lastModified(file) + Utils.TIMESTAMP_ACCURACY);
      assertTrue("Cannot modify timestamp for " + file.getAbsolutePath(), updated);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static void delete(String filePath) {
    File file = new File(FileUtil.toSystemDependentName(filePath));
    assertTrue("File " + file.getAbsolutePath() + " doesn't exist", file.exists());
    final boolean deleted = FileUtil.delete(file);
    assertTrue("Cannot delete file " + file.getAbsolutePath(), deleted);
  }

  protected JpsSdk<JpsDummyElement> addJdk(final String name) {
    try {
      return addJdk(name, FileUtil.toSystemIndependentName(ClasspathBootstrap.getResourcePath(Object.class).getCanonicalPath()));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected JpsSdk<JpsDummyElement> addJdk(final String name, final String path) {
    String homePath = System.getProperty("java.home");
    String versionString = System.getProperty("java.version");
    JpsTypedLibrary<JpsSdk<JpsDummyElement>> jdk = myModel.getGlobal().addSdk(name, homePath, versionString, JpsJavaSdkType.INSTANCE);
    jdk.addRoot(JpsPathUtil.pathToUrl(path), JpsOrderRootType.COMPILED);
    return jdk.getProperties();
  }

  protected String getProjectName() {
    return StringUtil.decapitalize(StringUtil.trimStart(getName(), "test"));
  }

  protected ProjectDescriptor createProjectDescriptor(final BuildLoggingManager buildLoggingManager) {
    try {
      BuildTargetIndexImpl targetIndex = new BuildTargetIndexImpl(myModel);
      ModuleExcludeIndex index = new ModuleExcludeIndexImpl(myModel);
      IgnoredFileIndexImpl ignoredFileIndex = new IgnoredFileIndexImpl(myModel);
      BuildRootIndexImpl buildRootIndex = new BuildRootIndexImpl(targetIndex, myModel, index, myDataStorageRoot, ignoredFileIndex);
      BuildTargetsState targetsState = new BuildTargetsState(myDataStorageRoot, myModel, buildRootIndex);
      ProjectTimestamps timestamps = new ProjectTimestamps(myDataStorageRoot, targetsState);
      BuildDataManager dataManager = new BuildDataManager(myDataStorageRoot, targetsState, true);
      return new ProjectDescriptor(myModel, new BuildFSState(true), timestamps, dataManager, buildLoggingManager, index, targetsState,
                                   targetIndex, buildRootIndex, ignoredFileIndex);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void loadProject(String projectPath) {
    loadProject(projectPath, Collections.<String, String>emptyMap());
  }

  protected void loadProject(String projectPath,
                             Map<String, String> pathVariables) {
    try {
      String testDataRootPath = getTestDataRootPath();
      String fullProjectPath = FileUtil.toSystemDependentName(testDataRootPath != null ? testDataRootPath + "/" + projectPath : projectPath);
      pathVariables = addPathVariables(pathVariables);
      JpsProjectLoader.loadProject(myProject, pathVariables, fullProjectPath);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected Map<String, String> addPathVariables(Map<String, String> pathVariables) {
    return pathVariables;
  }

  @Nullable
  protected String getTestDataRootPath() {
    return null;
  }

  protected JpsModule addModule(String moduleName,
                                String[] srcPaths,
                                @Nullable final String outputPath,
                                final JpsSdk<JpsDummyElement> jdk) {
    final JpsModule module = myProject.addModule(moduleName, JpsJavaModuleType.INSTANCE);
    module.getSdkReferencesTable().setSdkReference(JpsJavaSdkType.INSTANCE, jdk.createReference());
    module.getDependenciesList().addSdkDependency(JpsJavaSdkType.INSTANCE);
    if (srcPaths.length > 0) {
      for (String srcPath : srcPaths) {
        module.getContentRootsList().addUrl(JpsPathUtil.pathToUrl(srcPath));
        module.addSourceRoot(JpsPathUtil.pathToUrl(srcPath), JavaSourceRootType.SOURCE);
      }
      JpsJavaModuleExtension extension = JpsJavaExtensionService.getInstance().getOrCreateModuleExtension(module);
      if (outputPath != null) {
        extension.setOutputUrl(JpsPathUtil.pathToUrl(outputPath));
      }
      else {
        extension.setInheritOutput(true);
      }
    }
    return module;
  }

  protected void doRebuild() {
    ProjectDescriptor descriptor = createProjectDescriptor(new BuildLoggingManager(new ArtifactBuilderLoggerImpl(), new JavaBuilderLoggerImpl()));
    try {
      CompileScope scope = new CompileScopeImpl(true, BuilderRegistry.getInstance().getTargetTypes(), Collections.<BuildTarget<?>>emptySet(), Collections.<BuildTarget<?>,Set<File>>emptyMap());
      doBuild(descriptor, scope, false, true, false).assertSuccessful();
    }
    finally {
      descriptor.release();
    }
  }

  protected BuildResult doBuild(final ProjectDescriptor descriptor, CompileScope scope,
                                final boolean make, final boolean rebuild, final boolean forceCleanCaches) {
    IncProjectBuilder builder = new IncProjectBuilder(descriptor, BuilderRegistry.getInstance(), Collections.<String, String>emptyMap(), CanceledStatus.NULL, null);
    BuildResult result = new BuildResult();
    builder.addMessageHandler(result);
    try {
      builder.build(scope, make, rebuild, forceCleanCaches);
    }
    catch (RebuildRequestedException e) {
      throw new RuntimeException(e);
    }
    return result;
  }

  protected String createFile(String relativePath) {
    return createFile(relativePath, "");
  }

  protected String createFile(String relativePath, final String text) {
    try {
      File file = new File(getOrCreateProjectDir(), relativePath);
      FileUtil.writeToFile(file, text);
      return FileUtil.toSystemIndependentName(file.getAbsolutePath());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private File getOrCreateProjectDir() {
    if (myProjectDir == null) {
      try {
        myProjectDir = doGetProjectDir();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return myProjectDir;
  }

  protected File doGetProjectDir() throws IOException {
    return FileUtil.createTempDirectory("prj", null);
  }

  protected String getAbsolutePath(final String pathRelativeToProjectRoot) {
    return FileUtil.toSystemIndependentName(new File(getOrCreateProjectDir(), pathRelativeToProjectRoot).getAbsolutePath());
  }

  protected JpsModule addModule(String moduleName, String... srcPaths) {
    if (myJdk == null) {
      myJdk = addJdk("1.6");
    }
    return addModule(moduleName, srcPaths, getAbsolutePath("out/production/" + moduleName), myJdk);
  }

  protected String getProjectRelativePath(String path) {
    assertNotNull(myProjectDir);
    final String projectDir = FileUtil.toSystemIndependentName(myProjectDir.getAbsolutePath());
    return FileUtil.getRelativePath(projectDir, path, '/');
  }
}
