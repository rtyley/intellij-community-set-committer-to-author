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

package org.jetbrains.android.run;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.sdklib.internal.avd.AvdManager;
import com.intellij.CommonBundle;
import com.intellij.diagnostic.logging.LogConsole;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.GenericAttributeValue;
import org.jdom.Element;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.logcat.AndroidLogFilterModel;
import org.jetbrains.android.logcat.AndroidLogcatFiltersPreferences;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdk;
import org.jetbrains.android.sdk.AndroidSdkImpl;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 27, 2009
 * Time: 2:20:54 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class AndroidRunConfigurationBase extends ModuleBasedConfiguration<JavaRunConfigurationModule> {
  @NonNls private static final String ANDROID_TARGET_DEVICES_PROPERTY = "AndroidTargetDevices";

  public boolean CHOOSE_DEVICE_MANUALLY = false;
  public String PREFERRED_AVD = "";
  public String COMMAND_LINE = "";
  public boolean WIPE_USER_DATA = false;
  public boolean DISABLE_BOOT_ANIMATION = false;
  public String NETWORK_SPEED = "full";
  public String NETWORK_LATENCY = "none";
  public boolean CLEAR_LOGCAT = false;

  public AndroidRunConfigurationBase(final String name, final Project project, final ConfigurationFactory factory) {
    super(name, new JavaRunConfigurationModule(project, false), factory);
  }

  @Override
  public final void checkConfiguration() throws RuntimeConfigurationException {
    JavaRunConfigurationModule configurationModule = getConfigurationModule();
    configurationModule.checkForWarning();
    Module module = configurationModule.getModule();
    if (module == null) return;
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      throw new RuntimeConfigurationError(AndroidBundle.message("android.no.facet.error"));
    }
    if (facet.getConfiguration().getAndroidPlatform() == null) {
      throw new RuntimeConfigurationError(AndroidBundle.message("select.platform.error"));
    }
    if (facet.getManifest() == null) {
      throw new RuntimeConfigurationError(AndroidBundle.message("android.manifest.not.found.error"));
    }
    if (PREFERRED_AVD.length() > 0) {
      AvdManager avdManager = facet.getAvdManagerSilently();
      if (avdManager == null) {
        AndroidSdk sdk = facet.getConfiguration().getAndroidSdk();
        if (sdk instanceof AndroidSdkImpl) {
          throw new RuntimeConfigurationError(AndroidBundle.message("avd.cannot.be.loaded.error"));
        }
      }
      if (avdManager != null) {
        AvdManager.AvdInfo avdInfo = avdManager.getAvd(PREFERRED_AVD, false);
        if (avdInfo == null) {
          throw new RuntimeConfigurationError(AndroidBundle.message("avd.not.found.error", PREFERRED_AVD));
        }
        if (!facet.isCompatibleAvd(avdInfo)) {
          throw new RuntimeConfigurationError(AndroidBundle.message("avd.not.compatible.error", PREFERRED_AVD));
        }
        if (avdInfo.getStatus() != AvdManager.AvdInfo.AvdStatus.OK) {
          throw new RuntimeConfigurationError(AndroidBundle.message("avd.not.valid.error", PREFERRED_AVD));
        }
      }
    }
    checkConfiguration(facet);
  }

  protected abstract void checkConfiguration(@NotNull AndroidFacet facet) throws RuntimeConfigurationException;

  public Collection<Module> getValidModules() {
    final List<Module> result = new ArrayList<Module>();
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    for (Module module : modules) {
      if (AndroidFacet.getInstance(module) != null) {
        result.add(module);
      }
    }
    return result;
  }

  private static boolean fillRuntimeAndTestDependencies(@NotNull Module module, @NotNull Map<Module, String> module2PackageName) {
    for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;
        Module depModule = moduleOrderEntry.getModule();
        if (depModule != null && !module2PackageName.containsKey(depModule)) {
          AndroidFacet depFacet = AndroidFacet.getInstance(depModule);
          if (depFacet != null && !depFacet.getConfiguration().LIBRARY_PROJECT && moduleOrderEntry.getScope() != DependencyScope.COMPILE) {
            String packageName = getPackageName(depFacet);
            if (packageName == null) {
              return false;
            }
            module2PackageName.put(depModule, packageName);
            if (!fillRuntimeAndTestDependencies(depModule, module2PackageName)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  public RunProfileState getState(@NotNull final Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    final Module module = getConfigurationModule().getModule();
    if (module == null) {
      throw new ExecutionException("Module is not found");
    }
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      throw new ExecutionException(AndroidBundle.message("no.facet.error", module.getName()));
    }
    AndroidFacetConfiguration configuration = facet.getConfiguration();
    AndroidPlatform platform = configuration.getAndroidPlatform();
    Project project = module.getProject();
    if (platform == null) {
      Messages.showErrorDialog(project, AndroidBundle.message("specify.platform.error"), CommonBundle.getErrorTitle());
      ModulesConfigurator.showFacetSettingsDialog(facet, null);
      return null;
    }
    else {
      String aPackage = getPackageName(facet);
      if (aPackage == null) return null;

      Map<Module, String> depModule2PackageName = new HashMap<Module, String>();
      if (!fillRuntimeAndTestDependencies(module, depModule2PackageName)) return null;

      if (platform.getSdk().getDebugBridge(project) == null) return null;
      String[] deviceSerialNumbers = ArrayUtil.EMPTY_STRING_ARRAY;
      if (CHOOSE_DEVICE_MANUALLY) {
        deviceSerialNumbers = chooseDevicesManually(facet);
        if (deviceSerialNumbers.length == 0) return null;
      }
      AndroidApplicationLauncher applicationLauncher = getApplicationLauncher(facet);
      if (applicationLauncher != null) {
        return new AndroidRunningState(env, facet, deviceSerialNumbers, PREFERRED_AVD.length() > 0 ? PREFERRED_AVD : null,
                                       computeCommandLine(), aPackage, applicationLauncher, depModule2PackageName) {

          @NotNull
          @Override
          protected ConsoleView attachConsole() throws ExecutionException {
            return AndroidRunConfigurationBase.this.attachConsole(this, executor);
          }
        };
      }
    }
    return null;
  }

  @Nullable
  private static String getPackageName(AndroidFacet facet) {
    Manifest manifest = facet.getManifest();
    if (manifest == null) return null;
    GenericAttributeValue<String> packageAttrValue = manifest.getPackage();
    String aPackage = packageAttrValue.getValue();
    if (aPackage == null || aPackage.length() == 0) {
      Project project = facet.getModule().getProject();
      Messages.showErrorDialog(project, AndroidBundle.message("specify.main.package.error", facet.getModule().getName()),
                               CommonBundle.getErrorTitle());
      XmlAttributeValue attrValue = packageAttrValue.getXmlAttributeValue();
      if (attrValue != null) {
        PsiNavigateUtil.navigate(attrValue);
      }
      else {
        PsiNavigateUtil.navigate(manifest.getXmlElement());
      }
      return null;
    }
    return aPackage;
  }

  private String computeCommandLine() {
    StringBuilder result = new StringBuilder();
    result.append("-netspeed ").append(NETWORK_SPEED).append(' ');
    result.append("-netdelay ").append(NETWORK_LATENCY).append(' ');
    if (WIPE_USER_DATA) {
      result.append("-wipe-data ");
    }
    if (DISABLE_BOOT_ANIMATION) {
      result.append("-no-boot-anim ");
    }
    result.append(COMMAND_LINE);
    int last = result.length() - 1;
    if (result.charAt(last) == ' ') {
      result.deleteCharAt(last);
    }
    return result.toString();
  }

  @NotNull
  protected abstract ConsoleView attachConsole(AndroidRunningState state, Executor executor) throws ExecutionException;

  @Nullable
  protected abstract AndroidApplicationLauncher getApplicationLauncher(AndroidFacet facet);

  protected abstract boolean supportMultipleDevices();

  private static String toString(String[] strs) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0, n = strs.length; i < n; i++) {
      builder.append(strs[i]);
      if (i < n - 1) {
        builder.append(' ');
      }
    }
    return builder.toString();
  }

  private static String[] fromString(String s) {
    return s.split(" ");
  }

  @NotNull
  private String[] chooseDevicesManually(@NotNull AndroidFacet facet) {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(getProject());
    String value = propertiesComponent.getValue(ANDROID_TARGET_DEVICES_PROPERTY);
    String[] selectedSerials = value != null ? fromString(value) : null;
    DeviceChooser chooser = new DeviceChooser(facet, supportMultipleDevices(), selectedSerials);
    chooser.show();
    IDevice[] devices = chooser.getSelectedDevices();
    if (chooser.getExitCode() != DeviceChooser.OK_EXIT_CODE || devices.length == 0) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    String[] serials = new String[devices.length];
    for (int i = 0; i < devices.length; i++) {
      serials[i] = devices[i].getSerialNumber();
    }
    propertiesComponent.setValue(ANDROID_TARGET_DEVICES_PROPERTY, toString(serials));
    return serials;
  }

  @Override
  public void customizeLogConsole(LogConsole console) {
    final Project project = getProject();
    console.setFilterModel(new AndroidLogFilterModel(AndroidLogcatFiltersPreferences.getInstance(project).TAB_LOG_LEVEL) {
      @Override
      protected void setCustomFilter(String filter) {
        AndroidLogcatFiltersPreferences.getInstance(project).TAB_CUSTOM_FILTER = filter;
      }

      @Override
      protected void saveLogLevel(Log.LogLevel logLevel) {
        AndroidLogcatFiltersPreferences.getInstance(project).TAB_LOG_LEVEL = logLevel.name();
      }

      @Override
      public String getCustomFilter() {
        return AndroidLogcatFiltersPreferences.getInstance(project).TAB_CUSTOM_FILTER;
      }
    });
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    readModule(element);
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    writeModule(element);
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
