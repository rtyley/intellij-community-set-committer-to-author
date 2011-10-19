/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.mock;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class MockPsiDocumentManager extends PsiDocumentManager {
  @Override
  @Nullable
  public PsiFile getPsiFile(@NotNull Document document) {
    throw new UnsupportedOperationException("Method getPsiFile is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public PsiFile getCachedPsiFile(@NotNull Document document) {
    throw new UnsupportedOperationException("Method getCachedPsiFile is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public Document getDocument(@NotNull PsiFile file) {
    return null;
  }

  @Override
  @Nullable
  public Document getCachedDocument(@NotNull PsiFile file) {
    VirtualFile vFile = file.getViewProvider().getVirtualFile();
    return FileDocumentManager.getInstance().getCachedDocument(vFile);
  }

  @Override
  public void commitAllDocuments() {
  }

  @Override
  public void performForCommittedDocument(@NotNull final Document document, @NotNull final Runnable action) {
    action.run();
  }

  @Override
  public void commitDocument(@NotNull Document document) {
  }

  @Override
  @NotNull
  public Document[] getUncommittedDocuments() {
    throw new UnsupportedOperationException("Method getUncommittedDocuments is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean isUncommited(@NotNull Document document) {
    throw new UnsupportedOperationException("Method isUncommited is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean isCommitted(@NotNull Document document) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasUncommitedDocuments() {
    throw new UnsupportedOperationException("Method hasUncommitedDocuments is not yet implemented in " + getClass().getName());
  }

  @Override
  public void commitAndRunReadAction(@NotNull Runnable runnable) {
    throw new UnsupportedOperationException("Method commitAndRunReadAction is not yet implemented in " + getClass().getName());
  }

  @Override
  public <T> T commitAndRunReadAction(@NotNull Computable<T> computation) {
    throw new UnsupportedOperationException("Method commitAndRunReadAction is not yet implemented in " + getClass().getName());
  }

  @Override
  public void addListener(@NotNull Listener listener) {
    throw new UnsupportedOperationException("Method addListener is not yet implemented in " + getClass().getName());
  }

  @Override
  public void removeListener(@NotNull Listener listener) {
    throw new UnsupportedOperationException("Method removeListener is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean isDocumentBlockedByPsi(@NotNull Document doc) {
    throw new UnsupportedOperationException("Method isDocumentBlockedByPsi is not yet implemented in " + getClass().getName());
  }

  @Override
  public void doPostponedOperationsAndUnblockDocument(@NotNull Document doc) {
    throw new UnsupportedOperationException(
      "Method doPostponedOperationsAndUnblockDocument is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean performWhenAllCommitted(@NotNull Runnable action) {
    throw new UnsupportedOperationException();
  }
}
