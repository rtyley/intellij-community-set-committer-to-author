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
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Common base interface for quick fixes provided by local and global inspections.
 *
 * @author anna
 * @since 6.0
 * @see CommonProblemDescriptor#getFixes()
 */
public interface QuickFix<D extends CommonProblemDescriptor> {
  QuickFix[] EMPTY_ARRAY = new QuickFix[0];
  /**
   * Returns the name of the quick fix.
   *
   * @return the name of the quick fix.
   */
  @NotNull
  String getName();

  /**
   * To appear in "Apply Fix" statement when multiple Quick Fixes exist
   */
  @NotNull String getFamilyName();

  /**
   * Called to apply the fix.
   *
   * @param project    {@link com.intellij.openapi.project.Project}
   * @param descriptor problem reported by the tool which provided this quick fix action
   */
  void applyFix(@NotNull Project project, @NotNull D descriptor);
}
