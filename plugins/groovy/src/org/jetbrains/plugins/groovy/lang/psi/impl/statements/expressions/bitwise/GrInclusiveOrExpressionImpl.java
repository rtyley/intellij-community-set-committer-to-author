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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.bitwise;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrBinaryExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author ilyas
 */
public class GrInclusiveOrExpressionImpl extends GrBinaryExpressionImpl {

  public GrInclusiveOrExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Inclusive OR expression";
  }

  @Nullable
  public PsiType getType() {
    final GrExpression lop = getLeftOperand();
    return TypesUtil.getNumericResultType(this, lop == null ? null : lop.getType());
  }
}
