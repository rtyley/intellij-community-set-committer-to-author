/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.compiler;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jpsservice.Bootstrap;
import org.jetbrains.jpsservice.Client;

import java.io.File;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/6/11
 */
public class JpsServerManager implements ApplicationComponent{
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.JpsServerManager");
  private static final String COMPILE_SERVER_SYSTEM_ROOT = "compile-server";
  private volatile Client myServerClient;
  private volatile OSProcessHandler myProcessHandler;

  public JpsServerManager() {
    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      @Override
      public void run() {
        shutdownServer(myServerClient, myProcessHandler);
      }
    });
  }

  public static JpsServerManager getInstance() {
    return ApplicationManager.getApplication().getComponent(JpsServerManager.class);
  }

  @Nullable
  public Client getClient() {
    if (!ensureServerStarted()) {
      return null;
    }
    return myServerClient;
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
    shutdownServer(myServerClient, myProcessHandler);
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "com.intellij.compiler.JpsServerManager";
  }

  private volatile boolean myStartupFailed = false;

  private boolean ensureServerStarted() {
    if (myProcessHandler != null) {
      return true;
    }
    if (!myStartupFailed) {
      try {
        final int port = NetUtils.findAvailableSocketPort();
        final Process process = launchServer(port);
        final OSProcessHandler processHandler = new OSProcessHandler(process, null);
        processHandler.startNotify();
        myServerClient = new Client();
        myServerClient.connect(NetUtils.getLocalHostString(), port);

        myProcessHandler = processHandler;
        return true;
      }
      catch (Throwable e) {
        myStartupFailed = true;
        LOG.error(e); // todo
      }
    }
    return false;
  }

  private static Process launchServer(int port) throws ExecutionException {
    final Sdk projectJdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    final GeneralCommandLine cmdLine = new GeneralCommandLine();
    cmdLine.setExePath(((JavaSdkType)projectJdk.getSdkType()).getVMExecutablePath(projectJdk));
    cmdLine.addParameter("-Xmx256m");
    cmdLine.addParameter("-classpath");

    final List<File> cp = Bootstrap.buildServerProcessClasspath();
    cmdLine.addParameter(classpathToString(cp));

    cmdLine.addParameter("org.jetbrains.jpsservice.Server");
    cmdLine.addParameter(Integer.toString(port));

    final File workDirectory = new File(PathManager.getSystemPath(), COMPILE_SERVER_SYSTEM_ROOT);
    workDirectory.mkdirs();
    cmdLine.setWorkDirectory(workDirectory);

    return cmdLine.createProcess();
  }

  private static void shutdownServer(final Client client, final OSProcessHandler processHandler) {
    try {
      if (client != null) {
        final Future future = client.sendShutdownRequest();
        if (future != null) {
          future.get(500, TimeUnit.MILLISECONDS);
        }
      }
    }
    catch (Throwable ignored) {
      LOG.info(ignored);
    }
    finally {
      if (processHandler != null) {
        processHandler.destroyProcess();
      }
    }
  }

  private static String classpathToString(List<File> cp) {
    StringBuilder builder = new StringBuilder();
    for (File file : cp) {
      if (builder.length() > 0) {
        builder.append(File.pathSeparator);
      }
      builder.append(file.getAbsolutePath());
    }
    return builder.toString();
  }
}
