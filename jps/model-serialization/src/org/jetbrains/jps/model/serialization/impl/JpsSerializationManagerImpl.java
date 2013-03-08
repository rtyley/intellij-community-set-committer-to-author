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
package org.jetbrains.jps.model.serialization.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.serialization.JpsGlobalLoader;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;
import org.jetbrains.jps.model.serialization.JpsSerializationManager;

import java.io.IOException;
import java.util.Map;

/**
 * @author nik
 */
public class JpsSerializationManagerImpl extends JpsSerializationManager {
  @NotNull
  @Override
  public JpsModel loadModel(@NotNull String projectPath, @Nullable String optionsPath, @NotNull Map<String, String> pathVariables)
    throws IOException {
    JpsModel model = JpsElementFactory.getInstance().createModel();
    if (optionsPath != null) {
      JpsGlobalLoader.loadGlobalSettings(model.getGlobal(), pathVariables, optionsPath);
    }
    JpsProjectLoader.loadProject(model.getProject(), pathVariables, projectPath);
    return model;
  }
}
