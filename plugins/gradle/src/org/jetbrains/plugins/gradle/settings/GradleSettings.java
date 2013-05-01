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
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Holds shared project-level gradle-related settings (should be kept at the '*.ipr' or under '.idea').
 * 
 * @author peter
 */
@State(
    name = "GradleSettings",
    storages = {
      @Storage(file = StoragePathMacros.PROJECT_FILE),
      @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/gradle.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class GradleSettings extends AbstractExternalSystemSettings<GradleProjectSettings, GradleSettingsListener>
  implements PersistentStateComponent<GradleSettings.MyState>
{

  private String  myServiceDirectoryPath;

  public GradleSettings(@NotNull Project project) {
    super(GradleSettingsListener.TOPIC, project);
  }

  @NotNull
  public static GradleSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleSettings.class);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public GradleSettings.MyState getState() {
    MyState state = new MyState();
    fillState(state);
    state.serviceDirectoryPath = myServiceDirectoryPath;
    return state;
  }

  @Override
  public void loadState(MyState state) {
    super.loadState(state);
    myServiceDirectoryPath = state.serviceDirectoryPath;
  }

  /**
   * @return    service directory path (if defined). 'Service directory' is a directory which is used internally by gradle during
   *            calls to the tooling api. E.g. it holds downloaded binaries (dependency jars). We allow to define it because there
   *            is a possible situation when a user wants to configure particular directory to be excluded from anti-virus protection
   *            in order to increase performance
   */
  @Nullable
  public String getServiceDirectoryPath() {
    return myServiceDirectoryPath;
  }

  public void setServiceDirectoryPath(@Nullable String path) {
    if (!Comparing.equal(myServiceDirectoryPath, path)) {
      final String oldPath = myServiceDirectoryPath;
      getPublisher().onServiceDirectoryPathChange(oldPath, path);
    } 
  }

  @Override
  protected void checkSettings(@NotNull GradleProjectSettings old, @NotNull GradleProjectSettings current) {
    if (!Comparing.equal(old.getGradleHome(), current.getGradleHome())) {
      getPublisher().onGradleHomeChange(old.getGradleHome(), current.getGradleHome(), current.getExternalProjectPath());
    }
    if (old.isPreferLocalInstallationToWrapper() != current.isPreferLocalInstallationToWrapper()) {
      getPublisher().onPreferLocalGradleDistributionToWrapperChange(current.isPreferLocalInstallationToWrapper(),
                                                                    current.getExternalProjectPath());
    }
  }

  public static class MyState implements State<GradleProjectSettings> {

    private Set<GradleProjectSettings> myProjectSettings = ContainerUtilRt.newTreeSet();
    public String serviceDirectoryPath;

    @AbstractCollection(surroundWithTag = false, elementTypes = {GradleProjectSettings.class})
    public Set<GradleProjectSettings> getLinkedExternalProjectsSettings() {
      return myProjectSettings;
    }

    public void setLinkedExternalProjectsSettings(Set<GradleProjectSettings> settings) {
      if (settings != null) {
        myProjectSettings.addAll(settings);
      }
    }
  }
}