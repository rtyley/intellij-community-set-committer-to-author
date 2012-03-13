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
package org.jetbrains.android.facet;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidFacetConfiguration implements FacetConfiguration {
  @NonNls private static final String RES_OVERLAY_FOLDERS_ELEMENT_NAME = "resOverlayFolders";
  @NonNls private static final String PATH_ELEMENT_NAME = "path";

  public String GEN_FOLDER_RELATIVE_PATH_APT = "/" + SdkConstants.FD_GEN_SOURCES;
  public String GEN_FOLDER_RELATIVE_PATH_AIDL = "/" + SdkConstants.FD_GEN_SOURCES;

  public String MANIFEST_FILE_RELATIVE_PATH = "/" + SdkConstants.FN_ANDROID_MANIFEST_XML;

  public String RES_FOLDER_RELATIVE_PATH = "/" + SdkConstants.FD_RES;
  public String ASSETS_FOLDER_RELATIVE_PATH = "/" + SdkConstants.FD_ASSETS;
  public String LIBS_FOLDER_RELATIVE_PATH = "/" + SdkConstants.FD_NATIVE_LIBS;

  public List<String> RES_OVERLAY_FOLDERS = Arrays.asList("/res-overlay");

  public boolean REGENERATE_R_JAVA = true;

  public boolean REGENERATE_JAVA_BY_AIDL = true;

  public boolean USE_CUSTOM_APK_RESOURCE_FOLDER = false;
  public String CUSTOM_APK_RESOURCE_FOLDER = "";

  public boolean USE_CUSTOM_COMPILER_MANIFEST = false;
  public String CUSTOM_COMPILER_MANIFEST = "";

  public String APK_PATH = "";

  public boolean LIBRARY_PROJECT = false;

  public boolean RUN_PROCESS_RESOURCES_MAVEN_TASK = true;

  public boolean GENERATE_UNSIGNED_APK = false;

  public String CUSTOM_DEBUG_KEYSTORE_PATH = "";

  public boolean PACK_TEST_CODE = false;

  public boolean RUN_PROGUARD = false;
  public String PROGUARD_CFG_PATH = "/" + AndroidCompileUtil.PROGUARD_CFG_FILE_NAME;

  private AndroidFacet myFacet = null;

  public void init(@NotNull Module module, @NotNull VirtualFile contentRoot) {
    init(module, contentRoot.getPath());
  }

  public void init(@NotNull Module module, @NotNull String baseDirectoryPath) {
    String moduleDirPath = AndroidRootUtil.getModuleDirPath(module);
    if (moduleDirPath == null) {
      return;
    }
    if (moduleDirPath.equals(baseDirectoryPath)) {
      return;
    }

    String s = FileUtil.getRelativePath(moduleDirPath, baseDirectoryPath, '/');

    GEN_FOLDER_RELATIVE_PATH_APT = '/' + s + GEN_FOLDER_RELATIVE_PATH_APT;
    GEN_FOLDER_RELATIVE_PATH_AIDL = '/' + s + GEN_FOLDER_RELATIVE_PATH_AIDL;
    MANIFEST_FILE_RELATIVE_PATH = '/' + s + MANIFEST_FILE_RELATIVE_PATH;
    RES_FOLDER_RELATIVE_PATH = '/' + s + RES_FOLDER_RELATIVE_PATH;
    ASSETS_FOLDER_RELATIVE_PATH = '/' + s + ASSETS_FOLDER_RELATIVE_PATH;
    LIBS_FOLDER_RELATIVE_PATH = '/' + s + LIBS_FOLDER_RELATIVE_PATH;

    for (int i = 0; i < RES_OVERLAY_FOLDERS.size(); i++) {
      RES_OVERLAY_FOLDERS.set(i, '/' + s + RES_OVERLAY_FOLDERS.get(i));
    }
  }

  @Nullable
  public AndroidPlatform getAndroidPlatform() {
    Sdk moduleSdk = ModuleRootManager.getInstance(myFacet.getModule()).getSdk();
    if (moduleSdk != null && moduleSdk.getSdkType().equals(AndroidSdkType.getInstance())) {
      AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)moduleSdk.getSdkAdditionalData();
      return data != null ? data.getAndroidPlatform() : null;
    }
    return null;
  }

  @Nullable
  public AndroidSdkData getAndroidSdk() {
    AndroidPlatform platform = getAndroidPlatform();
    return platform != null ? platform.getSdkData() : null;
  }

  @Nullable
  public IAndroidTarget getAndroidTarget() {
    AndroidPlatform platform = getAndroidPlatform();
    return platform != null ? platform.getTarget() : null;
  }

  public void setFacet(@NotNull AndroidFacet facet) {
    this.myFacet = facet;
    facet.androidPlatformChanged();
  }

  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    return new FacetEditorTab[]{new AndroidFacetEditorTab(editorContext, this)};
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    readResOverlayFolders(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    writeResOverlayFolders(element);
  }

  private void readResOverlayFolders(final Element element) throws InvalidDataException {
    final List<String> resOverlayFolders = new ArrayList<String>();
    final Element resOverlayFoldersElement = element.getChild(RES_OVERLAY_FOLDERS_ELEMENT_NAME);

    if (resOverlayFoldersElement != null) {
      //noinspection unchecked
      for (Element conditionalCompilerDefinitionElement : (Iterable<Element>)resOverlayFoldersElement.getChildren(PATH_ELEMENT_NAME)) {
        resOverlayFolders.add(conditionalCompilerDefinitionElement.getValue());
      }
    }
    RES_OVERLAY_FOLDERS = resOverlayFolders;
  }

  private void writeResOverlayFolders(final Element element) throws WriteExternalException {
    final Element resOverlayFoldersElement = new Element(RES_OVERLAY_FOLDERS_ELEMENT_NAME);

    for (String resOverlayFolderPath : RES_OVERLAY_FOLDERS) {
      final Element pathElement = new Element(PATH_ELEMENT_NAME);
      pathElement.setText(resOverlayFolderPath);
      resOverlayFoldersElement.addContent(pathElement);
    }
    element.addContent(resOverlayFoldersElement);
  }
}
