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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.util.IncorrectOperationException;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @autor: ilyas
 */
public class GrWhileStatementImpl extends GroovyPsiElementImpl implements GrWhileStatement {
  public GrWhileStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitWhileStatement(this);
  }

  public String toString() {
    return "WHILE statement";
  }

  public GrCondition getCondition() {
    PsiElement lParenth = getLParenth();

    if (lParenth == null) return null;
    PsiElement afterLParen = lParenth.getNextSibling();

    if (afterLParen instanceof GrCondition) return ((GrCondition) afterLParen);

    return null;
  }

  public GrStatement getBody() {
    GrStatement[] statements = findChildrenByClass(GrStatement.class);

    if (getCondition() == null && statements.length > 0) return statements[0];
    else if (statements.length > 1 && (statements[1] instanceof GrStatement)) return statements[1];

    return null;
  }

  public GrCondition replaceBody(GrCondition newBody) throws IncorrectOperationException {
    if (getBody() == null ||
        newBody == null) {
      throw new IncorrectOperationException();
    }
    ASTNode oldBodyNode = getBody().getNode();
    if (oldBodyNode.getTreePrev() != null &&
        GroovyTokenTypes.mNLS.equals(oldBodyNode.getTreePrev().getElementType())) {
      ASTNode whiteNode = GroovyPsiElementFactory.getInstance(getProject()).createWhiteSpace().getNode();
      getNode().replaceChild(oldBodyNode.getTreePrev(), whiteNode);
    }
    getNode().replaceChild(oldBodyNode, newBody.getNode());
    ASTNode newNode = newBody.getNode();
    if (!(newNode.getPsi() instanceof GrCondition)) {
      throw new IncorrectOperationException();
    }
    return (GrCondition) newNode.getPsi();
  }

  public PsiElement getRParenth() {
    return findChildByType(GroovyTokenTypes.mRPAREN);
  }

  public PsiElement getLParenth() {
    return findChildByType(GroovyTokenTypes.mLPAREN);
  }

}
