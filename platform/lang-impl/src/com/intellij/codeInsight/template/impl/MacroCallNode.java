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

package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.Result;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 *
 */
public class MacroCallNode extends Expression {
  public Macro getMacro() {
    return myMacro;
  }

  private final Macro myMacro;
  private final ArrayList<Expression> myParameters = new ArrayList<Expression>();

  public MacroCallNode(@NotNull Macro macro) {
    myMacro = macro;
  }

  public void addParameter(Expression node) {
    myParameters.add(node);
  }

  public Result calculateResult(ExpressionContext context) {
    Expression[] parameters = myParameters.toArray(new Expression[myParameters.size()]);
    return myMacro.calculateResult(parameters, context);
  }

  public Result calculateQuickResult(ExpressionContext context) {
    Expression[] parameters = myParameters.toArray(new Expression[myParameters.size()]);
    return myMacro.calculateQuickResult(parameters, context);
  }

  public LookupElement[] calculateLookupItems(ExpressionContext context) {
    Expression[] parameters = myParameters.toArray(new Expression[myParameters.size()]);
    return myMacro.calculateLookupItems(parameters, context);
  }

  public Expression[] getParameters() {
    return myParameters.toArray(new Expression[myParameters.size()]);
  }
}
