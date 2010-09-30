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
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

/**
 * @author Maxim.Medvedev
 */
public class AccessorResolverProcessor extends ResolverProcessor {
  private final boolean mySearchForGetter;

  public AccessorResolverProcessor(String name, PsiElement place, boolean searchForGetter) {
    super(name, RESOLVE_KINDS_METHOD, place, PsiType.EMPTY_ARRAY);
    mySearchForGetter = searchForGetter;
  }

  public boolean execute(PsiElement element, ResolveState state) {
    final GroovyPsiElement resolveContext = state.get(RESOLVE_CONTEXT);
    boolean usedInCategory = usedInCategory(resolveContext);
    if (mySearchForGetter) {
      if (element instanceof PsiMethod && GroovyPropertyUtils.isSimplePropertyGetter((PsiMethod)element, null, usedInCategory)) {
        return super.execute(element, state);
      }
    }
    else {
      if (element instanceof PsiMethod && GroovyPropertyUtils.isSimplePropertySetter((PsiMethod)element, null, usedInCategory)) {
        return super.execute(element, state);
      }
    }
    return true;
  }

  private static boolean usedInCategory(GroovyPsiElement resolveContext) {
    if (resolveContext instanceof GrMethodCallExpression) {
      final GrExpression expression = ((GrMethodCallExpression)resolveContext).getInvokedExpression();
      if (expression instanceof GrReferenceExpression) {
        return "use".equals(((GrReferenceExpression)expression).getName());
      }
    }
    return false;
  }
}
