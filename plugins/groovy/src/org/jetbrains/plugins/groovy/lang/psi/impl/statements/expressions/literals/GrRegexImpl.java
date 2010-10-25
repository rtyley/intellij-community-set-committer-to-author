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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrRegex;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;

/**
 * @author ilyas
 */
public class GrRegexImpl extends GrExpressionImpl implements GrRegex {

  public GrRegexImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Compound regular expression";
  }

  public PsiType getType() {
    return null;
  }

  public boolean isPlainString() {
    return true;
  }

  @Override
  public GrStringInjection[] getInjections() {
    return GrStringInjection.EMPTY_ARRAY;
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitRegexExpression(this);
  }

  public Object getValue() {
    return null;
  }
}

