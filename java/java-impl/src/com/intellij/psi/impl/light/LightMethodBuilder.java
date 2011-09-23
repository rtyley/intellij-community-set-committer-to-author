/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.presentation.java.JavaPresentationUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.ui.RowIcon;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author peter
 */
public class LightMethodBuilder extends LightElement implements PsiMethod {
  private final String myName;
  private Computable<PsiType> myReturnType;
  private final PsiModifierList myModifierList;
  private PsiParameterList myParameterList;
  private PsiReferenceList myThrowsList;
  private Icon myBaseIcon;
  private PsiClass myContainingClass;
  private boolean myConstructor;
  private String myMethodKind = "LightMethodBuilder";

  public LightMethodBuilder(PsiClass constructedClass, Language language) {
    this(constructedClass.getManager(), language, constructedClass.getName());
    setContainingClass(constructedClass);
  }

  public LightMethodBuilder(PsiManager manager, String name) {
    this(manager, StdLanguages.JAVA, name);
  }
  
  public LightMethodBuilder(PsiManager manager, Language language, String name) {
    this(manager, language, name, new LightParameterListBuilder(manager, language), new LightModifierList(manager, language));
  }

  public LightMethodBuilder(PsiManager manager,
                            Language language,
                            String name,
                            PsiParameterList parameterList,
                            PsiModifierList modifierList) {
    this(manager, language, name, parameterList, modifierList, new LightReferenceListBuilder(manager, language, PsiReferenceList.Role.THROWS_LIST));
  }

