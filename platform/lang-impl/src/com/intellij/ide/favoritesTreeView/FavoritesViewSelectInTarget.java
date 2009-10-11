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

package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInManager;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;

/**
 * User: anna
 * Date: Feb 25, 2005
 */
public class FavoritesViewSelectInTarget extends ProjectViewSelectInTarget {
  public FavoritesViewSelectInTarget(final Project project) {
    super(project);
  }

  public String toString() {
    return SelectInManager.FAVORITES;
  }

  protected boolean canSelect(final PsiFileSystemItem file) {
    if (!super.canSelect(file)) return false;
    return findSuitableFavoritesList(file.getVirtualFile(), myProject, null) != null;
  }

  public static String findSuitableFavoritesList(VirtualFile file, Project project, final String currentSubId) {
    final FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
    if (currentSubId != null && favoritesManager.contains(currentSubId, file)) return currentSubId;
    final String[] lists = favoritesManager.getAvailableFavoritesLists();
    for (String name : lists) {
      if (favoritesManager.contains(name, file)) return name;
    }
    return null;
  }

  public String getMinorViewId() {
    return FavoritesProjectViewPane.ID;
  }

  public float getWeight() {
    return StandardTargetWeights.FAVORITES_WEIGHT;
  }

  protected boolean canWorkWithCustomObjects() {
    return false;
  }

  public boolean isSubIdSelectable(String subId, SelectInContext context) {
    final FavoritesManager favoritesManager = FavoritesManager.getInstance(myProject);
    return favoritesManager.contains(subId, context.getVirtualFile());
  }
}
