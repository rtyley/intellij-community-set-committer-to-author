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
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenameJavaMethodProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.*;

/**
 * @author Maxim.Medvedev
 */
public class RenameAliasImportedMethodProcessor extends RenameJavaMethodProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof GroovyPsiElement && super.canProcessElement(element);
  }

  @NotNull
  @Override
  public Collection<PsiReference> findReferences(PsiElement element) {
    return RenameAliasedUsagesUtil.filterAliasedRefs(super.findReferences(element), element);
  }



  @Override
  public void renameElement(PsiElement psiElement,
                            String newName,
                            UsageInfo[] usages,
                            RefactoringElementListener listener) throws IncorrectOperationException {
    boolean isGetter = isSimplePropertyGetter((PsiMethod)psiElement);
    boolean isSetter = isSimplePropertySetter((PsiMethod)psiElement);

    List<UsageInfo> methodAccess = new ArrayList<UsageInfo>(usages.length);
    List<UsageInfo> propertyAccess = new ArrayList<UsageInfo>(usages.length);

    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element instanceof GrReferenceExpression && ((GrReferenceExpression)element).advancedResolve().isInvokedOnProperty()) {
        propertyAccess.add(usage);
      }
      else {
        methodAccess.add(usage);
      }
    }
    super.renameElement(psiElement, newName, methodAccess.toArray(new UsageInfo[methodAccess.size()]), listener);
    final String propertyName;
    if (isGetter) {
      propertyName = getPropertyNameByGetterName(newName, true);
    }
    else if (isSetter) {
      propertyName = getPropertyNameBySetterName(newName);
    }
    else {
      propertyName = null;
    }

    if (propertyName == null) {
      //it means accessor is renamed to not-accessor and we should replace all property-access-refs with method-access-refs

      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(psiElement.getProject());
      for (UsageInfo info : propertyAccess) {
        final PsiElement element = info.getElement();
        if (element instanceof GrReferenceExpression) {
          final PsiElement qualifier = ((GrReferenceExpression)element).getQualifier();
          String qualifierPrefix = qualifier == null ? "" : qualifier.getText() + ".";
          if (isGetter) {
            final GrExpression call = factory.createExpressionFromText(qualifierPrefix + newName + "()");
            ((GrReferenceExpression)element).replaceWithExpression(call, true);
          }
          else {
            final PsiElement parent = element.getParent();
            assert parent instanceof GrAssignmentExpression;
            final GrExpression rValue = ((GrAssignmentExpression)parent).getRValue();
            final GrExpression call =
              factory.createExpressionFromText(qualifierPrefix + newName + "(" + (rValue == null ? "" : rValue.getText()) + ")");
            ((GrAssignmentExpression)parent).replaceWithExpression(call, true);
          }
        }
      }
    }
    else {
      for (UsageInfo usage : propertyAccess) {
        final PsiReference ref = usage.getReference();
        if (ref != null) {
          ((GrReferenceExpression)ref).handleElementRenameSimple(propertyName);
        }
      }
    }
  }

  @Override
  public void findCollisions(PsiElement element,
                             final String newName,
                             Map<? extends PsiElement, String> allRenames,
                             List<UsageInfo> result) {
    final ListIterator<UsageInfo> iterator = result.listIterator();
    while (iterator.hasNext()) {
      final UsageInfo info = iterator.next();
      final PsiElement ref = info.getElement();
      if (ref == null) continue;
      if (!RenameUtil.isValidName(element.getProject(), ref, newName)) {
        iterator.add(new UnresolvableCollisionUsageInfo(ref, element) {
          @Override
          public String getDescription() {
            return RefactoringBundle.message("0.is.not.an.identifier", newName, ref.getText());
          }
        });
      }
    }
  }
}
