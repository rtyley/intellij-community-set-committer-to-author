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

package com.intellij.ide.actions;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FilenameFilter;
import java.io.Serializable;
import java.util.Set;

/**
 * This class is serialized into StartupActionScript stream and must thus reside in bootstrap module.
 *
 * @author mike
 */
public class ImportSettingsFilenameFilter implements FilenameFilter, Serializable {
  private final Set<String> myRelativeNamesToExtract;
  @NonNls static final String SETTINGS_JAR_MARKER = "IntelliJ IDEA Global Settings";

  public ImportSettingsFilenameFilter(Set<String> relativeNamesToExtract) {
    myRelativeNamesToExtract = relativeNamesToExtract;
  }

  public boolean accept(File dir, String name) {
    if (name.equals(SETTINGS_JAR_MARKER)) return false;
    final File configPath = new File(PathManager.getConfigPath());
    final String rPath = FileUtil.getRelativePath(configPath, new File(dir, name));
    assert rPath != null;
    final String relativePath = FileUtil.toSystemIndependentName(rPath);
    for (final String allowedRelPath : myRelativeNamesToExtract) {
      if (relativePath.startsWith(allowedRelPath)) return true;
    }
    return false;
  }
}
