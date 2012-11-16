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

package org.jetbrains.plugins.groovy.griffon;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.IgnoredBeanFactory;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import gnu.trove.TIntArrayList;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.mvc.MvcFramework;
import org.jetbrains.plugins.groovy.mvc.MvcModuleStructureUtil;
import org.jetbrains.plugins.groovy.mvc.MvcPathMacros;
import org.jetbrains.plugins.groovy.mvc.MvcProjectStructure;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author peter
 */
public class GriffonFramework extends MvcFramework {
  @NonNls private static final String GRIFFON_COMMON_PLUGINS = "-griffonPlugins";
  private static final String GLOBAL_PLUGINS_MODULE_NAME = "GriffonGlobalPlugins";

  public static final String GRIFFON_USER_LIBRARY = "Griffon:lib";

  private static final Pattern PLUGIN_NAME_JSON_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern PLUGIN_VERSION_JSON_PATTERN = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");

  private GriffonFramework() {
  }

  public boolean hasSupport(@NotNull Module module) {
    return getSdkRoot(module) != null && findAppRoot(module) != null && !isAuxModule(module);
  }

  @Override
  public String getApplicationDirectoryName() {
    return "griffon-app";
  }

  @Override
  public boolean isToReformatOnCreation(VirtualFile file) {
    return file.getFileType() == GroovyFileType.GROOVY_FILE_TYPE;
  }

  @Override
  public void upgradeFramework(@NotNull Module module) {
  }

  @Nullable
  @Override
  protected GeneralCommandLine getCreationCommandLine(Module module) {
    GriffonCreateProjectDialog dialog = new GriffonCreateProjectDialog(module);
    dialog.show();
    if (!dialog.isOK()) {
      return null;
    }

    return createCommandAndShowErrors(null, module, true, dialog.getCommand(), dialog.getArguments());
  }

  @Override
  public boolean updatesWholeProject() {
    return false;
  }

  @Override
  public void updateProjectStructure(final @NotNull Module module) {
    if (!MvcModuleStructureUtil.isEnabledStructureUpdate()) return;

    final VirtualFile root = findAppRoot(module);
    if (root == null) return;

    AccessToken token = WriteAction.start();
    try {
      MvcModuleStructureUtil.updateModuleStructure(module, createProjectStructure(module, false), root);

      if (hasSupport(module)) {
        MvcModuleStructureUtil.updateAuxiliaryPluginsModuleRoots(module, this);
        MvcModuleStructureUtil.updateGlobalPluginModule(module.getProject(), this);
      }
    }
    finally {
      token.finish();
    }

    final Project project = module.getProject();
    ChangeListManager.getInstance(project).addFilesToIgnore(IgnoredBeanFactory.ignoreUnderDirectory(getUserHomeGriffon(), project));
  }

  @Override
  public void ensureRunConfigurationExists(@NotNull Module module) {
    final VirtualFile root = findAppRoot(module);
    if (root != null) {
      ensureRunConfigurationExists(module, GriffonRunConfigurationType.getInstance(), "Griffon:" + root.getName());
    }
  }

  @Override
  public String getInstalledPluginNameByPath(Project project, @NotNull VirtualFile pluginPath) {
    String nameFromPluginXml = super.getInstalledPluginNameByPath(project, pluginPath);
    if (nameFromPluginXml != null) {
      return nameFromPluginXml;
    }

    VirtualFile pluginJson = pluginPath.findChild("plugin.json");
    if (pluginJson != null) {
      String pluginAndVersion = pluginPath.getName(); // pluginName-version

      TIntArrayList separatorIndexes = new TIntArrayList();
      int start = -1;
      while (true) {
        start = pluginAndVersion.indexOf('-', start + 1);
        if (start == -1) break;
        separatorIndexes.add(start);
      }

      if (separatorIndexes.size() == 1) {
        return pluginAndVersion.substring(0, separatorIndexes.get(0));
      }

      if (separatorIndexes.size() > 0) {
        String json;
        try {
          json = VfsUtil.loadText(pluginJson);
        }
        catch (IOException e) {
          return null;
        }

        for (int i = 0; i < separatorIndexes.size(); i++) {
          int idx = separatorIndexes.get(i);
          String name = pluginAndVersion.substring(0, idx);
          String version = pluginAndVersion.substring(idx + 1);

          if (hasValue(PLUGIN_NAME_JSON_PATTERN, json, name) && hasValue(PLUGIN_VERSION_JSON_PATTERN, json, version)) {
            return name;
          }
        }
      }
    }

    return null;
  }

