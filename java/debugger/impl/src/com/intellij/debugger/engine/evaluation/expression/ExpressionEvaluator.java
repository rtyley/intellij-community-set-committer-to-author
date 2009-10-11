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
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.sun.jdi.Value;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jul 15, 2003
 * Time: 1:44:35 PM
 * To change this template use Options | File Templates.
 */
public interface ExpressionEvaluator {
  //call evaluate before
  public Value getValue();

  //call evaluate before
  public Modifier getModifier();

  public Value evaluate(final EvaluationContext context) throws EvaluateException;
}
