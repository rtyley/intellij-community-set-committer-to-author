/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.projectRoots;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author Gregory.Shrago
 */
public class SimpleJavaSdkType extends SdkType implements JavaSdkType {
  // do not use javaw.exe for Windows because of issues with encoding
  @NonNls private static final String VM_EXE_NAME = "java";

  public SimpleJavaSdkType() {
    super("SimpleJavaSdkType");
  }

  public Sdk createJdk(final String jdkName, final String home) {
    final Sdk jdk = ProjectJdkTable.getInstance().createSdk(jdkName, this);
    SdkModificator sdkModificator = jdk.getSdkModificator();

    String path = home.replace(File.separatorChar, '/');
    sdkModificator.setHomePath(path);
    sdkModificator.commitChanges();
    return jdk;
  }

  public String getPresentableName() {
    return ProjectBundle.message("sdk.java.name");
  }

  public AdditionalDataConfigurable createAdditionalDataConfigurable(SdkModel sdkModel, SdkModificator sdkModificator) {
    return null;
  }

  public void saveAdditionalData(SdkAdditionalData additionalData, Element additional) {
  }

  public String getBinPath(Sdk sdk) {
    return getConvertedHomePath(sdk) + "bin";
  }

  @NonNls
  public String getToolsPath(Sdk sdk) {
    final String versionString = sdk.getVersionString();
    final boolean isJdk1_x = versionString != null && (versionString.contains("1.0") || versionString.contains("1.1"));
    return getConvertedHomePath(sdk) + "lib" + File.separator + (isJdk1_x? "classes.zip" : "tools.jar");
  }

  public String getVMExecutablePath(Sdk sdk) {
    return getBinPath(sdk) + File.separator + VM_EXE_NAME;
  }

  private static String getConvertedHomePath(Sdk sdk) {
    String path = sdk.getHomePath().replace('/', File.separatorChar);
    if (!path.endsWith(File.separator)) {
      path += File.separator;
    }
    return path;
  }

  public String suggestHomePath() {
    return null;
  }

  public boolean isValidSdkHome(String path) {
    return JdkUtil.checkForJdk(new File(path));
  }

  public String suggestSdkName(String currentSdkName, String sdkHome) {
    return currentSdkName;
  }


  public final String getVersionString(final String sdkHome) {
    return SdkVersionUtil.readVersionFromProcessOutput(sdkHome, new String[] {sdkHome + File.separator + "bin" + File.separator + "java",  "-version"}, "version");
  }

}
