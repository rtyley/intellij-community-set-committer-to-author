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

/*
 * @author max
 */
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeUtil;

public class JavaHelper extends Helper {
  public JavaHelper(final FileType fileType, final Project project) {
    super(fileType, project);
  }

  protected int getIndentInner(final ASTNode element, final boolean includeNonSpace, final int recursionLevel) {
    if (recursionLevel > TOO_BIG_WALK_THRESHOULD) return 0;

    if (element.getTreePrev() != null) {
      ASTNode prev = element.getTreePrev();
      while (prev instanceof CompositeElement && !TreeUtil.isStrongWhitespaceHolder(prev.getElementType())) {
        ASTNode lastCompositePrev = prev;
        prev = prev.getLastChildNode();
        if (prev == null) { // element.prev is "empty composite"
          return getIndentInner(lastCompositePrev, includeNonSpace, recursionLevel + 1);
        }
      }

      String text = prev.getText();
      int index = Math.max(text.lastIndexOf('\n'), text.lastIndexOf('\r'));

      if (index >= 0) {
        return getIndent(text.substring(index + 1), includeNonSpace);
      }

      if (includeNonSpace) {
        return getIndentInner(prev, includeNonSpace, recursionLevel + 1) + getIndent(text, includeNonSpace);
      }

      if (element.getElementType() == ElementType.CODE_BLOCK) {
        ASTNode parent = element.getTreeParent();
        if (parent.getElementType() == ElementType.BLOCK_STATEMENT) {
          parent = parent.getTreeParent();
        }
        if (parent.getElementType() != ElementType.CODE_BLOCK) {
          //Q: use some "anchor" part of parent for some elements?
          // e.g. for method it could be declaration start, not doc-comment
          return getIndentInner(parent, includeNonSpace, recursionLevel + 1);
        }
      }
      else {
        if (element.getElementType() == ElementType.LBRACE) {
          return getIndentInner(element.getTreeParent(), includeNonSpace, recursionLevel + 1);
        }
      }
      //Q: any other cases?

      ASTNode parent = prev.getTreeParent();
      ASTNode child = prev;
      while (parent != null) {
        if (child.getTreePrev() != null) break;
        child = parent;
        parent = parent.getTreeParent();
      }

      if (parent == null) {
        return getIndent(text, includeNonSpace);
      }
      else {
        if (prev.getTreeParent().getElementType() == ElementType.LABELED_STATEMENT) {
          return getIndentInner(prev, true, recursionLevel + 1) + getIndent(text, true);
        }
        else
          return getIndentInner(prev, includeNonSpace, recursionLevel + 1);
      }
    }
    else {
      if (element.getTreeParent() == null) {
        return 0;
      }
      return getIndentInner(element.getTreeParent(), includeNonSpace, recursionLevel + 1);
    }
  }
}