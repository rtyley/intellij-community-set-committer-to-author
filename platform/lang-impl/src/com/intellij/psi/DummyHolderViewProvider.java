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
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DummyHolderViewProvider extends UserDataHolderBase implements FileViewProvider{
  private DummyHolder myHolder;
  private final PsiManager myManager;
  private final long myModificationStamp;
  private final LightVirtualFile myLightVirtualFile = new LightVirtualFile("DummyHolder");

  public DummyHolderViewProvider(@NotNull PsiManager manager) {
    myManager = manager;
    myModificationStamp = LocalTimeCounter.currentTime();
  }

  @NotNull
  public PsiManager getManager() {
    return myManager;
  }

  @Nullable
  public Document getDocument() {
    return FileDocumentManager.getInstance().getDocument(getVirtualFile());
  }

  @NotNull
  public CharSequence getContents() {
    return myHolder != null ? myHolder.getNode().getText() : "";
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myLightVirtualFile;
  }

  @NotNull
  public Language getBaseLanguage() {
    return myHolder.getLanguage();
  }

  @NotNull
  public Set<Language> getLanguages() {
    return Collections.singleton(getBaseLanguage());
  }

  public PsiFile getPsi(@NotNull Language target) {
    ((PsiManagerEx)myManager).getFileManager().setViewProvider(getVirtualFile(), this);
    return target == getBaseLanguage() ? myHolder : null;
  }

  @NotNull
  public List<PsiFile> getAllFiles() {
    return Collections.singletonList(getPsi(getBaseLanguage()));
  }

  public void beforeContentsSynchronized() {}

  public void contentsSynchronized() {}

  public boolean isEventSystemEnabled() {
    return false;
  }

  public boolean isPhysical() {
    return false;
  }

  public long getModificationStamp() {
    return myModificationStamp;
  }

  public boolean supportsIncrementalReparse(final Language rootLanguage) {
    return true;
  }

  public void rootChanged(PsiFile psiFile) {
  }

  public void setDummyHolder(final DummyHolder dummyHolder) {
    myHolder = dummyHolder;
    //myLightVirtualFile.setContent(null, getContents(), false);
  }

  public FileViewProvider clone(){
    throw new RuntimeException("Clone is not supported for DummyHolderProviders. Use DummyHolder clone directly.");
  }

  public PsiReference findReferenceAt(final int offset) {
    return SharedPsiElementImplUtil.findReferenceAt(getPsi(getBaseLanguage()), offset);
  }

  @Nullable
  public PsiElement findElementAt(final int offset, final Language language) {
    return language == getBaseLanguage() ? findElementAt(offset) : null;
  }


  public PsiElement findElementAt(int offset, Class<? extends Language> lang) {
    if (!lang.isAssignableFrom(getBaseLanguage().getClass())) return null;
    return findElementAt(offset);
  }

  public PsiReference findReferenceAt(final int offsetInElement, @NotNull final Language language) {
    return language == getBaseLanguage() ? findReferenceAt(offsetInElement) : null;
  }

  public boolean isLockedByPsiOperations() {
    return false;
  }

  @NotNull
  @Override
  public FileViewProvider createCopy(final VirtualFile copy) {
    throw new RuntimeException("Clone is not supported for DummyHolderProviders. Use DummyHolder clone directly.");
  }

  public PsiElement findElementAt(final int offset) {
    final LeafElement element = ((PsiFileImpl)getPsi(getBaseLanguage())).calcTreeElement().findLeafElementAt(offset);
    return element != null ? element.getPsi() : null;
  }
}
