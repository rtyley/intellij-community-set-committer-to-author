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
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;

/**
 * @author Maxim.Medvedev
 */
public class GroovyImplementationSearch implements QueryExecutor<PsiElement, PsiElement> {
  @Override
  public boolean execute(@NotNull PsiElement source, @NotNull Processor<PsiElement> consumer) {
    if (source instanceof GrAccessorMethod) {
      GrField property = ((GrAccessorMethod)source).getProperty();
      return consumer.process(property);
    }
    return true;
  }
}
