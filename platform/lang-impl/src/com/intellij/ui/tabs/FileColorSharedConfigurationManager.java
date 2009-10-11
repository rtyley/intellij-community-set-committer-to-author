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

package com.intellij.ui.tabs;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.project.Project;
import com.intellij.ui.FileColorManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
@State(
  name="SharedFileColors",
  storages = {
    @Storage(id = "default", file = "$PROJECT_FILE$"),
    @Storage(id = "other", file = "$PROJECT_CONFIG_DIR$/fileColors.xml", scheme = StorageScheme.DIRECTORY_BASED)
  }
)
public class FileColorSharedConfigurationManager implements PersistentStateComponent<Element> {
  private Project myProject;

  public FileColorSharedConfigurationManager(@NotNull final Project project) {
    myProject = project;
  }

  public Element getState() {
    return ((FileColorManagerImpl)FileColorManager.getInstance(myProject)).getState(true);
  }

  public void loadState(Element state) {
    ((FileColorManagerImpl)FileColorManager.getInstance(myProject)).loadState(state, true);
  }
}
