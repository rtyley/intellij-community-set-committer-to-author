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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/15/12
 */
public abstract class CompileScope {
  public abstract boolean isAffected(BuildTarget<?> target, @NotNull File file);

  public abstract boolean isAffected(@NotNull BuildTarget<?> target);

  public abstract boolean isRecompilationForced(@NotNull BuildTarget<?> target);

  public abstract boolean isRecompilationForcedForAllTargets(@NotNull BuildTargetType<?> targetType);

  public abstract boolean isRecompilationForcedForTargetsOfType(@NotNull BuildTargetType<?> targetType);
}
