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

package com.intellij.ide.structureView;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Eugene Belyaev
 */
public abstract class StructureViewFactoryEx extends StructureViewFactory {

  @Nullable
  public abstract StructureViewWrapper getStructureViewWrapper();

  public abstract void registerExtension(Class<? extends PsiElement> type, StructureViewExtension extension);
  public abstract void unregisterExtension(Class<? extends PsiElement> type, StructureViewExtension extension);

  public abstract Collection<StructureViewExtension> getAllExtensions(Class<? extends PsiElement> type);

  public abstract void setActiveAction(final String name, final boolean state);

  public abstract boolean isActionActive(final String name);

  public static StructureViewFactoryEx getInstanceEx(final Project project) {
    return (StructureViewFactoryEx)getInstance(project);
  }

  public abstract void runWhenInitialized(Runnable runnable);
}