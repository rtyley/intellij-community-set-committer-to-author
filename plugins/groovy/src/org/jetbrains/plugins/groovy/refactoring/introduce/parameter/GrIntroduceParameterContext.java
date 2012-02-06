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
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceParameterContext extends GrIntroduceContext {
  private final PsiElement toSearchFor;
  private final GrParametersOwner toReplaceIn;

  public GrIntroduceParameterContext(GrIntroduceContext context, GrParametersOwner toReplaceIn, PsiElement toSearchFor) {
    super(context.getProject(), context.getEditor(), context.getExpression(), context.getVar(), context.getOccurrences(),
          context.getScope());
    this.toReplaceIn = toReplaceIn;
    this.toSearchFor = toSearchFor;
  }

  public PsiElement getToSearchFor() {
    return toSearchFor;
  }

  public GrParametersOwner getToReplaceIn() {
    return toReplaceIn;
  }
}
