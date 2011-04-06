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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author ilyas
 */
public class GrTraditionalForClauseImpl extends GroovyPsiElementImpl implements GrTraditionalForClause {
  public GrTraditionalForClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitTraditionalForClause(this);
  }

  public String toString() {
    return "Traditional FOR clause";
  }

  public GrVariable getDeclaredVariable() {
    return findChildByClass(GrParameter.class);
  }

  public GrCondition getInitialization() {
    final ASTNode first = getFirstSemicolon();
    for (ASTNode child = getNode().getFirstChildNode(); child != null && child != first; child = child.getTreeNext()) {
      if (child.getPsi() instanceof GrCondition) {
        return (GrCondition)child.getPsi();
      }
    }
    return null;
  }

  public GrExpression getCondition() {
    final ASTNode first = getFirstSemicolon();
    if (first == null) return null;
    for (ASTNode child = first.getTreeNext(); child != null; child = child.getTreeNext()) {
      if (child.getPsi() instanceof GrExpression) {
        return (GrExpression) child.getPsi();
      }
    }

    return null;
  }

  public GrExpression getUpdate() {
    final ASTNode second = getSecondSemicolon();
    if (second == null) return null;

    for (ASTNode child = second; child != null; child = child.getTreeNext()) {
      if (child.getPsi() instanceof GrExpression) {
        return (GrExpression)child.getPsi();
      }
    }
    return null;
  }

  @Nullable
  private ASTNode getFirstSemicolon() {
    for (ASTNode child = getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.getElementType() == GroovyElementTypes.mSEMI) {
        return child;
      }
    }

    return null;
  }

  @Nullable
  private ASTNode getSecondSemicolon() {
    boolean firstPassed = false;
    for (ASTNode child = getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.getElementType() == GroovyElementTypes.mSEMI) {
        if (firstPassed) {
          return child;
        } else {
          firstPassed = true;
        }
      }
    }
    return null;
  }
}