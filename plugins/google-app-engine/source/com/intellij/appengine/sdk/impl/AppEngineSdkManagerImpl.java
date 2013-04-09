package com.intellij.appengine.sdk.impl;

import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.sdk.AppEngineSdkManager;
import com.intellij.appengine.server.integration.AppEngineServerData;
import com.intellij.appengine.server.integration.AppEngineServerIntegration;
import com.intellij.javaee.appServerIntegrations.ApplicationServer;
import com.intellij.javaee.serverInstances.ApplicationServersManager;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class AppEngineSdkManagerImpl extends AppEngineSdkManager {
  private final Map<String, AppEngineSdkImpl> myPath2Sdk = new THashMap<String, AppEngineSdkImpl>();

  @NotNull
  @Override
  public AppEngineSdk findSdk(@NotNull String sdkPath) {
    sdkPath = StringUtil.trimEnd(sdkPath, "/");
    if (!myPath2Sdk.containsKey(sdkPath)) {
      myPath2Sdk.put(sdkPath, new AppEngineSdkImpl(sdkPath));
    }
    return myPath2Sdk.get(sdkPath);
  }

  @NotNull
  @Override
  public List<? extends AppEngineSdk> getValidSdks() {
    final List<ApplicationServer> servers = ApplicationServersManager.getInstance().getApplicationServers(AppEngineServerIntegration.getInstance());
    List<AppEngineSdk> sdkList = new ArrayList<AppEngineSdk>();
    for (ApplicationServer server : servers) {
      final AppEngineSdk sdk = ((AppEngineServerData)server.getPersistentData()).getSdk();
      if (sdk.isValid()) {
        sdkList.add(sdk);
      }
    }
    return sdkList;
  }
}
