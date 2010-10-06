package com.intellij.appengine.sdk.impl;

import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.server.integration.AppEngineServerData;
import com.intellij.appengine.server.integration.AppEngineServerIntegration;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.javaee.appServerIntegrations.ApplicationServer;
import com.intellij.javaee.serverInstances.ApplicationServersManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;

/**
 * @author nik
 */
public class AppEngineSdkImpl implements AppEngineSdk {
  private static final Logger LOG = Logger.getInstance("#com.intellij.appengine.sdk.impl.AppEngineSdkImpl");
  private Map<String, Set<String>> myClassesWhiteList;
  private Map<String, Set<String>> myMethodsBlackList;
  private String myHomePath;

  public AppEngineSdkImpl(String homePath) {
    myHomePath = homePath;
  }

  public File getAppCfgFile() {
    final String extension = SystemInfo.isWindows ? "cmd" : "sh";
    return new File(FileUtil.toSystemDependentName(myHomePath + "/bin/appcfg." + extension));
  }

  public File getWebSchemeFile() {
    return new File(FileUtil.toSystemDependentName(myHomePath + "/docs/appengine-web.xsd"));
  }

  public File getToolsApiJarFile() {
    final String path = FileUtil.toSystemDependentName(myHomePath + "/lib/appengine-tools-api.jar");
    return new File(path);
  }

  public File[] getLibraries() {
    File sdkHome = new File(FileUtil.toSystemDependentName(myHomePath));
    final File libFolder = new File(sdkHome, "lib" + File.separator + "shared");
    List<File> jars = new ArrayList<File>();
    final File[] files = libFolder.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isFile() && file.getName().endsWith(".jar")) {
          jars.add(file);
        }
      }
    }
    return jars.toArray(new File[jars.size()]);
  }

  @NotNull
  public String getSdkHomePath() {
    return myHomePath;
  }

  public boolean isClassInWhiteList(@NotNull String className) {
    if (!isValid()) return true;

    if (myClassesWhiteList == null) {
      File cachedWhiteList = getCachedWhiteListFile();
      if (cachedWhiteList.exists()) {
        try {
          myClassesWhiteList = AppEngineSdkUtil.loadWhiteList(cachedWhiteList);
        }
        catch (IOException e) {
          LOG.error(e);
          myClassesWhiteList = Collections.emptyMap();
        }
      }
      else {
        myClassesWhiteList = AppEngineSdkUtil.computeWhiteList(getToolsApiJarFile());
        AppEngineSdkUtil.saveWhiteList(cachedWhiteList, myClassesWhiteList);
      }
    }
    final String packageName = StringUtil.getPackageName(className);
    final String name = StringUtil.getShortName(className);
    final Set<String> classes = myClassesWhiteList.get(packageName);
    return classes != null && classes.contains(name);
  }

  private File getCachedWhiteListFile() {
    String fileName = StringUtil.getShortName(myHomePath, '/') + Integer.toHexString(myHomePath.hashCode()) + "_" + Long.toHexString(getToolsApiJarFile().lastModified());
    return new File(AppEngineUtil.getAppEngineSystemDir(), fileName);
  }

  public boolean isMethodInBlacklist(@NotNull String className, @NotNull String methodName) {
    if (myMethodsBlackList == null) {
      try {
        myMethodsBlackList = loadBlackList();
      }
      catch (IOException e) {
        LOG.error(e);
        myMethodsBlackList = new THashMap<String, Set<String>>();
      }
    }
    final Set<String> methods = myMethodsBlackList.get(className);
    return methods != null && methods.contains(methodName);
  }

  public boolean isValid() {
    return getToolsApiJarFile().exists() && getAppCfgFile().exists();
  }

  @Nullable
  public ApplicationServer getOrCreateAppServer() {
    if (!isValid()) return null;
    final ApplicationServersManager serversManager = ApplicationServersManager.getInstance();
    final AppEngineServerIntegration integration = AppEngineServerIntegration.getInstance();

    final List<ApplicationServer> servers = serversManager.getApplicationServers(integration);
    File sdkHomeFile = new File(FileUtil.toSystemDependentName(myHomePath));
    for (ApplicationServer server : servers) {
      final String path = ((AppEngineServerData)server.getPersistentData()).getSdkPath();
      if (sdkHomeFile.equals(new File(FileUtil.toSystemDependentName(path)))) {
        return server;
      }
    }

    return ApplicationServersManager.getInstance().createServer(integration, new AppEngineServerData(myHomePath));
  }

  public String getOrmLibDirectoryPath() {
    return getLibUserDirectoryPath() + "/orm";
  }

  public VirtualFile[] getOrmLibSources() {
    final File libsDir = new File(FileUtil.toSystemDependentName(myHomePath + "/src/orm"));
    final File[] files = libsDir.listFiles();
    List<VirtualFile> roots = new ArrayList<VirtualFile>();
    if (files != null) {
      for (File file : files) {
        final String url = VfsUtil.getUrlForLibraryRoot(file);
        final VirtualFile zipRoot = VirtualFileManager.getInstance().findFileByUrl(url);
        if (zipRoot != null && zipRoot.isDirectory()) {
          String fileName = file.getName();
          final String srcDirName = StringUtil.trimEnd(fileName, "-src.zip");
          final VirtualFile sourcesDir = zipRoot.findFileByRelativePath(srcDirName + "/src/java");
          if (sourcesDir != null) {
            roots.add(sourcesDir);
          }
          else {
            roots.add(zipRoot);
          }
        }
      }
    }
    return VfsUtil.toVirtualFileArray(roots);
  }

  public String getLibUserDirectoryPath() {
    return myHomePath + "/lib/user";
  }

  private Map<String, Set<String>> loadBlackList() throws IOException {
    final InputStream stream = getClass().getResourceAsStream("/data/methodsBlacklist.txt");
    LOG.assertTrue(stream != null, "/data/methodsBlacklist.txt not found");
    final THashMap<String, Set<String>> map = new THashMap<String, Set<String>>();
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        final int i = line.indexOf(':');
        String className = line.substring(0, i);
        String methods = line.substring(i + 1);
        map.put(className, new THashSet<String>(StringUtil.split(methods, ",")));
      }
    }
    finally {
      reader.close();
    }
    return map;
  }

  public String getAgentPath() {
    return myHomePath + "/lib/agent/appengine-agent.jar";
  }
}
