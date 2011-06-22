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
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.ui.RowIcon;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author ven
 */
public class ClsClassObjectAccessExpressionImpl extends ClsElementImpl implements PsiClassObjectAccessExpression {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsClassObjectAccessExpressionImpl");
  private final ClsTypeElementImpl myTypeElement;
  private final ClsElementImpl myParent;
  @NonNls private static final String CLASS_ENDING = ".class";

  public ClsClassObjectAccessExpressionImpl(String canonicalClassText, ClsElementImpl parent) {
    myParent = parent;
    myTypeElement = new ClsTypeElementImpl(this, canonicalClassText, ClsTypeElementImpl.VARIANCE_NONE);
  }

  public void appendMirrorText(final int indentLevel, final StringBuilder buffer) {
    myTypeElement.appendMirrorText(0, buffer);
    buffer.append(CLASS_ENDING);
  }

  public void setMirror(@NotNull TreeElement element) {
    setMirrorCheckingType(element, null);

    PsiClassObjectAccessExpression mirror = (PsiClassObjectAccessExpression)SourceTreeToPsiMap.treeElementToPsi(element);
      ((ClsElementImpl)getOperand()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getOperand()));
  }

  @NotNull
  public PsiElement[] getChildren() {
    return new PsiElement[]{myTypeElement};
  }

  public PsiElement getParent() {
    return myParent;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitClassObjectAccessExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @NotNull
  public PsiTypeElement getOperand() {
    return myTypeElement;
  }

  public PsiType getType() {
    return PsiImplUtil.getType(this);
  }

  public String getText() {
    final StringBuilder buffer = new StringBuilder();
    appendMirrorText(0, buffer);
    return buffer.toString();
  }

  public Icon getElementIcon(final int flags) {
    final RowIcon rowIcon = createLayeredIcon(PlatformIcons.FIELD_ICON, 0);
    rowIcon.setIcon(PlatformIcons.PUBLIC_ICON, 1);
    return rowIcon;
  }
}
