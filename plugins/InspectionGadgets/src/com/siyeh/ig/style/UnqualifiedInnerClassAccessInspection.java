/*
 * Copyright 2010-2011 Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.HighlightUtils;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class UnqualifiedInnerClassAccessInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreReferencesToLocalInnerClasses = false;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unqualified.inner.class.access.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unqualified.inner.class.access.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message(
        "unqualified.inner.class.access.option"),
      this, "ignoreReferencesToLocalInnerClasses");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnqualifiedInnerClassAccessFix();
  }

  private static class UnqualifiedInnerClassAccessFix
    extends InspectionGadgetsFix {

    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "unqualified.inner.class.access.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiJavaCodeReferenceElement)) {
        return;
      }
      final PsiJavaCodeReferenceElement referenceElement =
        (PsiJavaCodeReferenceElement)element;
      final PsiElement target = referenceElement.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)target;
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String qualifiedName = containingClass.getQualifiedName();
      if (qualifiedName == null) {
        return;
      }
      final PsiFile containingFile = referenceElement.getContainingFile();
      if (!(containingFile instanceof PsiJavaFile)) {
        return;
      }
      final PsiJavaFile javaFile = (PsiJavaFile)containingFile;
      final String innerClassName = aClass.getQualifiedName();
      if (innerClassName == null) {
        return;
      }
      final PsiImportList importList = javaFile.getImportList();
      if (importList == null) {
        return;
      }
      final PsiImportStatement[] importStatements =
        importList.getImportStatements();
      final int importStatementsLength = importStatements.length;
      boolean onDemand = false;
      PsiImportStatement referenceImportStatement = null;
      for (int i = importStatementsLength - 1; i >= 0; i--) {
        final PsiImportStatement importStatement = importStatements[i];
        final String importString = importStatement.getQualifiedName();
        if (importStatement.isOnDemand()) {
          if (qualifiedName.equals(importString)) {
            referenceImportStatement = importStatement;
            onDemand = true;
            break;
          }
        }
        else {
          if (innerClassName.equals(importString)) {
            referenceImportStatement = importStatement;
            break;
          }
        }
      }
      final ReferenceCollector referenceCollector;
      if (onDemand) {
        referenceCollector =
          new ReferenceCollector(qualifiedName, onDemand);
      }
      else {
        referenceCollector =
          new ReferenceCollector(innerClassName, onDemand);
      }
      final PsiClass[] classes = javaFile.getClasses();
      for (PsiClass psiClass : classes) {
        psiClass.accept(referenceCollector);
      }
      final Collection<PsiJavaCodeReferenceElement> references =
        referenceCollector.getReferences();
      final SmartPointerManager pointerManager =
        SmartPointerManager.getInstance(project);
      final List<SmartPsiElementPointer> pointers = new ArrayList();
      for (PsiJavaCodeReferenceElement reference : references) {
        final SmartPsiElementPointer<PsiJavaCodeReferenceElement> pointer =
          pointerManager.createSmartPsiElementPointer(reference);
        pointers.add(pointer);
      }
      if (referenceImportStatement != null) {
        referenceImportStatement.delete();
      }
      ImportUtils.addImportIfNeeded(containingClass, referenceElement);
      final PsiDocumentManager documentManager =
        PsiDocumentManager.getInstance(project);
      final Document document =
        documentManager.getDocument(containingFile);
      if (document == null) {
        return;
      }
      documentManager.doPostponedOperationsAndUnblockDocument(document);
      final String text = buildNewText(javaFile, references,
                                       containingClass, new StringBuilder()).toString();
      document.replaceString(0, document.getTextLength(), text);
      documentManager.commitDocument(document);
      if (pointers.size() > 1) {
        final List<PsiElement> elements = new ArrayList();
        for (SmartPsiElementPointer pointer : pointers) {
          elements.add(pointer.getElement());
        }
        HighlightUtils.highlightElements(elements);
      }
    }


    private static StringBuilder buildNewText(
      PsiElement element,
      Collection<PsiJavaCodeReferenceElement> references,
      PsiClass aClass, StringBuilder out) {
      if (element == null) {
        return out;
      }
      //noinspection SuspiciousMethodCalls
      if (references.contains(element)) {
        final String shortClassName = aClass.getName();
        if (isReferenceToTargetClass(shortClassName, aClass, element)) {
          out.append(shortClassName);
        }
        else {
          out.append(aClass.getQualifiedName());
        }
        out.append('.');
        return out.append(element.getText());
      }
      final PsiElement[] children = element.getChildren();
      if (children.length == 0) {
        return out.append(element.getText());
      }
      for (PsiElement child : children) {
        buildNewText(child, references, aClass, out);
      }
      return out;
    }

    private static boolean isReferenceToTargetClass(
      String referenceText, PsiClass targetClass,
      PsiElement context) {
      final PsiManager manager = targetClass.getManager();
      final JavaPsiFacade facade =
        JavaPsiFacade.getInstance(manager.getProject());
      final PsiResolveHelper resolveHelper = facade.getResolveHelper();
      final PsiClass referencedClass =
        resolveHelper.resolveReferencedClass(referenceText,
                                             context);
      if (referencedClass == null) {
        return true;
      }
      return manager.areElementsEquivalent(targetClass, referencedClass);
    }
  }

  private static class ReferenceCollector
    extends JavaRecursiveElementVisitor {

    private final String name;
    private final boolean onDemand;
    private final Set<PsiJavaCodeReferenceElement> references =
      new HashSet();

    ReferenceCollector(String name, boolean onDemand) {
      this.name = name;
      this.onDemand = onDemand;
    }

    @Override
    public void visitReferenceElement(
      PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      if (reference.isQualified()) {
        return;
      }
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)target;
      if (!onDemand) {
        final String qualifiedName = aClass.getQualifiedName();
        if (name.equals(qualifiedName)) {
          references.add(reference);
        }
        return;
      }
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String qualifiedName = containingClass.getQualifiedName();
      if (name.equals(qualifiedName)) {
        references.add(reference);
      }
    }

    @Override
    public void visitReferenceExpression(
      PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    public Collection<PsiJavaCodeReferenceElement> getReferences() {
      return references;
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnqualifiedInnerClassAccessVisitor();
  }

  private class UnqualifiedInnerClassAccessVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitReferenceElement(
      PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      if (reference.isQualified()) {
        return;
      }
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)target;
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (ignoreReferencesToLocalInnerClasses) {
        if (PsiTreeUtil.isAncestor(containingClass, reference, true)) {
          return;
        }
        final PsiClass referenceClass =
          PsiTreeUtil.getParentOfType(reference, PsiClass.class);
        if (PsiTreeUtil.isAncestor(referenceClass, containingClass,
                                   true)) {
          return;
        }
      }
      registerError(reference, containingClass.getName());
    }

    @Override
    public void visitReferenceExpression(
      PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }
  }
}
