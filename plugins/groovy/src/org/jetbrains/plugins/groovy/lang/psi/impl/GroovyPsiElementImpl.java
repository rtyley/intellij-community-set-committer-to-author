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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

/**
 * @author ilyas
 */
public abstract class GroovyPsiElementImpl extends ASTWrapperPsiElement implements GroovyPsiElement {

  public GroovyPsiElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitElement(this);
  }

  public void acceptChildren(GroovyElementVisitor visitor) {
    acceptGroovyChildren(this, visitor);
  }

  public static void acceptGroovyChildren(PsiElement parent, GroovyElementVisitor visitor) {
    PsiElement child = parent.getFirstChild();
    while (child != null) {
      if (child instanceof GroovyPsiElement) {
        ((GroovyPsiElement) child).accept(visitor);
      }

      child = child.getNextSibling();
    }
  }

  public void removeElements(PsiElement[] elements) throws IncorrectOperationException {
    ASTNode parentNode = getNode();
    for (PsiElement element : elements) {
      if (element.isValid()) {
        ASTNode node = element.getNode();
        if (node == null || node.getTreeParent() != parentNode) {
          throw new IncorrectOperationException();
        }
        parentNode.removeChild(node);
      }
    }
  }

  public void removeStatement() throws IncorrectOperationException {
    if (getParent() == null ||
        getParent().getNode() == null) {
      throw new IncorrectOperationException();
    }
    ASTNode parentNode = getParent().getNode();
    ASTNode prevNode = getNode().getTreePrev();
    parentNode.removeChild(this.getNode());
    if (prevNode != null && TokenSets.SEPARATORS.contains(prevNode.getElementType())) {
      parentNode.removeChild(prevNode);
    }
  }

  public <T extends GrStatement> T replaceWithStatement(@NotNull T newStmt) {
    PsiElement parent = getParent();
    if (parent == null) {
      throw new PsiInvalidElementAccessException(this);
    }
    return (T)replace(newStmt);
  }

  /*
  public <T extends GroovyPsiElement> Iterable<T> childrenOfType(final TokenSet tokSet) {
    return new Iterable<T>() {

      public Iterator<T> iterator() {
        return new Iterator<T>() {
          private ASTNode findChild(ASTNode child) {
            if (child == null) return null;

            if (tokSet.contains(child.getElementType())) return child;

            return findChild(child.getTreeNext());
          }

          PsiElement first = getFirstChild();

          ASTNode n = first == null ? null : findChild(first.getNode());

          public boolean hasNext() {
            return n != null;
          }

          public T next() {
            if (n == null) return null;
            else {
              final ASTNode res = n;
              n = findChild(n.getTreeNext());
              return (T) res.getPsi();
            }
          }

          public void remove() {
          }
        };
      }
    };
  }
  */

}

