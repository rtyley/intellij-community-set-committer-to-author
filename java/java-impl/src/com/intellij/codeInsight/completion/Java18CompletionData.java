/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.TextFilter;
import com.intellij.psi.filters.classes.InterfaceFilter;
import com.intellij.psi.filters.position.LeftNeighbour;
import com.intellij.psi.filters.position.ParentElementFilter;

public class Java18CompletionData extends Java15CompletionData {
  @Override
  protected void initVariantsInMethodScope() {
    super.initVariantsInMethodScope();

    {
      // in extension method
      ElementFilter position = new AndFilter(
        new LeftNeighbour(new AndFilter(
          new TextFilter(")"),
          new ParentElementFilter(new ClassFilter(PsiParameterList.class)))),
        new ParentElementFilter(new InterfaceFilter(), 3));
      CompletionVariant variant = new CompletionVariant(PsiMethod.class, position);
      variant.addCompletion(PsiKeyword.DEFAULT);
      registerVariant(variant);
    }
  }
}
