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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
* @author peter
*/
public class InheritorsHolder implements Consumer<LookupElement> {
  private final PsiElement myPosition;
  private final Set<String> myAddedClasses = new HashSet<String>();
  private final CompletionResultSet myResult;

  public InheritorsHolder(PsiElement position, CompletionResultSet result) {
    myPosition = position;
    myResult = result;
  }

  @Override
  public void consume(LookupElement lookupElement) {
    final Object object = lookupElement.getObject();
    if (object instanceof PsiClass) {
      final PsiClass psiClass = (PsiClass)object;
      if (JavaCompletionUtil.hasAccessibleInnerClass(psiClass, myPosition)) return;

      ContainerUtil.addIfNotNull(myAddedClasses, psiClass.getQualifiedName());
    }
    myResult.addElement(AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(lookupElement));
  }

  public boolean alreadyProcessed(@NotNull LookupElement element) {
    final Object object = element.getObject();
    if (object instanceof PsiClass) {
      if (alreadyProcessed((PsiClass)object)) return true;
    }
    return false;
  }

  public boolean alreadyProcessed(@NotNull PsiClass object) {
    final String qualifiedName = object.getQualifiedName();
    if (qualifiedName == null || myAddedClasses.contains(qualifiedName)) {
      return true;
    }
    return false;
  }
}
