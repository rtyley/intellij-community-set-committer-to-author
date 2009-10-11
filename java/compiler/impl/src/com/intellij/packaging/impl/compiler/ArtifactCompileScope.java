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

import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.compiler.impl.ProjectCompileScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.elements.ModuleOutputElementType;
import com.intellij.packaging.impl.elements.ModuleOutputPackagingElement;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.Collection;
import java.util.Arrays;

/**
 * @author nik
 */
public class ArtifactCompileScope {
  private static final Key<Artifact[]> ARTIFACTS_KEY = Key.create("artifacts");

  private ArtifactCompileScope() {
  }

  @NotNull
  public static ModuleCompileScope create(@NotNull Project project, @NotNull Artifact artifact) {
    final Set<Module> modules = new HashSet<Module>();
    final PackagingElementResolvingContext context = ArtifactManager.getInstance(project).getResolvingContext();
    ArtifactUtil.processPackagingElements(artifact, ModuleOutputElementType.MODULE_OUTPUT_ELEMENT_TYPE, new Processor<ModuleOutputPackagingElement>() {
      public boolean process(ModuleOutputPackagingElement moduleOutputPackagingElement) {
        final Module module = moduleOutputPackagingElement.findModule(context);
        if (module != null) {
          modules.add(module);
        }
        return true;
      }
    }, context, true);

    final ModuleCompileScope scope = new ModuleCompileScope(project, modules.toArray(new Module[modules.size()]), true);
    scope.putUserData(ARTIFACTS_KEY, new Artifact[]{artifact});
    return scope;
  }

  public static CompileScope create(@NotNull Project project, @NotNull Collection<Artifact> artifacts) {
    return create(new ProjectCompileScope(project), artifacts);
  }

  public static CompileScope create(final CompileScope baseScope, @NotNull Collection<Artifact> artifacts) {
    baseScope.putUserData(ARTIFACTS_KEY, artifacts.toArray(new Artifact[artifacts.size()]));
    return baseScope;
  }

  @Nullable
  public static Artifact[] getArtifacts(@NotNull CompileScope compileScope) {
    return compileScope.getUserData(ARTIFACTS_KEY);
  }

  public static Set<Artifact> getArtifactsToBuild(final Project project, final CompileScope compileScope) {
    final Artifact[] artifactsFromScope = getArtifacts(compileScope);
    if (artifactsFromScope != null) {
      return new HashSet<Artifact>(Arrays.asList(artifactsFromScope));
    }
    Set<Artifact> artifacts = new HashSet<Artifact>();
    for (Artifact artifact : ArtifactManager.getInstance(project).getArtifacts()) {
      if (artifact.isBuildOnMake()) {
        artifacts.add(artifact);
      }
    }
    return artifacts;
  }
}
