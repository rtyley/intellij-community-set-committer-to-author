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
package com.intellij.usageView;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UsageInfo {
  public static final UsageInfo[] EMPTY_ARRAY = new UsageInfo[0];
  private static final Logger LOG = Logger.getInstance("#com.intellij.usageView.UsageInfo");
  private final SmartPsiElementPointer<?> mySmartPointer;
  public final int startOffset; // in navigation element
  public final int endOffset; // in navigation element

  public final boolean isNonCodeUsage;

  public UsageInfo(@NotNull PsiElement element, int startOffset, int endOffset, boolean isNonCodeUsage) {
    LOG.assertTrue(element.isValid());
    element = element.getNavigationElement();
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(element.getProject());
    mySmartPointer = smartPointerManager.createSmartPsiElementPointer(element);

    if (startOffset == -1 && endOffset == -1) {
      // calculate natural element range
      TextRange range = element.getTextRange();
      if (range == null) {
        LOG.error("text range null for " + element + "; " + element.getClass());
      }
      startOffset = element.getTextOffset() - range.getStartOffset();
      endOffset = range.getEndOffset() - range.getStartOffset();
    }
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    this.isNonCodeUsage = isNonCodeUsage;
    LOG.assertTrue(startOffset >= 0, startOffset);
    LOG.assertTrue(endOffset >= startOffset, endOffset-startOffset);
  }

  public UsageInfo(@NotNull PsiElement element, boolean isNonCodeUsage) {
    this(element, -1, -1, isNonCodeUsage);
  }

  public UsageInfo(@NotNull PsiElement element, int startOffset, int endOffset) {
    this(element, startOffset, endOffset, false);
  }

  public UsageInfo(@NotNull PsiReference reference) {
    this(reference.getElement(), reference.getRangeInElement().getStartOffset(), reference.getRangeInElement().getEndOffset());
  }

  public UsageInfo(@NotNull PsiQualifiedReference reference) {
    this((PsiElement)reference);
  }

  public UsageInfo(@NotNull PsiElement element) {
    this(element, false);
  }

  @Nullable
  public PsiElement getElement() { // SmartPointer is used to fix SCR #4572, hotya eto krivo i nado vse perepisat'
    return mySmartPointer.getElement();
  }

  @Nullable
  public PsiReference getReference() {
    return getElement().getReference();
  }

  public TextRange getRange() {
    return new TextRange(startOffset, endOffset);
  }

  /**
   * Override this method if you want a tooltip to be displayed for this usage
   */
  public String getTooltipText () {
    return null;
  }

  public final void navigateTo(boolean requestFocus) {
    int offset = getNavigationOffset();
    VirtualFile file = getVirtualFile();
    Project project = getProject();
    FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file, offset), requestFocus);
  }

  public int getNavigationOffset() {
    PsiElement element = getElement();
    if (element == null) return -1;
    TextRange range = element.getTextRange();
    return range.getStartOffset() + startOffset;
  }
  public Segment getSegment() {
    PsiElement element = getElement();
    if (element == null) return null;
    TextRange range = element.getTextRange();
    return new TextRange(range.getStartOffset() + startOffset, Math.min(range.getEndOffset(), range.getStartOffset() + endOffset));
  }

  public Project getProject() {
    return mySmartPointer.getProject();
  }

  public final boolean isWritable() {
    PsiElement element = getElement();
    return element == null || element.isWritable();
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!getClass().equals(o.getClass())) return false;

    final UsageInfo usageInfo = (UsageInfo)o;

    if (endOffset != usageInfo.endOffset) return false;
    if (isNonCodeUsage != usageInfo.isNonCodeUsage) return false;
    if (startOffset != usageInfo.startOffset) return false;

    return SmartPointerManager.getInstance(getProject()).pointToTheSameElement(mySmartPointer, usageInfo.mySmartPointer);
  }

  public int hashCode() {
    int result = mySmartPointer != null ? mySmartPointer.hashCode() : 0;
    result = 29 * result + startOffset;
    result = 29 * result + endOffset;
    result = 29 * result + (isNonCodeUsage ? 1 : 0);
    return result;
  }

  @Nullable
  public PsiFile getFile() {
    return mySmartPointer.getContainingFile();
  }

  @Nullable
  public VirtualFile getVirtualFile() {
    return mySmartPointer.getVirtualFile();
  }

  public void dispose() {
    ((Disposable)mySmartPointer).dispose();
  }
}
