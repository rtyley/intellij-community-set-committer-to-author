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

import java.util.ArrayList;
import java.util.List;

public abstract class SimpleMutableNode extends CachingSimpleNode {

  private final List myChildren = new ArrayList();

  public SimpleMutableNode() {
  }

  public SimpleMutableNode(SimpleNode aParent) {
    super(aParent);
  }

  public SimpleMutableNode(Project aProject, NodeDescriptor aParentDescriptor) {
    super(aProject, aParentDescriptor);
  }

  protected final SimpleNode[] buildChildren() {
    return (SimpleNode[]) myChildren.toArray(new SimpleNode[myChildren.size()]);
  }

  public final SimpleNode add(SimpleNode child) {
    myChildren.add(child);
    cleanUpCache();
    return child;
  }


  public final SimpleNode[] addAll(SimpleNode[] children) {
    for (int i = 0; i < children.length; i++) {
      add(children[i]);
    }

    return children;
  }

  public final void remove(SimpleNode child) {
    myChildren.remove(child);
    cleanUpCache();
  }

  public final void clear() {
    myChildren.clear();
    cleanUpCache();
  }
}
