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
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.PsiLocation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 3/16/13 1:54 PM
 */
public class GradleTaskLocation extends PsiLocation<PsiFile> {
  
  @NotNull private final List<String> myTasks;

  public GradleTaskLocation(@NotNull Project p, @NotNull PsiFile file, @NotNull List<String> tasks) {
    super(p, file);
    myTasks = tasks;
  }
  
  @NotNull
  public List<String> getTasks() {
    return myTasks;
  }
}