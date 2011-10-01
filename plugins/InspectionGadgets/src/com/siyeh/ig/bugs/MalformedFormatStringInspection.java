/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.FormatUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class MalformedFormatStringInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "malformed.format.string.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Object value = infos[0];
    if (value instanceof Exception) {
      return InspectionGadgetsBundle.message(
        "malformed.format.string.problem.descriptor.malformed");
    }
    final Validator[] validators = (Validator[])value;
    final int argumentCount = ((Integer)infos[1]).intValue();
    if (validators.length < argumentCount) {
      return InspectionGadgetsBundle.message(
        "malformed.format.string.problem.descriptor.too.many.arguments");
    }
    if (validators.length > argumentCount) {
      return InspectionGadgetsBundle.message(
        "malformed.format.string.problem.descriptor.too.few.arguments");
    }
    return InspectionGadgetsBundle.message(
      "malformed.format.string.problem.descriptor.arguments.do.not.match.type");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MalformedFormatStringVisitor();
  }

  private static class MalformedFormatStringVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!FormatUtils.isFormatCall(expression)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression firstArgument = arguments[0];
      final PsiType type = firstArgument.getType();
      if (type == null) {
        return;
      }
      final int formatArgumentIndex;
      if ("java.util.Locale".equals(type.getCanonicalText())
          && arguments.length > 1) {
        formatArgumentIndex = 1;
      }
      else {
        formatArgumentIndex = 0;
      }
      final PsiExpression formatArgument = arguments[formatArgumentIndex];
      if (!TypeUtils.expressionHasType(formatArgument,
                                       CommonClassNames.JAVA_LANG_STRING)) {
        return;
      }
      if (!PsiUtil.isConstantExpression(formatArgument)) {
        return;
      }
      final PsiType formatType = formatArgument.getType();
      final String value =
        (String)ConstantExpressionUtil.computeCastTo(
          formatArgument, formatType);
      if (value == null) {
        return;
      }
      final int argumentCount =
        arguments.length - (formatArgumentIndex + 1);
      final Validator[] validators;
      try {
        validators = FormatDecode.decode(value, argumentCount);
      }
      catch (Exception e) {
        registerError(formatArgument, e);
        return;
      }
      if (validators.length != argumentCount) {
        if (argumentCount == 1) {
          final PsiExpression argument =
            arguments[formatArgumentIndex + 1];
          final PsiType argumentType = argument.getType();
          if (argumentType instanceof PsiArrayType) {
            return;
          }
        }
        registerError(formatArgument, validators,
                      Integer.valueOf(argumentCount));
        return;
      }
      for (int i = 0; i < validators.length; i++) {
        final Validator validator = validators[i];
        final PsiType argumentType =
          arguments[i + formatArgumentIndex + 1].getType();
        if (!validator.valid(argumentType)) {
          registerError(formatArgument, validators,
                        Integer.valueOf(argumentCount));
          return;
        }
      }
    }
  }
}