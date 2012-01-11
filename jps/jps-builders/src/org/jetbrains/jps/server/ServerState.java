package org.jetbrains.jps.server;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.codehaus.groovy.runtime.MethodClosure;
import org.jetbrains.jps.JavaSdk;
import org.jetbrains.jps.Library;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.api.BuildParameters;
import org.jetbrains.jps.api.BuildType;
import org.jetbrains.jps.api.GlobalLibrary;
import org.jetbrains.jps.api.SdkLibrary;
import org.jetbrains.jps.idea.IdeaProjectLoader;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.storage.ProjectTimestamps;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/10/11
 * @noinspection UnusedDeclaration
 */
class ServerState {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.server.ServerState");
  public static final String IDEA_PROJECT_DIRNAME = ".idea";

  private final Map<String, ProjectDescriptor> myProjects = new HashMap<String, ProjectDescriptor>();

  private final Object myConfigurationLock = new Object();
  private final Map<String, String> myPathVariables = new HashMap<String, String>();
  private final List<GlobalLibrary> myGlobalLibraries = new ArrayList<GlobalLibrary>();

  public void setGlobals(List<GlobalLibrary> libs, Map<String, String> pathVars) {
    synchronized (myConfigurationLock) {
      for (ProjectDescriptor descriptor : myProjects.values()) {
        descriptor.close();
      }
      myProjects.clear(); // projects should be reloaded against the latest data
      myGlobalLibraries.clear();
      myGlobalLibraries.addAll(libs);
      myPathVariables.clear();
      myPathVariables.putAll(pathVars);
    }
  }

  public void notifyFileChanged(String projectPath, File file, RootDescriptor rd) {
    try {
      final ProjectDescriptor d;
      synchronized (myConfigurationLock) {
        d = myProjects.get(projectPath);
      }
      if (d != null) {
        d.fsState.markDirty(file, rd, d.timestamps.getStorage());
      }
    }
    catch (Exception e) {
      LOG.error(e); // todo
    }
  }

  public void notifyFileDeleted(String projectPath, Module module, String filePath, final boolean isTest) {
    try {
      final ProjectDescriptor d;
      synchronized (myConfigurationLock) {
        d = myProjects.get(projectPath);
      }
      if (d != null) {
        d.fsState.registerDeleted(module, FileUtil.toCanonicalPath(filePath), isTest, d.timestamps.getStorage());
      }
    }
    catch (Exception e) {
      LOG.error(e); // todo
    }
  }

  public void clearProjectCache(Collection<String> projectPaths) {
    synchronized (myConfigurationLock) {
      for (String projectPath : projectPaths) {
        final ProjectDescriptor descriptor = myProjects.remove(projectPath);
        if (descriptor != null) {
          descriptor.close();
        }
      }
    }
  }

  public void startBuild(String projectPath, Set<String> modules, final BuildParameters params, final MessageHandler msgHandler) throws Throwable{
    final String projectName = getProjectName(projectPath);
    BuildType buildType = params.buildType;

    ProjectDescriptor descriptor;
    synchronized (myConfigurationLock) {
      descriptor = myProjects.get(projectPath);
      if (descriptor == null) {
        final Project project = loadProject(projectPath, params);
        final FSState fsState = new FSState();
        descriptor = new ProjectDescriptor(projectName, project, fsState, new ProjectTimestamps(projectName));
        myProjects.put(projectPath, descriptor);
      }
    }

    final Project project = descriptor.project;

    try {
      final List<Module> toCompile = new ArrayList<Module>();
      if (modules != null && modules.size() > 0) {
        for (Module m : project.getModules().values()) {
          if (modules.contains(m.getName())){
            toCompile.add(m);
          }
        }
      }
      else {
        toCompile.addAll(project.getModules().values());
      }

      final CompileScope compileScope = new CompileScope(project, toCompile);

      final IncProjectBuilder builder = new IncProjectBuilder(descriptor, BuilderRegistry.getInstance());
      if (msgHandler != null) {
        builder.addMessageHandler(msgHandler);
      }
      switch (buildType) {
        case PROJECT_REBUILD:
          builder.build(compileScope, false, true);
          break;

        case FORCED_COMPILATION:
          builder.build(compileScope, false, false);
          break;

        case MAKE:
          builder.build(compileScope, true, false);
          break;

        case CLEAN:
          //todo[nik]
  //        new ProjectBuilder(new GantBinding(), project).clean();
          break;
      }
    }
    finally {
      clearZipIndexCache();
    }
  }

  private static void clearZipIndexCache() {
    try {
      final Class<?> indexClass = Class.forName("com.sun.tools.javac.zip.ZipFileIndex");
      final Method clearMethod = indexClass.getMethod("clearCache");
      clearMethod.invoke(null);
    }
    catch (Throwable ex) {
      LOG.info(ex);
    }
  }

  private static String getProjectName(String projectPath) {
    final File path = new File(projectPath);
    final String name = path.getName().toLowerCase(Locale.US);
    if (!isDirectoryBased(path) && name.endsWith(".ipr")) {
      return name.substring(0, name.length() - ".ipr".length());
    }
    return name;
  }

  private Project loadProject(String projectPath, BuildParameters params) {
    final Project project = new Project();
    // setup JDKs and global libraries
    final MethodClosure fakeClosure = new MethodClosure(new Object(), "hashCode");
    for (GlobalLibrary library : myGlobalLibraries) {
      if (library instanceof SdkLibrary) {
        final SdkLibrary sdk = (SdkLibrary)library;
        final JavaSdk jdk = project.createJavaSdk(sdk.getName(), sdk.getHomePath(), fakeClosure);
        jdk.setClasspath(sdk.getPaths());
      }
      else {
        final Library lib = project.createGlobalLibrary(library.getName(), fakeClosure);
        lib.setClasspath(library.getPaths());
      }
    }

    final File projectFile = new File(projectPath);

    //String root = dirBased ? projectPath : projectFile.getParent();

    final String loadPath = isDirectoryBased(projectFile) ? new File(projectFile, IDEA_PROJECT_DIRNAME).getPath() : projectPath;
    IdeaProjectLoader.loadFromPath(project, loadPath, myPathVariables, getStartupScript());
    return project;
  }

  private static boolean isDirectoryBased(File projectFile) {
    return !(projectFile.isFile() && projectFile.getName().endsWith(".ipr"));
  }

  private String getStartupScript() {
    //return "import org.jetbrains.jps.*\n";
    return null;
  }

  private static class InstanceHolder {
    static final ServerState ourInstance = new ServerState();
  }

  public static ServerState getInstance() {
    return InstanceHolder.ourInstance;
  }

}
