/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.ide.util.treeView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractTreeStructure {
  public abstract Object getRootElement();
  public abstract Object[] getChildElements(Object element);
  @Nullable
  public abstract Object getParentElement(Object element);

  @NotNull
  public abstract NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor);

  public abstract void commit();
  public abstract boolean hasSomethingToCommit();

  public boolean isToBuildChildrenInBackground(Object element){
    return false;
  }

  public boolean isAlwaysLeaf() {
    return false;
  }

}