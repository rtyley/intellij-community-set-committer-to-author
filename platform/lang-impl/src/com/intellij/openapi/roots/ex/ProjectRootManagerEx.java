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

package com.intellij.openapi.roots.ex;

import com.intellij.ide.caches.CacheUpdater;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.List;

public abstract class ProjectRootManagerEx extends ProjectRootManager {
  public static ProjectRootManagerEx getInstanceEx(Project project) {
    return (ProjectRootManagerEx)getInstance(project);
  }

  public abstract void registerRootsChangeUpdater(CacheUpdater updater);
  public abstract void unregisterRootsChangeUpdater(CacheUpdater updater);

  public abstract void registerRefreshUpdater(CacheUpdater updater);
  public abstract void unregisterRefreshUpdater(CacheUpdater updater);

  public abstract void addProjectJdkListener(ProjectJdkListener listener);

  public abstract void removeProjectJdkListener(ProjectJdkListener listener);

  // invokes runnable surrounded by beforeRootsChage()/rootsChanged() callbacks
  public abstract void makeRootsChange(@NotNull Runnable runnable, boolean filetypes, boolean fireEvents);

  public abstract void mergeRootsChangesDuring(@NotNull Runnable runnable);

  public abstract GlobalSearchScope getScopeForLibraryUsedIn(List<Module> modulesLibraryIsUsedIn);

  public abstract GlobalSearchScope getScopeForJdk(final JdkOrderEntry jdkOrderEntry);

  public abstract void clearScopesCachesForModules();


  public interface ProjectJdkListener extends EventListener {
    void projectJdkChanged();
  }
}
