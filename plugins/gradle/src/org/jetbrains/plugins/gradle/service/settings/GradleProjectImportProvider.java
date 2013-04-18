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
package org.jetbrains.plugins.gradle.service.settings;

import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * @author Denis Zhdanov
 * @since 4/15/13 2:27 PM
 */
public class GradleProjectImportProvider extends AbstractExternalProjectImportProvider {

  public GradleProjectImportProvider(GradleProjectImportBuilder builder) {
    super(builder);
  }

  @Override
  protected boolean canImportFromFile(VirtualFile file) {
    return GradleConstants.EXTENSION.equals(file.getExtension());
  }

  @Nullable
  @Override
  public String getFileSample() {
    return "<b>Gradle</b> build script (*.gradle)";
  }
}
