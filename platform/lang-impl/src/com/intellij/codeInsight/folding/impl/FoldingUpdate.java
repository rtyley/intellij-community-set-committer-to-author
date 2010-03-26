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

package com.intellij.codeInsight.folding.impl;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FoldingUpdate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.FoldingUpdate");

  private static final Key<CachedValue<Runnable>> CODE_FOLDING_KEY = Key.create("code folding");

  private static final Comparator<PsiElement> COMPARE_BY_OFFSET = new Comparator<PsiElement>() {
    public int compare(PsiElement element, PsiElement element1) {
      int startOffsetDiff = element.getTextRange().getStartOffset() - element1.getTextRange().getStartOffset();
      return startOffsetDiff == 0 ? element.getTextRange().getEndOffset() - element1.getTextRange().getEndOffset() : startOffsetDiff;
    }
  };

  private FoldingUpdate() {
  }

  @Nullable
  static Runnable updateFoldRegions(@NotNull final Editor editor, @NotNull PsiFile file, final boolean applyDefaultState, final boolean quick) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    final Project project = file.getProject();
    final Document document = editor.getDocument();
    LOG.assertTrue(!PsiDocumentManager.getInstance(project).isUncommited(document));

    CachedValue<Runnable> value = editor.getUserData(CODE_FOLDING_KEY);
    if (value != null && value.hasUpToDateValue() && !applyDefaultState) return null;
    if (quick) return getUpdateResult(file, document, quick, project, editor, applyDefaultState).getValue();
    
    return CachedValuesManager.getManager(project).getCachedValue(editor, CODE_FOLDING_KEY, new CachedValueProvider<Runnable>() {
      public Result<Runnable> compute() {
        Document document = editor.getDocument();
        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
        return getUpdateResult(file, document, quick, project, editor, applyDefaultState);
      }
    }, false);
  }

  private static CachedValueProvider.Result<Runnable> getUpdateResult(PsiFile file,
                                                                      Document document,
                                                                      boolean quick,
                                                                      final Project project,
                                                                      final Editor editor,
                                                                      final boolean applyDefaultState) {
    final TreeMap<PsiElement, FoldingDescriptor> elementsToFoldMap = new TreeMap<PsiElement, FoldingDescriptor>(COMPARE_BY_OFFSET);
    getFoldingsFor(file instanceof PsiCompiledElement ? (PsiFile)((PsiCompiledElement)file).getMirror() : file, document, elementsToFoldMap, quick);

    Runnable runnable = new Runnable() {
      public void run() {
        UpdateFoldRegionsOperation operation =
          new UpdateFoldRegionsOperation(project, editor, elementsToFoldMap, applyDefaultState, false);
        editor.getFoldingModel().runBatchFoldingOperationDoNotCollapseCaret(operation);
      }
    };
    Set<Object> dependencies = new HashSet<Object>();
    dependencies.add(document);
    for (FoldingDescriptor descriptor : elementsToFoldMap.values()) {
      dependencies.addAll(descriptor.getDependencies());
    }
    return CachedValueProvider.Result.create(runnable, ArrayUtil.toObjectArray(dependencies));
  }

  private static final Key<Object> LAST_UPDATE_INJECTED_STAMP_KEY = Key.create("LAST_UPDATE_INJECTED_STAMP_KEY");
  @Nullable
  public static Runnable updateInjectedFoldRegions(@NotNull final Editor editor, @NotNull PsiFile file) {
    if (file instanceof PsiCompiledElement) return null;
    ApplicationManager.getApplication().assertReadAccessAllowed();

    final Project project = file.getProject();
    Document document = editor.getDocument();
    LOG.assertTrue(!PsiDocumentManager.getInstance(project).isUncommited(document));

    final long timeStamp = document.getModificationStamp();
    Object lastTimeStamp = editor.getUserData(LAST_UPDATE_INJECTED_STAMP_KEY);
    if (lastTimeStamp instanceof Long && ((Long)lastTimeStamp).longValue() == timeStamp) return null;

    final TreeMap<PsiElement, FoldingDescriptor> elementsToFoldMap = new TreeMap<PsiElement, FoldingDescriptor>(COMPARE_BY_OFFSET);

    List<DocumentWindow> injectedDocuments = InjectedLanguageUtil.getCachedInjectedDocuments(file);
    if (injectedDocuments.isEmpty()) return null;
    for (DocumentWindow injectedDocument : injectedDocuments) {
      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(injectedDocument);
      if (psiFile == null || !psiFile.isValid() || !injectedDocument.isValid()) continue;
      getFoldingsFor(psiFile, injectedDocument, elementsToFoldMap, false);
    }

    final Runnable operation = new UpdateFoldRegionsOperation(project, editor, elementsToFoldMap, false, true);
    return new Runnable() {
      public void run() {
        editor.getFoldingModel().runBatchFoldingOperationDoNotCollapseCaret(operation);
        editor.putUserData(LAST_UPDATE_INJECTED_STAMP_KEY, timeStamp);
      }
    };
  }

  private static void getFoldingsFor(PsiFile file, Document document, TreeMap<PsiElement, FoldingDescriptor> elementsToFoldMap, boolean quick) {
    final FileViewProvider viewProvider = file.getViewProvider();
    for (final Language language : viewProvider.getLanguages()) {
      final PsiFile psi = viewProvider.getPsi(language);
      final FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(language);
      if (psi != null && foldingBuilder != null) {
        for (FoldingDescriptor descriptor : LanguageFolding.buildFoldingDescriptors(foldingBuilder, psi, document, quick)) {
          elementsToFoldMap.put(descriptor.getElement().getPsi(), descriptor);
        }
      }
    }
  }

}
