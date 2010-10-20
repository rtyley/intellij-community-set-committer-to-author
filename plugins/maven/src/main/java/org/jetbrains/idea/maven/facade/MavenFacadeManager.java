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
package org.jetbrains.idea.maven.facade;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.rmi.RemoteProcessSupport;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Alarm;
import com.intellij.util.PathUtil;
import com.intellij.util.SmartList;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.apache.lucene.search.Query;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;
import java.nio.charset.Charset;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MavenFacadeManager extends RemoteObjectWrapper<MavenFacade> {
  @NonNls private static final String MAIN_CLASS = "org.jetbrains.idea.maven.facade.RemoteMavenServer";

  private final RemoteProcessSupport<Object, MavenFacade, Object> mySupport;

  private final RemoteMavenFacadeLogger myLogger = new RemoteMavenFacadeLogger();
  private final RemoteMavenFacadeDownloadListener myDownloadListener = new RemoteMavenFacadeDownloadListener();
  private boolean myLoggerExported;
  private boolean myDownloadListenerExported;

  private final Alarm myShutdownAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  public static MavenFacadeManager getInstance() {
    return ServiceManager.getService(MavenFacadeManager.class);
  }

  public MavenFacadeManager() {
    super(null);

    mySupport = new RemoteProcessSupport<Object, MavenFacade, Object>(MavenFacade.class) {
      @Override
      protected void fireModificationCountChanged() {
      }

      @Override
      protected String getName(Object file) {
        return MavenFacadeManager.class.getSimpleName();
      }

      @Override
      protected RunProfileState getRunProfileState(Object target, Object configuration, Executor executor) throws ExecutionException {
        return createRunProfileState();
      }
    };

    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      public void run() {
        shutdown(false);
      }
    });
  }

  @NotNull
  protected synchronized MavenFacade create() throws RemoteException {
    MavenFacade result;
    try {
      result = mySupport.acquire(this, "");
    }
    catch (Exception e) {
      throw new RemoteException("Cannot start maven service", e);
    }

    myLoggerExported = UnicastRemoteObject.exportObject(myLogger, 0) != null;
    myDownloadListenerExported = UnicastRemoteObject.exportObject(myDownloadListener, 0) != null;
    result.set(myLogger, myDownloadListener);

    return result;
  }

  public synchronized void shutdown(boolean wait) {
    mySupport.stopAll(wait);
    cleanup();
  }

  protected synchronized void cleanup() {
    super.cleanup();

    if (myLoggerExported) {
      try {
        UnicastRemoteObject.unexportObject(myLogger, true);
      }
      catch (RemoteException e) {
        MavenLog.LOG.error(e);
      }
      myLoggerExported = false;
    }
    if (myDownloadListenerExported) {
      try {
        UnicastRemoteObject.unexportObject(myDownloadListener, true);
      }
      catch (RemoteException e) {
        MavenLog.LOG.error(e);
      }
      myDownloadListenerExported = false;
    }

    myShutdownAlarm.cancelAllRequests();
  }

  @Override
  protected void onWrappeeAccessed() {
    myShutdownAlarm.cancelAllRequests();
    myShutdownAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        shutdown(true);
      }
    }, 5 * 60 * 1000);
  }

  private RunProfileState createRunProfileState() {
    return new CommandLineState(null) {
      private SimpleJavaParameters createJavaParameters() throws ExecutionException {
        final SimpleJavaParameters params = new SimpleJavaParameters();

        params.setJdk(new SimpleJavaSdkType().createJdk("tmp", SystemProperties.getJavaHome()));

        params.setWorkingDirectory(PathManager.getBinPath());
        final ArrayList<String> classPath = new ArrayList<String>();
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(NotNull.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(StringUtil.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(THashSet.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(Element.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(Query.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(Query.class), classPath);
        params.getClassPath().add(PathManager.getResourceRoot(getClass(), "/messages/CommonBundle.properties"));
        params.getClassPath().addAll(classPath);
        params.getClassPath().addAllFiles(collectClassPathAndLIbsFolder().first);

        params.setMainClass(MAIN_CLASS);

        // todo pass sensible parameters, MAVEN_OPTS?
        if (SystemInfo.isMac) {
          String arch = System.getProperty("sun.arch.data.model");
          if (arch != null) {
            params.getVMParametersList().addParametersString("-d" + arch);
          }
        }
        params.getVMParametersList().addParametersString("-Djava.awt.headless=true -Xmx512m");
        //params.getVMParametersList().addParametersString("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5009");
        return params;
      }

      @Override
      public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
        ProcessHandler processHandler = startProcess();
        return new DefaultExecutionResult(null, processHandler, AnAction.EMPTY_ARRAY);
      }

      protected OSProcessHandler startProcess() throws ExecutionException {
        SimpleJavaParameters params = createJavaParameters();
        Sdk sdk = params.getJdk();

        final GeneralCommandLine commandLine = JdkUtil.setupJVMCommandLine(((JavaSdkType)sdk.getSdkType()).getVMExecutablePath(sdk), params,
                                                                           JdkUtil.useDynamicClasspath(PlatformDataKeys
                                                                                                         .PROJECT.getData(
                                                                               DataManager.getInstance().getDataContext())));
        final OSProcessHandler processHandler = new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString()) {
          @Override
          public Charset getCharset() {
            return commandLine.getCharset();
          }
        };
        ProcessTerminatedListener.attach(processHandler);
        return processHandler;
      }
    };
  }

  public static Pair<List<File>, File> collectClassPathAndLIbsFolder() {
    File pluginFileOrDir = new File(PathUtil.getJarPathForClass(MavenFacadeManager.class));

    File libDir;
    List<File> classpath = new SmartList<File>();

    if (pluginFileOrDir.isDirectory()) {
      classpath.add(new File(pluginFileOrDir.getParent(), "maven-facade-api"));
      classpath.add(new File(pluginFileOrDir.getParent(), "maven-facade-impl"));
      File luceneLib = new File(PathUtil.getJarPathForClass(Query.class));
      libDir = new File(luceneLib.getParentFile().getParentFile().getParentFile(), "facade-impl/lib");
    }
    else {
      libDir = pluginFileOrDir.getParentFile();
    }
    MavenLog.LOG.assertTrue(libDir.exists() && libDir.isDirectory(), "Maven Facade libraries dir not found: " + libDir);

    File[] files = libDir.listFiles();
    for (File jar : files) {
      if (jar.isFile() && jar.getName().endsWith(".jar") && !jar.equals(pluginFileOrDir)) {
        classpath.add(jar);
      }
    }
    return Pair.create(classpath, libDir);
  }

  public MavenEmbedderWrapper createEmbedder(final Project project) {
    final MavenFacadeSettings settings = convertSettings(MavenProjectsManager.getInstance(project).getGeneralSettings());
    return new MavenEmbedderWrapper(this) {
      @NotNull
      @Override
      protected MavenFacadeEmbedder create() throws RemoteException {
        return MavenFacadeManager.this.getOrCreateWrappee().createEmbedder(settings);
      }
    };
  }

  public MavenIndexerWrapper createIndexer() {
    return new MavenIndexerWrapper(this) {
      @NotNull
      @Override
      protected MavenFacadeIndexer create() throws RemoteException {
        return MavenFacadeManager.this.getOrCreateWrappee().createIndexer();
      }
    };
  }

  public List<MavenRepositoryInfo> getRepositories(final String nexusUrl) {
    return perform(new Retriable<List<MavenRepositoryInfo>>() {
      @Override
      public List<MavenRepositoryInfo> execute() throws RemoteException {
        return getOrCreateWrappee().getRepositories(nexusUrl);
      }
    });
  }

  public List<MavenArtifactInfo> findArtifacts(final MavenArtifactInfo template, final String nexusUrl) {
    return perform(new Retriable<List<MavenArtifactInfo>>() {
      @Override
      public List<MavenArtifactInfo> execute() throws RemoteException {
        return getOrCreateWrappee().findArtifacts(template, nexusUrl);
      }
    });
  }

  public MavenModel interpolateAndAlignModel(final MavenModel model, final File basedir) {
    return perform(new Retriable<MavenModel>() {
      @Override
      public MavenModel execute() throws RemoteException {
        return getOrCreateWrappee().interpolateAndAlignModel(model, basedir);
      }
    });
  }

  public MavenModel assembleInheritance(final MavenModel model, final MavenModel parentModel) {
    return perform(new Retriable<MavenModel>() {
      @Override
      public MavenModel execute() throws RemoteException {
        return getOrCreateWrappee().assembleInheritance(model, parentModel);
      }
    });
  }

  public ProfileApplicationResult applyProfiles(final MavenModel model,
                                                final File basedir,
                                                final Collection<String> explicitProfiles,
                                                final Collection<String> alwaysOnProfiles) {
    return perform(new Retriable<ProfileApplicationResult>() {
      @Override
      public ProfileApplicationResult execute() throws RemoteException {
        return getOrCreateWrappee().applyProfiles(model, basedir, explicitProfiles, alwaysOnProfiles);
      }
    });
  }

  public void addDownloadListener(MavenFacadeDownloadListener listener) {
    myDownloadListener.myListeners.add(listener);
  }

  public void removeDownloadListener(MavenFacadeDownloadListener listener) {
    myDownloadListener.myListeners.remove(listener);
  }

  public static MavenFacadeSettings convertSettings(MavenGeneralSettings settings) {
    MavenFacadeSettings result = new MavenFacadeSettings();
    result.setLoggingLevel(settings.getLoggingLevel().getLevel());
    result.setOffline(settings.isWorkOffline());
    result.setMavenHome(settings.getEffectiveMavenHome());
    result.setUserSettingsFile(settings.getEffectiveUserSettingsIoFile());
    result.setGlobalSettingsFile(settings.getEffectiveGlobalSettingsIoFile());
    result.setLocalRepository(settings.getEffectiveLocalRepository());
    result.setPluginUpdatePolicy(settings.getPluginUpdatePolicy().getFacadePolicy());
    result.setSnapshotUpdatePolicy(settings.getSnapshotUpdatePolicy().getFacadePolicy());
    return result;
  }

  public static MavenFacadeConsole wrapAndExport(final MavenConsole console) {
    try {
      RemoteMavenFacadeConsole result = new RemoteMavenFacadeConsole(console);
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  public static MavenFacadeProgressIndicator wrapAndExport(final MavenProgressIndicator process) {
    try {
      RemoteMavenFacadeProgressIndicator result = new RemoteMavenFacadeProgressIndicator(process);
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (RemoteException e) {
      throw new RuntimeException(e);
    }
  }

  private static class RemoteMavenFacadeLogger extends MavenRemoteObject implements MavenFacadeLogger {
    public void info(Throwable e) {
      MavenLog.LOG.info(e);
    }

    public void warn(Throwable e) {
      MavenLog.LOG.warn(e);
    }

    public void error(Throwable e) {
      MavenLog.LOG.error(e);
    }

    public void print(String s) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println(s);
    }
  }

  private static class RemoteMavenFacadeDownloadListener extends MavenRemoteObject implements MavenFacadeDownloadListener {
    private final List<MavenFacadeDownloadListener> myListeners = ContainerUtil.createEmptyCOWList();

    public void artifactDownloaded(File file, String relativePath) throws RemoteException {
      for (MavenFacadeDownloadListener each : myListeners) {
        each.artifactDownloaded(file, relativePath);
      }
    }
  }

  private static class RemoteMavenFacadeProgressIndicator extends MavenRemoteObject implements MavenFacadeProgressIndicator {
    private final MavenProgressIndicator myProcess;

    public RemoteMavenFacadeProgressIndicator(MavenProgressIndicator process) {
      myProcess = process;
    }

    public void setText(String text) {
      myProcess.setText(text);
    }

    public void setText2(String text) {
      myProcess.setText2(text);
    }

    public boolean isCanceled() {
      return myProcess.isCanceled();
    }

    public void setIndeterminate(boolean value) {
      myProcess.getIndicator().setIndeterminate(value);
    }

    public void setFraction(double fraction) {
      myProcess.setFraction(fraction);
    }
  }

  private static class RemoteMavenFacadeConsole extends MavenRemoteObject implements MavenFacadeConsole {
    private final MavenConsole myConsole;

    public RemoteMavenFacadeConsole(MavenConsole console) {
      myConsole = console;
    }

    public void printMessage(int level, String message, Throwable throwable) {
      myConsole.printMessage(level, message, throwable);
    }
  }
}
