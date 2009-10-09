package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;

import java.util.HashSet;

class PackageLocalsUsageCollector extends JavaRecursiveElementWalkingVisitor {
  private final HashMap<PsiElement,HashSet<PsiElement>> myReported = new HashMap<PsiElement, HashSet<PsiElement>>();
  private final PsiElement[] myElementsToMove;
  private final MultiMap<PsiElement, String> myConflicts;
  private final PackageWrapper myTargetPackage;

  public PackageLocalsUsageCollector(final PsiElement[] elementsToMove, final PackageWrapper targetPackage, MultiMap<PsiElement,String> conflicts) {
    myElementsToMove = elementsToMove;
    myConflicts = conflicts;
    myTargetPackage = targetPackage;
  }

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
    super.visitReferenceExpression(expression);
    visitReferenceElement(expression);
  }

  @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    super.visitReferenceElement(reference);
    PsiElement resolved = reference.resolve();
    visitResolvedReference(resolved, reference);
  }

  private void visitResolvedReference(PsiElement resolved, PsiJavaCodeReferenceElement reference) {
    if (resolved instanceof PsiModifierListOwner) {
      final PsiModifierList modifierList = ((PsiModifierListOwner)resolved).getModifierList();
      if (PsiModifier.PACKAGE_LOCAL.equals(VisibilityUtil.getVisibilityModifier(modifierList))) {
        PsiFile aFile = resolved.getContainingFile();
        if (aFile != null && !isInsideMoved(resolved)) {
          final PsiDirectory containingDirectory = aFile.getContainingDirectory();
          if (containingDirectory != null) {
            PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(containingDirectory);
            if (aPackage != null && !myTargetPackage.equalToPackage(aPackage)) {
              HashSet<PsiElement> reportedRefs = myReported.get(resolved);
              if (reportedRefs == null) {
                reportedRefs = new HashSet<PsiElement>();
                myReported.put(resolved, reportedRefs);
              }
              PsiElement container = ConflictsUtil.getContainer(reference);
              if (!reportedRefs.contains(container)) {
                final String message = RefactoringBundle.message("0.uses.a.package.local.1",
                                                                 RefactoringUIUtil.getDescription(container, true),
                                                                 RefactoringUIUtil.getDescription(resolved, true));
                myConflicts.putValue(resolved, CommonRefactoringUtil.capitalize(message));
                reportedRefs.add(container);
              }
            }
          }
        }
      }
    }
  }

  private boolean isInsideMoved(PsiElement place) {
    for (PsiElement element : myElementsToMove) {
      if (element instanceof PsiClass) {
        if (PsiTreeUtil.isAncestor(element, place, false)) return true;
      }
    }
    return false;
  }
}