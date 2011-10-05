package org.jetbrains.jps.server;

import org.codehaus.gant.GantBinding;
import org.codehaus.groovy.runtime.MethodClosure;
import org.jetbrains.jps.JavaSdk;
import org.jetbrains.jps.Library;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.idea.IdeaProjectLoader;
import org.jetbrains.jps.incremental.BuilderRegistry;
import org.jetbrains.jps.incremental.CompileScope;
import org.jetbrains.jps.incremental.IncProjectBuilder;
import org.jetbrains.jps.incremental.MessageHandler;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/10/11
 * @noinspection UnusedDeclaration
 */
public class Facade {
  public static final String IDEA_PROJECT_DIRNAME = ".idea";

  private final Map<String, Project> myProjects = new HashMap<String, Project>();
  private final Object myConfigurationLock = new Object();
  private final Map<String, String> myPathVariables = new HashMap<String, String>();
  private final List<GlobalLibrary> myGlobalLibraries = new ArrayList<GlobalLibrary>();

  public void setGlobals(List<GlobalLibrary> libs, Map<String, String> pathVars) {
    synchronized (myConfigurationLock) {
      myGlobalLibraries.clear();
      myGlobalLibraries.addAll(libs);
      myPathVariables.clear();
      myPathVariables.putAll(pathVars);
    }
  }

  public void clearProjectCache(String projectPath) {
    synchronized (myConfigurationLock) {
      myProjects.remove(projectPath);
    }
  }

  public void startBuild(String projectPath, Set<String> modules, final BuildParameters params, final MessageHandler msgHandler) throws Throwable {
    Project project;

    synchronized (myConfigurationLock) {
      project = myProjects.get(projectPath);
      if (project == null) {
        project = loadProject(projectPath, params);
        myProjects.put(projectPath, project);
      }
    }

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

    final CompileScope compileScope = new CompileScope(project) {
      public Collection<Module> getAffectedModules() {
        return toCompile;
      }
    };

    final IncProjectBuilder builder = new IncProjectBuilder(project, BuilderRegistry.getInstance());
    if (msgHandler != null) {
      builder.addMessageHandler(msgHandler);
    }
    switch (params.buildType) {
      case REBUILD:
        builder.build(compileScope, false);
        break;

      case MAKE:
        builder.build(compileScope, true);
        break;

      case CLEAN:
        project.clean();
        break;
    }
    //pw.save();
  }

  private Project loadProject(String projectPath, BuildParameters params) {
    final Project project = new Project(new GantBinding());
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
    final boolean dirBased = !(projectFile.isFile() && projectPath.endsWith(".ipr"));

    //String root = dirBased ? projectPath : projectFile.getParent();

    final String loadPath = dirBased ? new File(projectFile, IDEA_PROJECT_DIRNAME).getPath() : projectPath;
    IdeaProjectLoader.loadFromPath(project, loadPath, myPathVariables, getStartupScript());
    return project;
  }

  private String getStartupScript() {
    //return "import org.jetbrains.jps.*\n";
    return null;
  }

  private static class InstanceHolder {
    static final Facade ourInstance = new Facade();
  }

  public static Facade getInstance() {
    return InstanceHolder.ourInstance;
  }

}
