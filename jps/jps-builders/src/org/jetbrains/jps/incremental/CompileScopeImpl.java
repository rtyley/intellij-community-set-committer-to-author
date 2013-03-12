/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental;

import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.ModuleBasedBuildTargetType;
import org.jetbrains.jps.builders.ModuleBasedTarget;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author nik
 */
public class CompileScopeImpl extends CompileScope {
  private final Collection<? extends BuildTargetType<?>> myTypes;
  private final Collection<BuildTargetType<?>> myTypesToForceBuild;
  private final Collection<BuildTarget<?>> myTargets;
  private final Map<BuildTarget<?>, Set<File>> myFiles;

  public CompileScopeImpl(Collection<? extends BuildTargetType<?>> types,
                          Collection<? extends BuildTargetType<?>> typesToForceBuild,
                          Collection<BuildTarget<?>> targets,
                          Map<BuildTarget<?>, Set<File>> files) {
    myTypes = types;
    myTypesToForceBuild = new HashSet<BuildTargetType<?>>();
    boolean forceBuildAllModuleBasedTargets = false;
    for (BuildTargetType<?> type : typesToForceBuild) {
      myTypesToForceBuild.add(type);
      forceBuildAllModuleBasedTargets |= type instanceof JavaModuleBuildTargetType;
    }
    if (forceBuildAllModuleBasedTargets) {
      for (BuildTargetType<?> targetType : TargetTypeRegistry.getInstance().getTargetTypes()) {
        if (targetType instanceof ModuleBasedBuildTargetType<?>) {
          myTypesToForceBuild.add(targetType);
        }
      }
    }
    myTargets = targets;
    myFiles = files;
  }

  @Override
  public boolean isAffected(@NotNull BuildTarget<?> target) {
    return myTypes.contains(target.getTargetType()) || myTargets.contains(target) || myFiles.containsKey(target) || isAffectedByAssociatedModule(target);
  }

  @Override
  public boolean isRecompilationForced(@NotNull BuildTarget<?> target) {
    BuildTargetType<?> type = target.getTargetType();
    return myTypesToForceBuild.contains(type) && (myTypes.contains(type) || myTargets.contains(target) || isAffectedByAssociatedModule(target));
  }

  @Override
  public boolean isRecompilationForcedForAllTargets(@NotNull BuildTargetType<?> targetType) {
    return myTypesToForceBuild.contains(targetType) && myTypes.contains(targetType);
  }

  @Override
  public boolean isRecompilationForcedForTargetsOfType(@NotNull BuildTargetType<?> targetType) {
    return myTypesToForceBuild.contains(targetType);
  }

  @Override
  public boolean isAffected(BuildTarget<?> target, @NotNull File file) {
    if (myFiles.isEmpty()) {//optimization
      return true;
    }
    if (myTypes.contains(target.getTargetType()) || myTargets.contains(target) || isAffectedByAssociatedModule(target)) {
      return true;
    }
    final Set<File> files = myFiles.get(target);
    return files != null && files.contains(file);
  }

  private boolean isAffectedByAssociatedModule(BuildTarget<?> target) {
    final JpsModule module = target instanceof ModuleBasedTarget ? ((ModuleBasedTarget)target).getModule() : null;
    if (module != null) {
      // this target is associated with module
      for (JavaModuleBuildTargetType moduleType : JavaModuleBuildTargetType.ALL_TYPES) {
        if (myTypes.contains(moduleType) || myTargets.contains(new ModuleBuildTarget(module, moduleType))) {
          return true;
        }
      }
    }
    return false;
  }

}
