package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.psi.*;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author peter
 */
public class DynamicMembersContributor extends NonCodeMembersContributor {
  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiScopeProcessor processor,
                                     GroovyPsiElement place,
                                     ResolveState state) {
    final DynamicManager manager = DynamicManager.getInstance(place.getProject());
    NameHint nameHint = processor.getHint(NameHint.KEY);
    if (nameHint != null && !((DynamicManagerImpl)manager).containsDynamicMember(nameHint.getName(state))) {
      return;
    }


    for (String qName : ResolveUtil.getAllSuperTypes(qualifierType, place.getProject()).keySet()) {
      for (PsiMethod method : manager.getMethods(qName)) {
        if (!ResolveUtil.processElement(processor, method, state)) return;
      }

      for (PsiVariable var : manager.getProperties(qName)) {
        if (!ResolveUtil.processElement(processor, var, state)) return;
      }
    }
  }
}
