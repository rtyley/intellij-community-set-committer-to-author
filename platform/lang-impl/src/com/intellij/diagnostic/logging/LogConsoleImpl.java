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

package com.intellij.diagnostic.logging;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.Charset;

/**
 * User: anna
 * Date: Apr 19, 2005
 */
public abstract class LogConsoleImpl extends LogConsoleBase {
  private final String myPath;

  public LogConsoleImpl(Project project, File file, @NotNull Charset charset, long skippedContents, String title, final boolean buildInActions) {
    super(project, file, charset, skippedContents, title, buildInActions, new DefaultLogFilterModel(project));
    myPath = file.getAbsolutePath();
  }


  @Nullable
  public String getTooltip() {
    return myPath;
  }

  public String getPath() {
    return myPath;
  }

}
