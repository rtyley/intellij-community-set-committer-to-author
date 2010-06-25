package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author peter
 */
public class GdkMembersContributor extends NonCodeMembersContributor {
  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiScopeProcessor processor,
                                     PsiElement place,
                                     ResolveState state,
                                     ProcessingContext ctx) {
    final GroovyPsiManager manager = GroovyPsiManager.getInstance(place.getProject());
    for (String qName : ResolveUtil.getAllSuperTypes(qualifierType, place, ctx).keySet()) {
      for (PsiMethod defaultMethod : manager.getDefaultMethods(qName)) {
        if (!ResolveUtil.processElement(processor, defaultMethod)) return;
      }
    }
  }
}
