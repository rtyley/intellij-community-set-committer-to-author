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

/*
 * Class LocalVariableEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.impl.watch.LocalVariableDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.Type;
import com.sun.jdi.Value;

class LocalVariableEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.expression.LocalVariableEvaluator");

  private final String myLocalVariableName;
  private EvaluationContextImpl myContext;
  private LocalVariableProxyImpl myEvaluatedVariable;
  private final boolean myIsJspSpecial;

  public LocalVariableEvaluator(String localVariableName, boolean isJspSpecial) {
    myLocalVariableName = localVariableName;
    myIsJspSpecial = isJspSpecial;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    StackFrameProxyImpl frameProxy = context.getFrameProxy();
    if (frameProxy == null) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.no.stackframe"));
    }

    try {
      for(;;) {
        LocalVariableProxyImpl local = frameProxy.visibleVariableByName(myLocalVariableName);
        if (local != null) {
          myEvaluatedVariable = local;
          myContext = context;
          return frameProxy.getValue(local);
        }

        ThreadReferenceProxyImpl threadProxy = frameProxy.threadProxy();
        if(myIsJspSpecial && frameProxy.getFrameIndex() < threadProxy.frameCount() - 1) {
          if(frameProxy.getFrameIndex() < threadProxy.frameCount() - 1) {
            frameProxy = threadProxy.frame(frameProxy.getFrameIndex() + 1);
            continue;
          }
        }

        break;
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.local.variable.missing", myLocalVariableName));
    }
    catch (EvaluateException e) {
      myEvaluatedVariable = null;
      myContext = null;
      throw e;
    }
  }

  public Modifier getModifier() {
    Modifier modifier = null;
    if (myEvaluatedVariable != null && myContext != null) {
      modifier = new Modifier() {
        public boolean canInspect() {
          return true;
        }

        public boolean canSetValue() {
          return true;
        }

        public void setValue(Value value) throws ClassNotLoadedException, InvalidTypeException {
          StackFrameProxyImpl frameProxy = myContext.getFrameProxy();
          try {
            frameProxy.setValue(myEvaluatedVariable, value);
          }
          catch (EvaluateException e) {
            LOG.error(e);  
          }
        }

        public Type getExpectedType() throws ClassNotLoadedException {
          try {
            return myEvaluatedVariable.getType();
          } catch (EvaluateException e) {
            LOG.error(e);
            return null;
          }
        }

        public NodeDescriptorImpl getInspectItem(Project project) {
          return new LocalVariableDescriptorImpl(project, myEvaluatedVariable);
        }
      };
    }
    return modifier;
  }
}
