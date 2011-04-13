/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang;

import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class GroovyMethodReturnNamedArgumentProvider extends GroovyNamedArgumentProvider {
  @Override
  public void getNamedArguments(@NotNull GrCall call,
                                @Nullable PsiElement resolve,
                                @Nullable String argumentName,
                                boolean forCompletion,
                                Map<String, ArgumentDescriptor> result) {
    if (!forCompletion || !(resolve instanceof PsiMethod)) return;

    PsiType returnType = ((PsiMethod)resolve).getReturnType();
    if (!(returnType instanceof PsiClassType)) return;

    Map<String, ArgumentDescriptor> map = new HashMap<String, ArgumentDescriptor>();

    GroovyConstructorNamedArgumentProvider.processClass(call, (PsiClassType)returnType, argumentName, map);

    for (String name : map.keySet()) {
      result.put(name, TYPE_ANY_NOT_FIRST);
    }
  }
}
