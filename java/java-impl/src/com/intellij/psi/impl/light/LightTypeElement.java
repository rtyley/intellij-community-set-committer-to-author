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
package com.intellij.psi.impl.light;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class LightTypeElement extends LightElement implements PsiTypeElement {
  private final PsiType myType;

  public LightTypeElement(PsiManager manager, PsiType type) {
    super(manager, StdFileTypes.JAVA.getLanguage());
    type = PsiUtil.convertAnonymousToBaseType(type);
    myType = type;
  }

  public String toString() {
    return "PsiTypeElement:" + getText();
  }

  public String getText() {
    return myType.getPresentableText();
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public PsiElement copy() {
    return new LightTypeElement(myManager, myType);
  }

  @NotNull
  public PsiType getType() {
    return myType;
  }

  public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
    return null;
  }

  public PsiAnnotationOwner getOwner(PsiAnnotation annotation) {
    return this;
  }

  public PsiType getTypeNoResolve(@NotNull PsiElement context) {
    return getType();
  }

  public boolean isValid() {
    return myType.isValid();
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    return myType.getAnnotations();
  }

  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return myType.findAnnotation(qualifiedName);
  }

  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    throw new IncorrectOperationException();
  }
  
  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    return getAnnotations();
  }

}
