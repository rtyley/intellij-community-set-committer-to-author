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
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrBinaryExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author ilyas
 */
public class GrAdditiveExpressionImpl extends GrBinaryExpressionImpl {

  public GrAdditiveExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public PsiType getType() {
    final GrExpression lop = getLeftOperand();
    final PsiType lType = lop.getType();
    final PsiType numeric = TypesUtil.getNumericResultType(this, lType);
    if (numeric != null) return numeric;

    IElementType tokenType = getOperationTokenType();
    if (tokenType == GroovyTokenTypes.mPLUS) {
      if (isStringType(lType)) {
        return getTypeByFQName(CommonClassNames.JAVA_LANG_STRING);
      }
      final GrExpression rop = getRightOperand();
      if (rop != null && isStringType(rop.getType())) {
        return getTypeByFQName(CommonClassNames.JAVA_LANG_STRING);
      }
    }

    return null;
  }

  private static boolean isStringType(PsiType type) {
    return type != null && (type.equalsToText(CommonClassNames.JAVA_LANG_STRING) || type.equalsToText(GroovyCommonClassNames.GROOVY_LANG_GSTRING));
  }

  public String toString() {
    return "Additive expression";
  }
}
