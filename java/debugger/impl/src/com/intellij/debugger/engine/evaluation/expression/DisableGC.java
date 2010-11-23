/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.ObjectReference;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/30/10
 */
public class DisableGC implements Evaluator{
  private final Evaluator myDelegate;

  public DisableGC(Evaluator delegate) {
    myDelegate = delegate;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    final Object result = myDelegate.evaluate(context);
    if (result instanceof ObjectReference) {
      context.getSuspendContext().keep((ObjectReference)result);
    }
    return result;
  }

  public Evaluator getDelegate() {
    return myDelegate;
  }

  public Modifier getModifier() {
    return myDelegate.getModifier();
  }
}
