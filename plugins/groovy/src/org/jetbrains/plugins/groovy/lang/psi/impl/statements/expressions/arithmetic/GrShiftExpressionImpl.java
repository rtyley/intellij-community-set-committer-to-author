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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrBinaryExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author ilyas
 */
public class GrShiftExpressionImpl extends GrBinaryExpressionImpl {

  public GrShiftExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Shift expression";
  }

  public PsiType getType() {
    GrExpression lop = getLeftOperand();
    if (lop == null) return null;
    PsiType lopType = lop.getType();
    if (lopType == null) return null;
    if (lopType.equalsToText("java.lang.Byte") ||
        lopType.equalsToText("java.lang.Character") ||
        lopType.equalsToText("java.lang.Short")) {
      return getTypeByFQName("java.lang.Integer");
    }

    return lopType;
  }
}
