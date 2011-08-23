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

package com.intellij.psi.impl.source.tree;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class LeafPsiElement extends LeafElement implements PsiElement, NavigationItem {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.LeafPsiElement");

  public LeafPsiElement(IElementType type, CharSequence text) {
    super(type, text);
  }

  @Deprecated
  public LeafPsiElement(IElementType type, CharSequence buffer, int startOffset, int endOffset, CharTable table) {
    super(type, table.intern(buffer, startOffset, endOffset));
  }

  @NotNull
  public PsiElement[] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  public PsiElement getFirstChild() {
    return null;
  }

  public PsiElement getLastChild() {
    return null;
  }

  public void acceptChildren(@NotNull PsiElementVisitor visitor) {
  }

  public PsiElement getParent() {
    return SharedImplUtil.getParent(this);
  }

  public PsiElement getNextSibling() {
    return SharedImplUtil.getNextSibling(this);
  }

  public PsiElement getPrevSibling() {
    return SharedImplUtil.getPrevSibling(this);
  }

  public PsiFile getContainingFile() {
    final PsiFile file = SharedImplUtil.getContainingFile(this);
    if (file == null || !file.isValid()) invalid();
    //noinspection ConstantConditions
    return file;
  }

  private void invalid() {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.checkCanceled();
    }

    final StringBuilder builder = new StringBuilder();
    TreeElement element = this;
    while (element != null) {
      if (element != this) builder.append(" / ");
      builder.append(element.getClass().getName()).append(':').append(element.getElementType());
      element = element.getTreeParent();
    }

    throw new PsiInvalidElementAccessException(this, builder.toString());
  }

  public PsiElement findElementAt(int offset) {
    return this;
  }

  public PsiReference findReferenceAt(int offset) {
    return SharedPsiElementImplUtil.findReferenceAt(this, offset);
  }

  public PsiElement copy() {
    ASTNode elementCopy = copyElement();
    return SourceTreeToPsiMap.treeElementToPsi(elementCopy);
  }

  public boolean isValid() {
    return SharedImplUtil.isValid(this);
  }

  public boolean isWritable() {
    return SharedImplUtil.isWritable(this);
  }

  public PsiReference getReference() {
    return null;
  }

  @NotNull
  public PsiReference[] getReferences() {
    return SharedPsiElementImplUtil.getReferences(this);
  }

  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void delete() throws IncorrectOperationException {
    LOG.assertTrue(getTreeParent() != null);
    CheckUtil.checkWritable(this);
    getTreeParent().deleteChildInternal(this);
    invalidate();
  }

  public void checkDelete() throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    return SharedImplUtil.doReplace(this, this, newElement);
  }

  public String toString() {
    return "PsiElement" + "(" + getElementType().toString() + ")";
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitElement(this);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return true;
  }

  public PsiElement getContext() {
    return getParent();
  }

  public PsiElement getNavigationElement() {
    return this;
  }

  public PsiElement getOriginalElement() {
    return this;
  }

  public boolean isPhysical() {
    PsiFile file = getContainingFile();
    return file != null && file.isPhysical();
  }

  @NotNull
  public GlobalSearchScope getResolveScope() {
    return getManager().getFileManager().getResolveScope(this);
  }

  @NotNull
  public SearchScope getUseScope() {
    return getManager().getFileManager().getUseScope(this);
  }

  @NotNull
  public Project getProject() {
    final PsiManager manager = getManager();
    if (manager == null) invalid();
    //noinspection ConstantConditions
    return manager.getProject();
  }

  @NotNull
  public Language getLanguage() {
    return getElementType().getLanguage();
  }

  public ASTNode getNode() {
    return this;
  }

  public PsiElement getPsi() {
    return this;
  }

  public ItemPresentation getPresentation() {
    return null;
  }

  public String getName() {
    return null;
  }

  public void navigate(boolean requestFocus) {
    final Navigatable descriptor = EditSourceUtil.getDescriptor(this);
    if (descriptor != null) {
      descriptor.navigate(requestFocus);
    }
  }

  public boolean canNavigate() {
    return EditSourceUtil.canNavigate(this);
  }

  public boolean canNavigateToSource() {
    return canNavigate();
  }

  public boolean isEquivalentTo(final PsiElement another) {
    return this == another;
  }
}
