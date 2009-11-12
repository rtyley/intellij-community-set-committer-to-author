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
package com.intellij.packaging.impl.compiler;

import com.intellij.compiler.impl.AdditionalCompileScopeProvider;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerFilter;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author nik
 */
public class ArtifactAdditionalCompileScopeProvider extends AdditionalCompileScopeProvider {
  @Override
  public CompileScope getAdditionalScope(@NotNull CompileScope baseScope, @NotNull CompilerFilter filter, @NotNull Project project) {
    if (ArtifactCompileScope.getArtifacts(baseScope) != null || !filter.acceptCompiler(IncrementalArtifactsCompiler.getInstance(project))) {
      return null;
    }
    final Set<Artifact> artifacts = ArtifactCompileScope.getArtifactsToBuild(project, baseScope);
    return ArtifactCompileScope.createScopeForModulesInArtifacts(project, artifacts);
  }
}
