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
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.util.Comparing;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.*;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ModuleGroupItem extends PackagingSourceItem {
  private final String myGroupName;
  private final String[] myPath;

  public ModuleGroupItem(String[] path) {
    super(false);
    myGroupName = path[path.length - 1];
    myPath = path;
  }

  public boolean equals(Object obj) {
    return obj instanceof ModuleGroupItem && Comparing.equal(myPath, ((ModuleGroupItem)obj).myPath);
  }

  public int hashCode() {
    return Arrays.hashCode(myPath);
  }

  public SourceItemPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new ModuleGroupSourceItemPresentation(myGroupName);
  }

  @NotNull
  @Override
  public List<? extends PackagingElement<?>> createElements(@NotNull ArtifactEditorContext context) {
    return Collections.emptyList();
  }

  public String[] getPath() {
    return myPath;
  }

  private static class ModuleGroupSourceItemPresentation extends SourceItemPresentation {
    private final String myGroupName;

    public ModuleGroupSourceItemPresentation(String groupName) {
      myGroupName = groupName;
    }

    @Override
    public String getPresentableName() {
      return myGroupName;
    }

    @Override
    public void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes,
                       SimpleTextAttributes commentAttributes) {
      presentationData.setClosedIcon(Icons.CLOSED_MODULE_GROUP_ICON);
      presentationData.setOpenIcon(Icons.OPENED_MODULE_GROUP_ICON);
      presentationData.addText(myGroupName, mainAttributes);
    }

    @Override
    public int getWeight() {
      return SourceItemWeights.MODULE_GROUP_WEIGHT;
    }
  }
}
