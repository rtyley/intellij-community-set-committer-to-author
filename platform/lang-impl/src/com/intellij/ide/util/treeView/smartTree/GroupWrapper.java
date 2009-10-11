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

package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.project.Project;

import java.util.Collection;

class GroupWrapper extends CachingChildrenTreeNode<Group> {
  public GroupWrapper(Project project, Group value, TreeModel treeModel) {
    super(project, value, treeModel);
    clearChildren();
  }

  public void copyFromNewInstance(final CachingChildrenTreeNode newInstance) {
    clearChildren();
    setChildren(newInstance.getChildren());
    synchronizeChildren();
  }

  public void update(PresentationData presentation) {
    presentation.updateFrom(getValue().getPresentation());
  }

  public void initChildren() {
    clearChildren();
    Collection<TreeElement> children = getValue().getChildren();
    for (TreeElement child : children) {
      TreeElementWrapper childNode = new TreeElementWrapper(getProject(), child, myTreeModel);
      addSubElement(childNode);
    }
  }


  protected void performTreeActions() {
    filterChildren(myTreeModel.getFilters());
    groupChildren(myTreeModel.getGroupers());
    sortChildren(myTreeModel.getSorters());
  }
}
