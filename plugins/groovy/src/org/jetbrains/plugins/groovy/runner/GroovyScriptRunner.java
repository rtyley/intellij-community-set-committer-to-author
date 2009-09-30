package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;

/**
 * @author peter
 */
public abstract class GroovyScriptRunner {

  public abstract boolean isValidModule(@NotNull Module module);

  public abstract boolean ensureRunnerConfigured(@Nullable Module module, final String confName, final Project project) throws ExecutionException;

  public abstract void configureCommandLine(JavaParameters params, @Nullable Module module, boolean tests, VirtualFile script,
                                            GroovyScriptRunConfiguration configuration) throws CantRunException;

  protected static String getConfPath(final String groovyHomePath) {
    String confpath = FileUtil.toSystemDependentName(groovyHomePath + "/conf/groovy-starter.conf");
    if (new File(confpath).exists()) {
      return confpath;
    }

    try {
      final String jarPath = PathUtil.getJarPathForClass(GroovyScriptRunner.class);
      if (new File(jarPath).isFile()) { //jar; distribution mode
        return new File(jarPath, "../groovy-starter.conf").getCanonicalPath();
      }

      //else, it's directory in out, development mode
      return new File(jarPath, "conf/groovy-starter.conf").getCanonicalPath();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static void setGroovyHome(JavaParameters params, String groovyHome) {
    params.getVMParametersList().add("-Dgroovy.home=" + groovyHome);
    if (groovyHome.contains("grails")) { //a bit of a hack
      params.getVMParametersList().add("-Dgrails.home=" + groovyHome);
    }
    if (groovyHome.contains("griffon")) { //a bit of a hack
      params.getVMParametersList().add("-Dgriffon.home=" + groovyHome);
    }
  }

  protected static void setToolsJar(JavaParameters params) {
    Sdk jdk = params.getJdk();
    if (jdk != null && jdk.getSdkType() instanceof JavaSdkType) {
      String toolsPath = ((JavaSdkType)jdk.getSdkType()).getToolsPath(jdk);
      if (toolsPath != null) {
        params.getVMParametersList().add("-Dtools.jar=" + toolsPath);
      }
    }
  }

  @Nullable
  protected static VirtualFile findGroovyJar(@NotNull Module module) {
    final Pattern pattern = Pattern.compile(".*[\\\\/]groovy[^\\\\/]*jar");
    for (Library library : GroovyConfigUtils.getInstance().getSDKLibrariesByModule(module)) {
      for (VirtualFile root : library.getFiles(OrderRootType.CLASSES)) {
        if (pattern.matcher(root.getPresentableUrl()).matches()) {
          return root;
        }
      }
    }
    return null;
  }

  protected static void addClasspathFromRootModel(@Nullable Module module, boolean isTests, JavaParameters params) throws CantRunException {
    if (module == null) {
      return;
    }

    final JavaParameters tmp = new JavaParameters();
    tmp.configureByModule(module, isTests ? JavaParameters.CLASSES_AND_TESTS : JavaParameters.CLASSES_ONLY);
    if (tmp.getClassPath().getVirtualFiles().isEmpty()) {
      return;
    }


    final boolean embeddable =
      LibrariesUtil.isEmbeddableDistribution(ModuleRootManager.getInstance(module).getFiles(OrderRootType.CLASSES));
    if (embeddable) {
      params.getProgramParametersList().add("--classpath");
      params.getProgramParametersList().add(tmp.getClassPath().getPathsString());
      return;
    }

    final LinkedHashSet<String> pathList = new LinkedHashSet<String>(tmp.getClassPath().getPathList());
    for (Library library : GroovyConfigUtils.getInstance().getSDKLibrariesByModule(module)) {
      for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
        pathList.remove(file.getPresentableUrl());
      }
    }
    if (pathList.isEmpty()) {
      return;
    }

    params.getProgramParametersList().add("--classpath");
    params.getProgramParametersList().add(StringUtil.join(pathList, File.pathSeparator));
  }

}
