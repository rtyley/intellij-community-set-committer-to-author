/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class ProblemsHolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ProblemsHolder");
  private final InspectionManager myManager;
  private final PsiFile myFile;
  private List<ProblemDescriptor> myProblems = null;

  public ProblemsHolder(@NotNull InspectionManager manager, @NotNull PsiFile file) {
    myManager = manager;
    myFile = file;
  }

  public void registerProblem(PsiElement psiElement, @Nls String descriptionTemplate, LocalQuickFix... fixes) {
    registerProblem(psiElement, descriptionTemplate, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fixes);
  }

  public void registerProblem(PsiElement psiElement,
                              String descriptionTemplate,
                              ProblemHighlightType highlightType,
                              LocalQuickFix... fixes) {
    registerProblem(myManager.createProblemDescriptor(psiElement, descriptionTemplate, fixes, highlightType));
  }

  public void registerProblem(ProblemDescriptor problemDescriptor) {
    if (myProblems == null) {
      myProblems = new ArrayList<ProblemDescriptor>(1);
    }
    PsiElement element = problemDescriptor.getPsiElement();
    if (element != null && !isInPsiFile(element)) {
      LOG.error("Reported element " + element + " is not from the file '" + myFile + "' the inspection was invoked for. Message:" + problemDescriptor.getDescriptionTemplate());
    }
    myProblems.add(problemDescriptor);
  }

  private boolean isInPsiFile(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    return ArrayUtil.indexOf(myFile.getPsiRoots(), file) != -1;
  }

  public void registerProblem(PsiReference reference, String descriptionTemplate, ProblemHighlightType highlightType) {
    LocalQuickFix[] fixes = null;
    if (reference instanceof LocalQuickFixProvider) {
      fixes = ((LocalQuickFixProvider)reference).getQuickFixes();
    }

    registerProblem(myManager.createProblemDescriptor(reference.getElement(), reference.getRangeInElement(), descriptionTemplate, highlightType, fixes));
  }

  public void registerProblem(PsiReference reference) {
    assert reference instanceof EmptyResolveMessageProvider;
    registerProblem(reference, ((EmptyResolveMessageProvider)reference).getUnresolvedMessagePattern(), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
  }

  public void registerProblem(@NotNull final PsiElement psiElement,
                              @NotNull final String message,
                              final ProblemHighlightType highlightType,
                              final TextRange rangeInElement,
                              final LocalQuickFix... fixes) {

    final ProblemDescriptor descriptor = myManager.createProblemDescriptor(psiElement, rangeInElement, message, highlightType, fixes);
    registerProblem(descriptor);
  }

  public void registerProblem(@NotNull final PsiElement psiElement,
                              final TextRange rangeInElement,
                              @NotNull final String message,
                              final LocalQuickFix... fixes) {

    final ProblemDescriptor descriptor = myManager.createProblemDescriptor(psiElement, rangeInElement, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fixes);
    registerProblem(descriptor);
  }

  @Nullable
  public List<ProblemDescriptor> getResults() {
    final List<ProblemDescriptor> problems = myProblems;
    myProblems = null;
    return problems;
  }

  @Nullable
  public ProblemDescriptor[] getResultsArray() {
    final List<ProblemDescriptor> problems = myProblems;
    myProblems = null;
    return problems == null ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  public final InspectionManager getManager() {
    return myManager;
  }
  public boolean hasResults() {
    return myProblems != null && !myProblems.isEmpty();
  }
}
