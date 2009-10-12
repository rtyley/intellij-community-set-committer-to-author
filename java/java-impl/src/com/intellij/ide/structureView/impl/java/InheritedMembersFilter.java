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
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.FileStructureFilter;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class InheritedMembersFilter implements FileStructureFilter {
  @NonNls public static final String ID = "SHOW_INHERITED";

  public boolean isVisible(TreeElement treeNode) {
    if (treeNode instanceof JavaClassTreeElementBase) {
      return !((JavaClassTreeElementBase)treeNode).isInherited();
    }
    else {
      return true;
    }
  }

  @NotNull
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(IdeBundle.message("action.structureview.show.inherited"), null, IconLoader.getIcon("/hierarchy/supertypes.png"));
  }

  @NotNull
  public String getName() {
    return ID;
  }

  public boolean isReverted() {
    return true;
  }

  public String getCheckBoxText() {
    return IdeBundle.message("file.structure.toggle.show.inherited");
  }

  public Shortcut[] getShortcut() {
    return KeymapManager.getInstance().getActiveKeymap().getShortcuts("FileStructurePopup");
  }
}
