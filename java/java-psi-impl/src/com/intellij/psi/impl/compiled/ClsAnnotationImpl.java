/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.compiled;

import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class ClsAnnotationImpl extends ClsRepositoryPsiElement<PsiAnnotationStub> implements PsiAnnotation, Navigatable {
  private ClsJavaCodeReferenceElementImpl myReferenceElement;
  private ClsAnnotationParameterListImpl myParameterList;

  public ClsAnnotationImpl(final PsiAnnotationStub stub) {
    super(stub);
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    buffer.append("@").append(getReferenceElement().getCanonicalText());
    appendText(getParameterList(), indentLevel, buffer);
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);
    PsiAnnotation mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);
    setMirror(getNameReferenceElement(), mirror.getNameReferenceElement());
    setMirror(getParameterList(), mirror.getParameterList());
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    return new PsiElement[]{getReferenceElement(), getParameterList()};
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotation(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  @NotNull
  public PsiAnnotationParameterList getParameterList() {
    synchronized (LAZY_BUILT_LOCK) {
      if (myParameterList == null) {
        PsiAnnotationParameterList paramList = PsiTreeUtil.getRequiredChildOfType(getStub().getPsiElement(), PsiAnnotationParameterList.class);
        myParameterList = new ClsAnnotationParameterListImpl(this, paramList.getAttributes());
      }
      return myParameterList;
    }
  }

  @Override
  @Nullable
  public String getQualifiedName() {
    if (getReferenceElement() == null) return null;
    return getReferenceElement().getCanonicalText();
  }

  @Override
  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    return getReferenceElement();
  }

  @Override
  public PsiAnnotationMemberValue findAttributeValue(String attributeName) {
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  @Override
  @Nullable
  public PsiAnnotationMemberValue findDeclaredAttributeValue(@NonNls final String attributeName) {
    return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
  }

  @Override
  public <T extends PsiAnnotationMemberValue> T setDeclaredAttributeValue(@NonNls String attributeName, T value) {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  @Override
  public String getText() {
    final StringBuilder buffer = new StringBuilder();
    appendMirrorText(0, buffer);
    return buffer.toString();
  }

  @Override
  public PsiMetaData getMetaData() {
    return MetaRegistry.getMetaBase(this);
  }

  private ClsJavaCodeReferenceElementImpl getReferenceElement() {
    synchronized (LAZY_BUILT_LOCK) {
      if (myReferenceElement == null) {
        String text = PsiTreeUtil.getRequiredChildOfType(getStub().getPsiElement(), PsiJavaCodeReferenceElement.class).getText();
        myReferenceElement = new ClsJavaCodeReferenceElementImpl(this, text);
      }

      return myReferenceElement;
    }
  }

  @Override
  public PsiAnnotationOwner getOwner() {
    return (PsiAnnotationOwner)getParent();//todo
  }
}