  private static boolean hasValue(Pattern pattern, String text, String value) {
    Matcher matcher = pattern.matcher(text);
    while (matcher.find()) {
      if (matcher.group(1).equals(value)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public VirtualFile getSdkRoot(@Nullable Module module) {
    if (module == null) return null;
    final VirtualFile[] classRoots = ModuleRootManager.getInstance(module).orderEntries().librariesOnly().getClassesRoots();
    for (VirtualFile file : classRoots) {
      if (GriffonLibraryPresentationProvider.isGriffonCoreJar(file)) {
        final VirtualFile localFile = JarFileSystem.getInstance().getVirtualFileForJar(file);
        if (localFile != null) {
          final VirtualFile parent = localFile.getParent();
          if (parent != null) {
            return parent.getParent();
          }
        }
        return null;
      }
    }
    return null;
  }

  @Override
  public String getUserLibraryName() {
    return GRIFFON_USER_LIBRARY;
  }

  @Override
  public JavaParameters createJavaParameters(@NotNull Module module, boolean forCreation, boolean forTests,
                                             boolean classpathFromDependencies,
                                             @Nullable String jvmParams,
                                             @NotNull String command,
                                             @NotNull String... args) throws ExecutionException {
    JavaParameters params = new JavaParameters();

    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();

    params.setJdk(sdk);
    final VirtualFile sdkRoot = getSdkRoot(module);
    if (sdkRoot == null) {
      return params;
    }

    Map<String, String> env = params.getEnv();
    if (env == null) {
      env = new HashMap<String, String>();
      params.setEnv(env);
    }
    env.put(getSdkHomePropertyName(), FileUtil.toSystemDependentName(sdkRoot.getPath()));

    final VirtualFile lib = sdkRoot.findChild("lib");
    if (lib != null) {
      for (final VirtualFile child : lib.getChildren()) {
        final String name = child.getName();
        if (name.startsWith("groovy-all-") && name.endsWith(".jar")) {
          params.getClassPath().add(child);
        }
      }
    }
    final VirtualFile dist = sdkRoot.findChild("dist");
    if (dist != null) {
      for (final VirtualFile child : dist.getChildren()) {
        final String name = child.getName();
        if (name.endsWith(".jar")) {
          if (name.startsWith("griffon-cli-") || name.startsWith("griffon-rt-") || name.startsWith("griffon-resources-")) {
            params.getClassPath().add(child);
          }
        }
      }
    }


    /////////////////////////////////////////////////////////////

    params.setMainClass("org.codehaus.griffon.cli.support.GriffonStarter");

    final VirtualFile rootFile;

    if (forCreation) {
      VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
      if (roots.length != 1) {
        throw new ExecutionException("Failed to initialize griffon module: module " + module.getName() + " contains more than one root");
      }

      args = ArrayUtil.mergeArrays(new String[]{roots[0].getName()}, args);
      rootFile = roots[0].getParent();
    }
    else {
      rootFile = findAppRoot(module);
      if (rootFile == null) {
        throw new ExecutionException("Failed to run griffon command: module " + module.getName() + " is not a Griffon module");
      }
    }

    String workDir = VfsUtilCore.virtualToIoFile(rootFile).getAbsolutePath();

    if (jvmParams != null) {
      params.getVMParametersList().addParametersString(jvmParams);
    }

    if (!params.getVMParametersList().getParametersString().contains(XMX_JVM_PARAMETER)) {
      params.getVMParametersList().add("-Xmx256M");
    }

    final String griffonHomePath = FileUtil.toSystemDependentName(sdkRoot.getPath());
    params.getVMParametersList().add("-Dgriffon.home=" + griffonHomePath);
    params.getVMParametersList().add("-Dbase.dir=" + workDir);

    assert sdk != null;
    params.getVMParametersList().add("-Dtools.jar=" + ((JavaSdkType)sdk.getSdkType()).getToolsPath(sdk));

    final String confpath = griffonHomePath + GROOVY_STARTER_CONF;
    params.getVMParametersList().add("-Dgroovy.starter.conf=" + confpath);

    params.getVMParametersList().add(
      "-Dgroovy.sanitized.stacktraces=\"groovy., org.codehaus.groovy., java., javax., sun., gjdk.groovy., gant., org.codehaus.gant.\"");

    params.getProgramParametersList().add("--main");
    params.getProgramParametersList().add("org.codehaus.griffon.cli.GriffonScriptRunner");
    params.getProgramParametersList().add("--conf");
    params.getProgramParametersList().add(confpath);
    if (!forCreation && classpathFromDependencies) {
      final String path = getApplicationClassPath(module).getPathsString();
      if (StringUtil.isNotEmpty(path)) {
        params.getProgramParametersList().add("--classpath");
        params.getProgramParametersList().add(path);
      }
    }

    params.setWorkingDirectory(workDir);

    String argsString = args.length == 0 ? command : command + ' ' + StringUtil.join(args, " ");
    params.getProgramParametersList().add(argsString);

    params.setDefaultCharset(module.getProject());

    return params;
  }

  @Override
  public String getFrameworkName() {
    return "Griffon";
  }

  @Override
  public Icon getIcon() {
    return JetgroovyIcons.Griffon.Griffon;
  }

  @Override
  public Icon getToolWindowIcon() {
    return JetgroovyIcons.Griffon.GriffonToolWindow;
  }

  @Override
  public String getSdkHomePropertyName() {
    return "GRIFFON_HOME";
  }

  @Override
  protected String getCommonPluginSuffix() {
    return GRIFFON_COMMON_PLUGINS;
  }

  @Override
  public String getGlobalPluginsModuleName() {
    return GLOBAL_PLUGINS_MODULE_NAME;
  }

  @Nullable
  public File getDefaultSdkWorkDir(@NotNull Module module) {
    final String version = GriffonLibraryPresentationProvider.getGriffonVersion(module);
    if (version == null) return null;

    return new File(getUserHomeGriffon(), version);
  }

  @Override
  public boolean isSDKLibrary(Library library) {
    return GriffonLibraryPresentationProvider.isGriffonSdk(library.getFiles(OrderRootType.CLASSES));
  }

  @Override
  public MvcProjectStructure createProjectStructure(@NotNull Module module, boolean auxModule) {
    return new GriffonProjectStructure(module, auxModule);
  }

  @Override
  public LibraryKind getLibraryKind() {
    return GriffonLibraryPresentationProvider.GRIFFON_KIND;
  }

  @Override
  public String getSomeFrameworkClass() {
    return "griffon.core.GriffonApplication";
  }

  public static String getUserHomeGriffon() {
    return MvcPathMacros.getSdkWorkDirParent("griffon");
  }

  public static GriffonFramework getInstance() {
    return EP_NAME.findExtension(GriffonFramework.class);
  }

  public VirtualFile getApplicationPropertiesFile(Module module) {
    final VirtualFile appRoot = findAppRoot(module);
    return appRoot != null ? appRoot.findChild("application.properties") : null;
  }

  @Override
  public String getApplicationName(Module module) {
    final VirtualFile appProperties = getApplicationPropertiesFile(module);
    if (appProperties != null) {
      final PsiFile file = PsiManager.getInstance(module.getProject()).findFile(appProperties);
      if (file instanceof PropertiesFile) {
        final IProperty property = ((PropertiesFile)file).findPropertyByKey("application.name");
        return property != null ? property.getValue() : super.getApplicationName(module);
      }
    }
    return super.getApplicationName(module);
  }

  private static class GriffonProjectStructure extends MvcProjectStructure {
    public GriffonProjectStructure(Module module, final boolean auxModule) {
      super(module, auxModule, getUserHomeGriffon(), GriffonFramework.getInstance().getSdkWorkDir(module));
    }

    @NotNull
    public String getUserLibraryName() {
      return GRIFFON_USER_LIBRARY;
    }

    public String[] getSourceFolders() {
      List<String> sourceFolders = new ArrayList<String>();

      for (VirtualFile file : ModuleRootManager.getInstance(myModule).getContentRoots()) {
        handleSrc(file.findChild("src"), sourceFolders);
        VirtualFile griffonApp = file.findChild("griffon-app");
        handleGriffonApp(griffonApp, sourceFolders);
        List<GriffonSourceInspector.GriffonSource> sources =
          GriffonSourceInspector.processModuleMetadata(myModule);
        for (GriffonSourceInspector.GriffonSource source : sources) {
          sourceFolders.add(source.getPath());
        }
        if (griffonApp != null) {
          for (VirtualFile child : file.getChildren()) {
            if (child.getNameWithoutExtension().endsWith("GriffonAddon")) {
              sourceFolders.add("");
              break;
            }
          }
        }
      }
      return sourceFolders.toArray(new String[sourceFolders.size()]);
    }

    private void handleGriffonApp(VirtualFile griffonApp, List<String> sourceFolders) {
      if (griffonApp == null) return;
      // Add standard artifacts, i.e, models, views, controllers, services, conf, lifecycle
      for (String child : new String[]{"models", "views", "controllers", "services", "conf", "lifecycle"}) {
        if (griffonApp.findChild(child) != null) {
          sourceFolders.add("griffon-app/" + child);
        }
      }
    }

    private void handleSrc(VirtualFile src, List<String> sourceFolders) {
      if (src == null) return;
      for (String child : new String[]{"main", "cli"}) {
        if (src.findChild(child) != null) {
          sourceFolders.add("src/" + child);
        }
      }
    }

    private void handleTest(VirtualFile test, List<String> sourceFolders) {
      if (test == null) return;
      for (String child : new String[]{"unit", "integration", "shared"}) {
        if (test.findChild(child) != null) {
          sourceFolders.add("test/" + child);
        }
      }
    }

    public String[] getTestFolders() {
      List<String> sourceFolders = new ArrayList<String>();

      for (VirtualFile file : ModuleRootManager.getInstance(myModule).getContentRoots()) {
        handleTest(file.findChild("test"), sourceFolders);
      }
      return sourceFolders.toArray(new String[sourceFolders.size()]);
    }

    public String[] getInvalidSourceFolders() {
      return new String[]{"src"};
    }

    @Override
    public String[] getExcludedFolders() {
      return new String[]{"target/classes", "target/test-classes"};
    }
  }
}
