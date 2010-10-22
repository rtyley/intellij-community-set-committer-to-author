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
package com.intellij.codeInsight.intention;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyMemberType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
public abstract class QuickFixFactory {
  public static QuickFixFactory getInstance() {
    return ServiceManager.getService(QuickFixFactory.class);
  }

  public abstract IntentionAction createModifierListFix(@NotNull PsiModifierList modifierList,
                                                        @Modifier @NotNull String modifier,
                                                        boolean shouldHave,
                                                        final boolean showContainingClass);
  public abstract IntentionAction createModifierListFix(@NotNull PsiModifierListOwner owner,
                                                        @Modifier @NotNull String modifier,
                                                        boolean shouldHave,
                                                        final boolean showContainingClass);
  public abstract IntentionAction createMethodReturnFix(@NotNull PsiMethod method, @NotNull PsiType toReturn, boolean fixWholeHierarchy);

  public abstract IntentionAction createAddMethodFix(@NotNull PsiMethod method, @NotNull PsiClass toClass);
  public abstract IntentionAction createAddMethodFix(@NotNull String methodText, @NotNull PsiClass toClass, String... exceptions);

  /**
   * @param psiElement psiClass or enum constant without class initializer
   */
  public abstract IntentionAction createImplementMethodsFix(@NotNull PsiElement psiElement);
  public abstract IntentionAction createImplementMethodsFix(@NotNull PsiClass psiElement);
  public abstract IntentionAction createMethodThrowsFix(@NotNull PsiMethod method, @NotNull PsiClassType exceptionClass, boolean shouldThrow, boolean showContainingClass);
  public abstract IntentionAction createAddDefaultConstructorFix(@NotNull PsiClass aClass);
  public abstract IntentionAction createMethodParameterTypeFix(@NotNull PsiMethod method, int index, @NotNull PsiType newType, boolean fixWholeHierarchy);
  public abstract IntentionAction createMakeClassInterfaceFix(@NotNull PsiClass aClass);
  public abstract IntentionAction createMakeClassInterfaceFix(@NotNull PsiClass aClass, final boolean makeInterface);
  public abstract IntentionAction createExtendsListFix(@NotNull PsiClass aClass, @NotNull PsiClassType typeToExtendFrom, boolean toAdd);
  public abstract IntentionAction createRemoveUnusedParameterFix(@NotNull PsiParameter parameter);
  public abstract IntentionAction createRemoveUnusedVariableFix(@NotNull PsiVariable variable);

  @Nullable
  public abstract IntentionAction createCreateClassOrPackageFix(@NotNull PsiElement context, @NotNull String qualifiedName, final boolean createClass, final String superClass);
  @Nullable
  public abstract IntentionAction createCreateClassOrInterfaceFix(@NotNull PsiElement context, @NotNull String qualifiedName, final boolean createClass, final String superClass);
  public abstract IntentionAction createCreateFieldOrPropertyFix(final PsiClass aClass, final String name, final PsiType type, final PropertyMemberType targetMember, final PsiAnnotation... annotations);
}
