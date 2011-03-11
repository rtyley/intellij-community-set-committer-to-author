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
package com.intellij.refactoring.util;

import com.intellij.psi.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

public class MoveRenameUsageInfo extends UsageInfo{
  private SmartPsiElementPointer myReferencedElementPointer = null;
  private PsiElement myReferencedElement;

  private PsiReference myReference;
  private RangeMarker myReferenceRangeMarker = null;
  private boolean myDynamicUsage;

  public MoveRenameUsageInfo(PsiReference reference, PsiElement referencedElement){
    this(reference.getElement(), reference, referencedElement);
  }

  public MoveRenameUsageInfo(PsiElement element, PsiReference reference, PsiElement referencedElement){
    super(element);
    init(element, reference, referencedElement);

  }

  public MoveRenameUsageInfo(PsiElement element, PsiReference reference, int startOffset, int endOffset, PsiElement referencedElement, boolean nonCodeUsage){
    super(element, startOffset, endOffset, nonCodeUsage);
    init(element, reference, referencedElement);
  }

  public MoveRenameUsageInfo(PsiElement element, PsiReference reference, int startOffset, int endOffset, PsiElement referencedElement, boolean nonCodeUsage, boolean isDynamicUsage){
    super(element, startOffset, endOffset, nonCodeUsage);
    myDynamicUsage = isDynamicUsage;
    init(element, reference, referencedElement);
  }

  private void init(final PsiElement element, PsiReference reference, final PsiElement referencedElement) {
    final Project project = element.getProject();
    myReferencedElement = referencedElement;
    if (referencedElement != null) {
      myReferencedElementPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(referencedElement);
    }
    if (reference == null) reference = element.getReference();
    PsiFile containingFile = element.getContainingFile();
    if (reference == null) reference = containingFile.findReferenceAt(element.getTextRange().getStartOffset());
    myReference = reference;
    if (reference != null) {
      Document document = PsiDocumentManager.getInstance(project).getDocument(containingFile);
      assert document != null : containingFile;
      int elementStart = reference.getElement().getTextRange().getStartOffset();
      myReferenceRangeMarker = document.createRangeMarker(elementStart + reference.getRangeInElement().getStartOffset(),
                                                          elementStart + reference.getRangeInElement().getEndOffset());
    }
  }

  @Nullable
  public PsiElement getUpToDateReferencedElement() {
    return myReferencedElementPointer == null ? null : myReferencedElementPointer.getElement();
  }

  @Nullable
  public PsiElement getReferencedElement() {
    return myReferencedElement;
  }

  @Nullable
  public PsiReference getReference() {
    if (myReference != null) {
      final PsiElement element = myReference.getElement();
      if (element != null && element.isValid()) return myReference;
    }

    if (myReferenceRangeMarker == null) return null;
    final PsiElement element = getElement();
    if (element == null) return null;
    final int start = myReferenceRangeMarker.getStartOffset() - element.getTextRange().getStartOffset();
    final int end = myReferenceRangeMarker.getEndOffset() - element.getTextRange().getStartOffset();
    final PsiReference reference = element.findReferenceAt(start);
    if (reference == null) return null;
    final TextRange rangeInElement = reference.getRangeInElement();
    if (rangeInElement.getStartOffset() != start || rangeInElement.getEndOffset() != end) return null;
    return reference;
  }

  public boolean isDynamicUsage() {
    return myDynamicUsage;
  }
}
