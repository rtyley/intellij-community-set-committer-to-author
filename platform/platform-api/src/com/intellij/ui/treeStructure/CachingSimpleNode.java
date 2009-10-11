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
package com.intellij.ui.treeStructure;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;

public abstract class CachingSimpleNode extends SimpleNode {

  SimpleNode[] myChildren;

  protected CachingSimpleNode() {
  }

  protected CachingSimpleNode(SimpleNode aParent) {
    super(aParent);
  }

  protected CachingSimpleNode(Project aProject, NodeDescriptor aParentDescriptor) {
    super(aProject, aParentDescriptor);
  }

  public final SimpleNode[] getChildren() {
    if (myChildren == null) {
      myChildren = buildChildren();
    }

    return myChildren;
  }

  protected abstract SimpleNode[] buildChildren();

  public void cleanUpCache() {
    myChildren = null;
  }

  SimpleNode[] getCached() {
    return myChildren;
  }

}
