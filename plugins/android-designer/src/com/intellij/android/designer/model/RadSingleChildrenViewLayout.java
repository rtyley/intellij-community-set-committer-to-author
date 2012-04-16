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
package com.intellij.android.designer.model;

import com.intellij.android.designer.designSurface.DropToOperation;
import com.intellij.android.designer.designSurface.TreeDropToOperation;
import com.intellij.designer.componentTree.TreeEditOperation;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.OperationContext;

/**
 * @author Alexander Lobas
 */
public class RadSingleChildrenViewLayout extends RadViewLayout {
  @Override
  public EditOperation processChildOperation(OperationContext context) {
    if (myContainer.getChildren().isEmpty() &&
        (context.isCreate() || context.isPaste() || context.isAdd()) &&
        context.getComponents().size() == 1) {
      if (context.isTree()) {
        if (TreeEditOperation.isTarget(myContainer, context)) {
          return new TreeDropToOperation(myContainer, context);
        }
        return null;
      }
      return new DropToOperation(myContainer, context);
    }

    return null;
  }
}