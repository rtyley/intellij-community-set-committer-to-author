/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 4/24/13 11:57 AM
 */
public class GradleProjectSettings extends ExternalProjectSettings {

  @Nullable private String  myGradleHome;
  private boolean myPreferLocalInstallationToWrapper;

  @Nullable
  public String getGradleHome() {
    return myGradleHome;
  }

  public void setGradleHome(@Nullable String gradleHome) {
    myGradleHome = gradleHome;
  }

  public boolean isPreferLocalInstallationToWrapper() {
    return myPreferLocalInstallationToWrapper;
  }

  public void setPreferLocalInstallationToWrapper(boolean preferLocalInstallationToWrapper) {
    myPreferLocalInstallationToWrapper = preferLocalInstallationToWrapper;
  }
}
