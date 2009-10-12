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

package com.intellij.psi.impl;

import com.intellij.extapi.psi.PsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public abstract class FakePsiElement extends PsiElementBase implements PsiNamedElement, ItemPresentation {

  public ItemPresentation getPresentation() {
    return this;
  }

  @NotNull
  public Language getLanguage() {
    return Language.ANY;
  }

  @NotNull
  public PsiElement[] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  @Nullable
  public PsiElement getFirstChild() {
    return null;
  }

  @Nullable
  public PsiElement getLastChild() {
    return null;
  }

  @Nullable
  public PsiElement getNextSibling() {
    return null;
  }

  @Nullable
  public PsiElement getPrevSibling() {
    return null;
  }

  @Nullable
  public TextRange getTextRange() {
    return null;
  }

  public int getStartOffsetInParent() {
    return 0;
  }

  public int getTextLength() {
    return 0;
  }

  @Nullable
  public PsiElement findElementAt(int offset) {
    return null;
  }

  public int getTextOffset() {
    return 0;
  }

  @Nullable
  @NonNls
  public String getText() {
    return null;
  }

  @NotNull
  public char[] textToCharArray() {
    return new char[0];
  }

  public boolean textContains(char c) {
    return false;
  }

  @Nullable
  public ASTNode getNode() {
    return null;
  }

  public String getPresentableText() {
    return getName();
  }

  @Nullable
  public String getLocationString() {
    return null;
  }

  public final Icon getIcon(final int flags) {
    return super.getIcon(flags);
  }

  protected final Icon getElementIcon(final int flags) {
    return super.getElementIcon(flags);
  }

  @Nullable
  public Icon getIcon(boolean open) {
    return null;
  }

  @Nullable
  public TextAttributesKey getTextAttributesKey() {
    return null;
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    return null;
  }
}
