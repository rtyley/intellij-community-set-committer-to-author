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

package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.Nullable;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ReferenceRange;
import com.intellij.psi.search.*;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * author ven
 */
public class AccessorReferencesSearcher extends SearchRequestor {
  @Override
  public void contributeSearchTargets(@NotNull PsiElement target,
                                      @NotNull final FindUsagesOptions options,
                                      @NotNull PsiSearchRequest.ComplexRequest collector,
                                      final Processor<PsiReference> consumer) {
    if (!(target instanceof PsiMethod)) {
      return;
    }

    final PsiMethod method = (PsiMethod)target;

    final String propertyName = GroovyPropertyUtils.getPropertyName(method);
    if (propertyName == null) return;

    SearchScope searchScope = PsiUtil.restrictScopeToGroovyFiles(options.searchScope);

    final TextOccurenceProcessor processor = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        final PsiReference[] refs = element.getReferences();
        for (PsiReference ref : refs) {
          if (ReferenceRange.containsOffsetInElement(ref, offsetInElement)) {
            if (ref.isReferenceTo(method)) {
              return consumer.process(ref);
            }
          }
        }
        return true;
      }
    };

    collector.addRequest(PsiSearchRequest.elementsWithWord(searchScope, propertyName, UsageSearchContext.IN_CODE, true, processor));
  }

}
