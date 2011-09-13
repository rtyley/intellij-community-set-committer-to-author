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
package org.jetbrains.plugins.groovy.lang.completion.weighers;

import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.completion.CompletionWeigher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrPropertyForCompletion;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GrKindWeigher extends CompletionWeigher {
  private static final Set<String> TRASH_CLASSES = new HashSet<String>(10);

  static {
    TRASH_CLASSES.add(CommonClassNames.JAVA_LANG_CLASS);
    TRASH_CLASSES.add(CommonClassNames.JAVA_LANG_OBJECT);
    TRASH_CLASSES.add(GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT);
  }

  @Override
  public Comparable weigh(@NotNull LookupElement element, @NotNull CompletionLocation location) {
    final PsiElement position = location.getCompletionParameters().getPosition();
    if (!(position.getContainingFile() instanceof GroovyFileBase)) return null;
    if (!(position.getParent() instanceof GrReferenceElement)) return null;

    final GrReferenceElement parent = (GrReferenceElement)position.getParent();

    Object o = element.getObject();
    if (o instanceof ResolveResult) {
      o = ((ResolveResult)o).getElement();
    }


    final PsiElement qualifier = parent.getQualifier();
    if (qualifier == null) {
      if (o instanceof PsiVariable && !(o instanceof PsiField)) {
        return NotQualifiedKind.local;
      }
      if (isLightElement(o)) return NotQualifiedKind.unknown;
      if (o instanceof PsiMember) {
        final PsiClass containingClass = ((PsiMember)o).getContainingClass();
        if (isAccessor((PsiMember)o)) return NotQualifiedKind.accessor;
        if (o instanceof PsiClass && ((PsiClass)o).getContainingClass() == null || o instanceof PsiPackage) return NotQualifiedKind.unknown;
        if (o instanceof PsiClass) return NotQualifiedKind.innerClass;
        if (PsiTreeUtil.isContextAncestor(containingClass, position, false)) return NotQualifiedKind.currentClassMember;
        return NotQualifiedKind.member;
      }
      return NotQualifiedKind.unknown;
    }
    else {
      if (o instanceof PsiEnumConstant) return QualifiedKind.enumConstant;

      if (isLightElement(o)) return QualifiedKind.unknown;
      if (o instanceof PsiMember) {
        if (isTrashMethod((PsiMember)o)) return QualifiedKind.unknown;
        if (isAccessor((PsiMember)o)) return QualifiedKind.accessor;
        if (isQualifierClassMember((PsiMember)o, qualifier)) {
          return QualifiedKind.currentClassMember;
        }
        if (o instanceof PsiClass && ((PsiClass)o).getContainingClass() == null || o instanceof PsiPackage) return QualifiedKind.unknown;
        if (o instanceof PsiClass) return QualifiedKind.innerClass;
        return QualifiedKind.member;
      }
      return QualifiedKind.unknown;
    }
  }

  private static boolean isLightElement(Object o) {
    return o instanceof LightElement && !(o instanceof GrPropertyForCompletion) && !(o instanceof GrAccessorMethod);
  }

  private static boolean isTrashMethod(PsiMember o) {
    final PsiClass containingClass = o.getContainingClass();
    return containingClass != null && TRASH_CLASSES.contains(containingClass.getQualifiedName());
  }
  
  private static boolean isAccessor(PsiMember member) {
    return member instanceof PsiMethod && (GroovyPropertyUtils.isSimplePropertyAccessor((PsiMethod)member) || "setProperty".equals(((PsiMethod)member).getName()));
  }
  

  private static boolean isQualifierClassMember(PsiMember member, PsiElement qualifier) {
    if (!(qualifier instanceof GrExpression)) return false;

    final PsiType type = ((GrExpression)qualifier).getType();
    if (!(type instanceof PsiClassType)) return false;

    final PsiClass psiClass = ((PsiClassType)type).resolve();
    if (psiClass == null) return false;

    return PsiManager.getInstance(qualifier.getProject()).areElementsEquivalent(member.getContainingClass(), psiClass);
  }

  private static enum NotQualifiedKind {
    innerClass,
    unknown,
    accessor,
    member,
    currentClassMember,
    local,
  }

  private static enum QualifiedKind {
    innerClass,
    unknown,
    accessor,
    member,
    currentClassMember,
    enumConstant,
  }
}
