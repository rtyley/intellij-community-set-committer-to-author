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
package com.intellij.util.download;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.net.URL;

/**
 * @author nik
 */
public abstract class DownloadableFileService {
  public static DownloadableFileService getInstance() {
    return ServiceManager.getService(DownloadableFileService.class);
  }

  @NotNull
  public abstract DownloadableFileDescription createFileDescription(@NotNull String downloadUrl, @NotNull String fileName);

  @NotNull
  public abstract DownloadableFileSetVersions<DownloadableFileSetDescription> createFileSetVersions(@NotNull String groupId, @NotNull URL... localUrls);

  public abstract void loadVersionsToCombobox(@NotNull DownloadableFileSetVersions<?> versions, @NotNull JComboBox comboBox);
}
