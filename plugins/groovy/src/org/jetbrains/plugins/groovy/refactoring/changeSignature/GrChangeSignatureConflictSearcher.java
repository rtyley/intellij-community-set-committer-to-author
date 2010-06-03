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
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.JavaChangeInfo;
import com.intellij.refactoring.changeSignature.JavaParameterInfo;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
class GrChangeSignatureConflictSearcher {
  private static final Logger LOG =
    Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.changeSignature.GrChangeSignatureConflictSearcher");
  private final JavaChangeInfo myChangeInfo;

  GrChangeSignatureConflictSearcher(JavaChangeInfo changeInfo) {
    this.myChangeInfo = changeInfo;
  }

  public MultiMap<PsiElement, String> findConflicts(Ref<UsageInfo[]> refUsages) {
    MultiMap<PsiElement, String> conflictDescriptions = new MultiMap<PsiElement, String>();
    addMethodConflicts(conflictDescriptions);
    UsageInfo[] usagesIn = refUsages.get();
    RenameUtil.addConflictDescriptions(usagesIn, conflictDescriptions);
    Set<UsageInfo> usagesSet = new HashSet<UsageInfo>(Arrays.asList(usagesIn));
    RenameUtil.removeConflictUsages(usagesSet);
    if (myChangeInfo.isVisibilityChanged()) {
      try {
        addInaccessibilityDescriptions(usagesSet, conflictDescriptions);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    return conflictDescriptions;
  }

  private boolean needToChangeCalls() {
    return myChangeInfo.isNameChanged() || myChangeInfo.isParameterSetOrOrderChanged() || myChangeInfo.isExceptionSetOrOrderChanged();
  }

  private void addInaccessibilityDescriptions(Set<UsageInfo> usages, MultiMap<PsiElement, String> conflictDescriptions)
    throws IncorrectOperationException {
    PsiMethod method = myChangeInfo.getMethod();
    PsiModifierList modifierList = (PsiModifierList)method.getModifierList().copy();
    VisibilityUtil.setVisibility(modifierList, myChangeInfo.getNewVisibility());

    for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext();) {
      UsageInfo usageInfo = iterator.next();
      PsiElement element = usageInfo.getElement();
      if (element != null) {
        if (element instanceof GrReferenceExpression) {
          PsiClass accessObjectClass = null;
          GrExpression qualifier = ((GrReferenceExpression)element).getQualifierExpression();
          if (qualifier != null) {
            accessObjectClass = getAccessObjectClass(qualifier);
          }

          if (!JavaPsiFacade.getInstance(element.getProject()).getResolveHelper()
            .isAccessible(method, modifierList, element, accessObjectClass, null)) {
            String message =
              RefactoringBundle.message("0.with.1.visibility.is.not.accesible.from.2",
                                        RefactoringUIUtil.getDescription(method, true),
                                        myChangeInfo.getNewVisibility(),
                                        RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(element), true));
            conflictDescriptions.putValue(method, message);
            if (!needToChangeCalls()) {
              iterator.remove();
            }
          }
        }
      }
    }
  }

  @Nullable
  private static PsiClass getAccessObjectClass(GrExpression expression) {
    if (expression instanceof GrConstructorInvocation) return null;
    PsiType type = expression.getType();
    if (type instanceof PsiClassType) {
      return ((PsiClassType)type).resolveGenerics().getElement();
    }
    if (type == null && expression instanceof PsiReferenceExpression) {
      JavaResolveResult resolveResult = ((PsiReferenceExpression)expression).advancedResolve(false);
      if (resolveResult.getElement() instanceof PsiClass) {
        return (PsiClass)resolveResult.getElement();
      }
    }
    return null;
  }


  private void addMethodConflicts(MultiMap<PsiElement, String> conflicts) {
    try {
      GrMethod prototype;
      final PsiMethod method = myChangeInfo.getMethod();
      if (!(method instanceof GrMethod)) return;
      PsiManager manager = PsiManager.getInstance(method.getProject());
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(manager.getProject());
      final CanonicalTypes.Type returnType = myChangeInfo.getNewReturnType();
      String newMethodName = myChangeInfo.getNewName();
      if (returnType != null) {
        prototype = factory.createMethodFromText("", newMethodName, returnType.getTypeText(), ArrayUtil.EMPTY_STRING_ARRAY, method);
      }
      else {
        prototype = factory.createConstructorFromText(newMethodName, ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY, "{}", method);
      }
      JavaParameterInfo[] parameters = myChangeInfo.getNewParameters();

      for (JavaParameterInfo info : parameters) {
        PsiParameter param = factory.createParameter(info.getName(), info.getTypeText(), (GroovyPsiElement)method);
        prototype.getParameterList().add(param);
      }

      ConflictsUtil.checkMethodConflicts(method.getContainingClass(), method, prototype, conflicts);
      GrMethodConflictUtil.checkMethodConflicts(method.getContainingClass(), prototype, ((GrMethod)method), conflicts, true);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
