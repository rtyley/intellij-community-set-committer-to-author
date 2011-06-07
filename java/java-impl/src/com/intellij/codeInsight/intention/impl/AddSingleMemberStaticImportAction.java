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

/*
 * @author ven
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddSingleMemberStaticImportAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.AddSingleMemberStaticImportAction");
  private static final Key<PsiElement> TEMP_REFERENT_USER_DATA = new Key<PsiElement>("TEMP_REFERENT_USER_DATA");

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.add.single.member.static.import.family");
  }

  /**
   * Allows to check if it's possible to perform static import for the target element.
   * 
   * @param element     target element that is static import candidate
   * @return            not-null qualified name of the class which method may be statically imported if any; <code>null</code> otherwise
   */
  @Nullable
  public static String getStaticImportClass(@NotNull PsiElement element) {
    if (!PsiUtil.isLanguageLevel5OrHigher(element)) return null;
    PsiFile file = element.getContainingFile();
    if (element instanceof PsiIdentifier && element.getParent() instanceof PsiReferenceExpression &&
        ((PsiReferenceExpression)element.getParent()).getQualifierExpression() != null) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression)element.getParent();
      PsiReferenceParameterList parameterList = refExpr.getParameterList();
      if (parameterList != null && parameterList.getFirstChild() != null) return null;
      PsiElement resolved = refExpr.resolve();
      if (resolved instanceof PsiMember && ((PsiModifierListOwner)resolved).hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass aClass = ((PsiMember)resolved).getContainingClass();
        if (aClass != null && !PsiTreeUtil.isAncestor(aClass, element, true)) {
          String qName = aClass.getQualifiedName();
          if (qName != null) {
            qName = qName + "." +refExpr.getReferenceName();
            if (file instanceof PsiJavaFile) {
              PsiImportList importList = ((PsiJavaFile)file).getImportList();
              if (importList != null && importList.findSingleImportStatement(refExpr.getReferenceName()) == null) {
                return qName;
              }
            }
          }
        }
      }
    }

    return null;
  }
  
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    String classQName = getStaticImportClass(element);
    if (classQName != null) {
      setText(CodeInsightBundle.message("intention.add.single.member.static.import.text", classQName));
    }
    return classQName != null;
  }

  public static void invoke(PsiFile file, PsiElement element) {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)element.getParent();
    final PsiElement resolved = refExpr.resolve();

    file.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        String referenceName = refExpr.getReferenceName();
        if (referenceName != null && referenceName.equals(expression.getReferenceName())) {
          PsiElement resolved = expression.resolve();
          if (resolved != null) {
            expression.putUserData(TEMP_REFERENT_USER_DATA, resolved);
          }
        }
      }
    });

    if (resolved != null) {
      RefactoringUtil.bindToElementViaStaticImport(
        ((PsiMember)resolved).getContainingClass(), ((PsiNamedElement)resolved).getName(), ((PsiJavaFile)file).getImportList()
      );
    }

    file.accept(new JavaRecursiveElementVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.getParameterList() != null &&
            expression.getParameterList().getFirstChild() != null) return;

        if (refExpr.getReferenceName().equals(expression.getReferenceName())) {
          final PsiExpression qualifierExpression = expression.getQualifierExpression();
          if (!expression.isQualified()) {
            PsiElement referent = expression.getUserData(TEMP_REFERENT_USER_DATA);

            if (referent instanceof PsiMember && referent != expression.resolve()) {
              PsiElementFactory factory = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory();
              try {
                PsiReferenceExpression copy = (PsiReferenceExpression)factory.createExpressionFromText("A." + expression.getReferenceName(), null);
                expression = (PsiReferenceExpression)expression.replace(copy);
                ((PsiReferenceExpression)qualifierExpression).bindToElement(((PsiMember)referent).getContainingClass());
              }
              catch (IncorrectOperationException e) {
                LOG.error (e);
              }
            }
            expression.putUserData(TEMP_REFERENT_USER_DATA, null);
          } else {
            if (qualifierExpression instanceof PsiReferenceExpression) {
              PsiElement aClass = ((PsiReferenceExpression)qualifierExpression).resolve();
              if (aClass == ((PsiMember)resolved).getContainingClass()) {
                try {
                  qualifierExpression.delete();
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
              }
            }
          }
          expression.putUserData(TEMP_REFERENT_USER_DATA, null);
        }

        super.visitReferenceExpression(expression);
      }
    });

  }
  
  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    invoke(file, element);
  }
}
