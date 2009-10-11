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

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Ref;
import com.intellij.util.diff.FlyweightCapableTreeStructure;

/**
 * @author max
 */
public class ASTStructure implements FlyweightCapableTreeStructure<ASTNode> {
  private final ASTNode myRoot;

  public ASTStructure(final ASTNode root) {
    myRoot = root;
  }

  public ASTNode prepareForGetChildren(final ASTNode astNode) {
    return astNode;
  }

  public ASTNode getRoot() {
    return myRoot;
  }

  public void disposeChildren(final ASTNode[] nodes, final int count) {
  }

  public int getChildren(final ASTNode astNode, final Ref<ASTNode[]> into) {
    ASTNode child = astNode.getFirstChildNode();
    if (child == null) return 0;

    ASTNode[] store = into.get();
    if (store == null) {
      store = new ASTNode[10];
      into.set(store);
    }

    int count = 0;
    while (child != null) {
      if (count >= store.length) {
        ASTNode[] newStore = new ASTNode[count * 3 / 2];
        System.arraycopy(store, 0, newStore, 0, count);
        into.set(newStore);
        store = newStore;
      }
      store[count++] = child;
      child = child.getTreeNext();
    }

    return count;
  }
}
