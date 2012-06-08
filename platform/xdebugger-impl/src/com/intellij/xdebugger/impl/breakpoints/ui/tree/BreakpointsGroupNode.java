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
package com.intellij.xdebugger.impl.breakpoints.ui.tree;

import com.intellij.ui.CheckedTreeNode;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup;

class BreakpointsGroupNode<G extends XBreakpointGroup> extends CheckedTreeNode {
  private final G myGroup;
  private final int myLevel;

  BreakpointsGroupNode(G group, int level) {
    super(group);
    myLevel = level;
    setChecked(false);
    myGroup = group;
  }

  public G getGroup() {
    return myGroup;
  }

  public int getLevel() {
    return myLevel;
  }
}
