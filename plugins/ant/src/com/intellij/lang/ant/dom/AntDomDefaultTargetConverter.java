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
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.AntSupport;
import com.intellij.openapi.util.Trinity;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 16, 2010
 */
public class AntDomDefaultTargetConverter extends Converter<Trinity<AntDomTarget, String, Map<String, AntDomTarget>>> implements CustomReferenceConverter<Trinity<AntDomTarget, String, Map<String, AntDomTarget>>>{

  @NotNull public PsiReference[] createReferences(GenericDomValue<Trinity<AntDomTarget, String, Map<String, AntDomTarget>>> value, PsiElement element, ConvertContext context) {
    final Trinity<AntDomTarget, String, Map<String, AntDomTarget>> trinity = value.getValue();
    if (trinity == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final DomTarget domTarget = DomTarget.getTarget(trinity.getFirst());
    if (domTarget == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    final PsiElement psiElement = PomService.convertToPsi(domTarget);
    return new PsiReference[] {new PsiReferenceBase<PsiElement>(psiElement) {
      public PsiElement resolve() {
        return psiElement;
      }

      @NotNull public Object[] getVariants() {
        final Set<String> set = trinity.getThird().keySet();
        return set.toArray(new Object[set.size()]);
      }
    }};
  }

  @Nullable
  public Trinity<AntDomTarget, String, Map<String, AntDomTarget>> fromString(@Nullable @NonNls String s, ConvertContext context) {
    final AntDomElement element = AntSupport.getInvocationAntDomElement(context);
    if (element != null && s != null) {
      final AntDomProject project = element.getAntProject();
      return TargetResolver.resolveWithVariants(project, null, s);
    }
    return null;
  }

  @Nullable
  public String toString(@Nullable Trinity<AntDomTarget, String, Map<String, AntDomTarget>> trinity, ConvertContext context) {
    return trinity != null? trinity.getSecond() : null;
  }
}
