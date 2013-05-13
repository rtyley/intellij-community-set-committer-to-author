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
package com.intellij.openapi.externalSystem.settings;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

/**
 * Holds settings specific to a particular project imported from an external system.
 *
 * @author Denis Zhdanov
 * @since 4/24/13 11:41 AM
 */
public abstract class ExternalProjectSettings implements Comparable<ExternalProjectSettings>, Cloneable {

  private String  myExternalProjectPath;
  private boolean myUseAutoImport;

  public String getExternalProjectPath() {
    return myExternalProjectPath;
  }

  public void setExternalProjectPath(@NotNull String externalProjectPath) {
    myExternalProjectPath = externalProjectPath;
  }

  public boolean isUseAutoImport() {
    return myUseAutoImport;
  }

  public void setUseAutoImport(boolean useAutoImport) {
    myUseAutoImport = useAutoImport;
  }

  @Override
  public int compareTo(@NotNull ExternalProjectSettings that) {
    return Comparing.compare(myExternalProjectPath, that.myExternalProjectPath);
  }

  @Override
  public int hashCode() {
    return myExternalProjectPath != null ? myExternalProjectPath.hashCode() : 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExternalProjectSettings that = (ExternalProjectSettings)o;

    return myExternalProjectPath == null ? that.myExternalProjectPath == null : myExternalProjectPath.equals(that.myExternalProjectPath);
  }

  @Override
  public String toString() {
    return myExternalProjectPath;
  }

  @NotNull
  public abstract ExternalProjectSettings clone();

  protected void copyTo(@NotNull ExternalProjectSettings receiver) {
    receiver.myExternalProjectPath = myExternalProjectPath;
    receiver.myUseAutoImport = myUseAutoImport;
  }
}
