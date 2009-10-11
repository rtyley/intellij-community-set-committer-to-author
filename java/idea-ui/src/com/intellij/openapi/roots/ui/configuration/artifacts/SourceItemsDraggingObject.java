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
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.ArtifactEditorContext;

import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
public class SourceItemsDraggingObject extends PackagingElementDraggingObject {
  private final PackagingSourceItem[] mySourceItems;

  public SourceItemsDraggingObject(PackagingSourceItem[] sourceItems) {
    mySourceItems = sourceItems;
  }

  @Override
  public List<PackagingElement<?>> createPackagingElements(ArtifactEditorContext context) {
    final List<PackagingElement<?>> result = new ArrayList<PackagingElement<?>>();
    for (PackagingSourceItem item : mySourceItems) {
      result.addAll(item.createElements(context));
    }
    return result;
  }

}
