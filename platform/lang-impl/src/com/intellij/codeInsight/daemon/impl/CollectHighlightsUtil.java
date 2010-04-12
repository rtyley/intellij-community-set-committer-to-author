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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CollectHighlightsUtil {
  private static final ExtensionPointName<Condition<PsiElement>> EP_NAME = ExtensionPointName.create("com.intellij.elementsToHighlightFilter");

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil");

  private CollectHighlightsUtil() {
  }

  @NotNull
  public static List<PsiElement> getElementsInRange(PsiElement root, final int startOffset, final int endOffset) {
    return getElementsInRange(root, startOffset, endOffset, false);
  }

  @NotNull
  public static List<PsiElement> getElementsInRange(@NotNull PsiElement root,
                                                    final int startOffset,
                                                    final int endOffset,
                                                    boolean includeAllParents) {
    PsiElement commonParent = findCommonParent(root, startOffset, endOffset);
    if (commonParent == null) return new ArrayList<PsiElement>();
    final List<PsiElement> list = getElementsToHighlight(commonParent, startOffset, endOffset);

    PsiElement parent = commonParent;
    while (parent != null && parent != root) {
      list.add(parent);
      parent = includeAllParents ? parent.getParent() : null;
    }

    list.add(root);

    return list;
  }

  private static final int STARTING_TREE_HEIGHT = 100;
  
  private static List<PsiElement> getElementsToHighlight(final PsiElement commonParent, final int startOffset, final int endOffset) {
    final List<PsiElement> result = new ArrayList<PsiElement>();
    final int currentOffset = commonParent.getTextRange().getStartOffset();
    final Condition<PsiElement>[] filters = Extensions.getExtensions(EP_NAME);

    final PsiElementVisitor visitor = new PsiElementVisitor() {
      int offset = currentOffset;
      
      final TIntArrayList starts = new TIntArrayList(STARTING_TREE_HEIGHT);
      final List<PsiElement> elements = new ArrayList<PsiElement>(STARTING_TREE_HEIGHT);
      final List<PsiElement> children = new ArrayList<PsiElement>(STARTING_TREE_HEIGHT);
      
      @Override public void visitElement(PsiElement element) {
        children.add(PsiUtilBase.NULL_PSI_ELEMENT);
        
        while (true) {
          ProgressManager.checkCanceled();
          
          PsiElement child = children.remove(children.size() - 1);

          for (Condition<PsiElement> filter : filters) {
            if (!filter.value(element)) {
              assert child == PsiUtilBase.NULL_PSI_ELEMENT;
              child = null; // do not want to process children
              break;
            }
          }

          boolean startChildrenVisiting = false;

          if (child == PsiUtilBase.NULL_PSI_ELEMENT) {
            startChildrenVisiting = true;
            child = element.getFirstChild();
          }

          if (child == null) {
            if (startChildrenVisiting) {
              // leaf element
              offset += element.getTextLength();
            }
            
            if (elements.size() == 0) break;
            int start = starts.remove(starts.size() - 1);
            if (startOffset <= start && offset <= endOffset) result.add(element);
            
            element = elements.remove(elements.size() - 1);
          }
          else {
            // composite element
            if (offset <= endOffset) {
              starts.add(offset);
              children.add(child.getNextSibling());
              children.add(PsiUtilBase.NULL_PSI_ELEMENT);
              elements.add(element);
              element = child;
            } else {
              break;
            }
          }
        }
      }
    };
    commonParent.accept(visitor);
        
    return result;
  }


  @Nullable
  public static PsiElement findCommonParent(final PsiElement root, final int startOffset, final int endOffset) {
    if (startOffset == endOffset) return null;
    final PsiElement left = findElementAtInRoot(root, startOffset);
    PsiElement right = findElementAtInRoot(root, endOffset - 1);
    if (left == null || right == null) return null;

    PsiElement commonParent = PsiTreeUtil.findCommonParent(left, right);
    LOG.assertTrue(commonParent != null);
    LOG.assertTrue(commonParent.getTextRange() != null);

    while (commonParent.getParent() != null && commonParent.getTextRange().equals(commonParent.getParent().getTextRange())) {
      commonParent = commonParent.getParent();
    }
    return commonParent;
  }

  @Nullable
  private static PsiElement findElementAtInRoot(final PsiElement root, final int offset) {
    if (root instanceof PsiFile) {
      return ((PsiFile)root).getViewProvider().findElementAt(offset, root.getLanguage());
    }
    return root.findElementAt(offset);
  }

  public static boolean shouldHighlightFile(@Nullable final PsiFile psiFile) {
    if (psiFile == null) return true;

    final ProblemHighlightFilter[] filters = ProblemHighlightFilter.EP_NAME.getExtensions();
    for (ProblemHighlightFilter filter : filters) {
      if (!filter.shouldHighlight(psiFile)) return false;
    }

    return true;
  }

  public static boolean isOutsideSourceRootJavaFile(@Nullable PsiFile psiFile) {
    return psiFile != null && psiFile.getFileType() == StdFileTypes.JAVA && isOutsideSourceRoot(psiFile);
  }

  public static boolean isOutsideSourceRoot(@Nullable PsiFile psiFile) {
    if (psiFile == null) return false;
    if (psiFile instanceof PsiCodeFragment) return false;
    final VirtualFile file = psiFile.getVirtualFile();
    if (file == null) return false;
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(psiFile.getProject()).getFileIndex();
    return !projectFileIndex.isInSource(file) && !projectFileIndex.isInLibraryClasses(file);
  }
}