  public LightMethodBuilder(PsiManager manager,
                            Language language,
                            String name,
                            PsiParameterList parameterList,
                            PsiModifierList modifierList,
                            PsiReferenceList throwsList) {
    super(manager, language);
    myName = name;
    myParameterList = parameterList;
    myModifierList = modifierList;
    myThrowsList = throwsList;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  public boolean hasTypeParameters() {
    return false;
  }

  @NotNull public PsiTypeParameter[] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  public PsiTypeParameterList getTypeParameterList() {
    //todo
    return null;
  }

  public PsiDocComment getDocComment() {
    //todo
    return null;
  }

  public boolean isDeprecated() {
    //todo
    return false;
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Please don't rename light methods");
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @NotNull
  public PsiModifierList getModifierList() {
    return myModifierList;
  }

  public LightMethodBuilder addModifiers(String... modifiers) {
    for (String modifier : modifiers) {
      addModifier(modifier);
    }
    return this;
  }

  public LightMethodBuilder addModifier(String modifier) {
    ((LightModifierList)myModifierList).addModifier(modifier);
    return this;
  }

  public LightMethodBuilder setModifiers(String... modifiers) {
    ((LightModifierList)myModifierList).clearModifiers();
    addModifiers(modifiers);
    return this;
  }

  public PsiType getReturnType() {
    return myReturnType == null ? null : myReturnType.compute();
  }

  public LightMethodBuilder setMethodReturnType(Computable<PsiType> returnType) {
    myReturnType = returnType;
    return this;
  }

  public LightMethodBuilder setMethodReturnType(PsiType returnType) {
    return setMethodReturnType(new Computable.PredefinedValueComputable<PsiType>(returnType));
  }

  public LightMethodBuilder setMethodReturnType(@NotNull final String returnType) {
    return setMethodReturnType(new Computable.NotNullCachedComputable<PsiType>() {
      @NotNull
      @Override
      protected PsiType internalCompute() {
        return JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory().createTypeByFQClassName(returnType, getResolveScope());
      }
    });
  }

  public PsiTypeElement getReturnTypeElement() {
    return null;
  }

  @NotNull
  public PsiParameterList getParameterList() {
    return myParameterList;
  }

  public LightMethodBuilder addParameter(@NotNull PsiParameter parameter) {
    ((LightParameterListBuilder)myParameterList).addParameter(parameter);
    return this;
  }

  public LightMethodBuilder addParameter(@NotNull String name, @NotNull String type) {
    return addParameter(name, JavaPsiFacade.getElementFactory(getProject()).createTypeFromText(type, this));
  }
  
  public LightMethodBuilder addParameter(@NotNull String name, @NotNull PsiType type) {
    return addParameter(new LightParameter(name, type, this, StdLanguages.JAVA));
  }

  public LightMethodBuilder addParameter(@NotNull String name, @NotNull PsiType type, boolean isVarArgs) {
    if (isVarArgs && !(type instanceof PsiEllipsisType)) {
      type = new PsiEllipsisType(type);
    }
    return addParameter(new LightParameter(name, type, this, StdLanguages.JAVA, isVarArgs));
  }

  public LightMethodBuilder addException(PsiClassType type) {
    ((LightReferenceListBuilder)myThrowsList).addReference(type);
    return this;
  }

  public LightMethodBuilder addException(String fqName) {
    ((LightReferenceListBuilder)myThrowsList).addReference(fqName);
    return this;
  }


  @NotNull
  public PsiReferenceList getThrowsList() {
    return myThrowsList;
  }

  public PsiCodeBlock getBody() {
    return null;
  }

  public LightMethodBuilder setConstructor(boolean constructor) {
    myConstructor = constructor;
    return this;
  }

  public boolean isConstructor() {
    return myConstructor;
  }

  public boolean isVarArgs() {
    //todo
    return false;
  }

  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @NotNull
  public PsiMethod[] findSuperMethods() {
    return PsiSuperMethodImplUtil.findSuperMethods(this);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, parentClass);
  }

  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess);
  }

  public PsiMethod findDeepestSuperMethod() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethod(this);
  }

  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethods(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitMethod(this);
    }
  }

  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  public LightMethodBuilder setContainingClass(PsiClass containingClass) {
    myContainingClass = containingClass;
    return this;
  }

  public LightMethodBuilder setMethodKind(String debugKindName) {
    myMethodKind = debugKindName;
    return this;
  }

  public String toString() {
    return myMethodKind + ":" + getName();
  }

  public Icon getElementIcon(final int flags) {
    Icon methodIcon = myBaseIcon != null ? myBaseIcon :
                      hasModifierProperty(PsiModifier.ABSTRACT) ? PlatformIcons.ABSTRACT_METHOD_ICON : PlatformIcons.METHOD_ICON;
    RowIcon baseIcon = ElementPresentationUtil.createLayeredIcon(methodIcon, this, false);
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  public LightMethodBuilder setBaseIcon(Icon baseIcon) {
    myBaseIcon = baseIcon;
    return this;
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isMethodEquivalentTo(this, another);
  }

  @NotNull
  public SearchScope getUseScope() {
    return PsiImplUtil.getMemberUseScope(this);
  }

  @Nullable
  @Override
  public PsiFile getContainingFile() {
    final PsiClass containingClass = getContainingClass();
    return containingClass == null ? null : containingClass.getContainingFile();
  }

  @Override
  public PsiElement getContext() {
    final PsiElement navElement = getNavigationElement();
    if (navElement != this) {
      return navElement;
    }

    final PsiClass cls = getContainingClass();
    if (cls != null) {
      return cls;
    }

    return getContainingFile();
  }

  public PsiMethodReceiver getMethodReceiver() {
    return null;
  }
  @Nullable
  public PsiType getReturnTypeNoResolve() {
    return getReturnType();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LightMethodBuilder that = (LightMethodBuilder)o;

    if (myConstructor != that.myConstructor) return false;
    if (myBaseIcon != null ? !myBaseIcon.equals(that.myBaseIcon) : that.myBaseIcon != null) return false;
    if (myContainingClass != null ? !myContainingClass.equals(that.myContainingClass) : that.myContainingClass != null) return false;
    if (!myMethodKind.equals(that.myMethodKind)) return false;
    if (!myModifierList.equals(that.myModifierList)) return false;
    if (!myName.equals(that.myName)) return false;
    if (!myParameterList.equals(that.myParameterList)) return false;
    if (myReturnType != null ? !myReturnType.equals(that.myReturnType) : that.myReturnType != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + (myReturnType != null ? myReturnType.hashCode() : 0);
    result = 31 * result + myModifierList.hashCode();
    result = 31 * result + myParameterList.hashCode();
    result = 31 * result + (myBaseIcon != null ? myBaseIcon.hashCode() : 0);
    result = 31 * result + (myContainingClass != null ? myContainingClass.hashCode() : 0);
    result = 31 * result + (myConstructor ? 1 : 0);
    result = 31 * result + myMethodKind.hashCode();
    return result;
  }
}
