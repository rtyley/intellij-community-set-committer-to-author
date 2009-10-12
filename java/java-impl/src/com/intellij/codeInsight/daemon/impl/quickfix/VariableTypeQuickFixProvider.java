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
 * User: anna
 * Date: 27-Aug-2009
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.quickfix.ChangeVariableTypeQuickFixProvider;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiType;

public class VariableTypeQuickFixProvider implements ChangeVariableTypeQuickFixProvider{
  public IntentionAction[] getFixes(PsiVariable variable, PsiType toReturn) {
    return new IntentionAction[]{new VariableTypeFix(variable, toReturn)};
  }
}