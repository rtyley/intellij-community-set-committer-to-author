/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * User: anna
 * Date: 9/1/11
 */
public class ImportsUtil {
  private ImportsUtil() {
  }

  public static List<PsiJavaCodeReferenceElement> collectReferencesThrough(PsiFile file,
                                                                           @Nullable final PsiJavaCodeReferenceElement refExpr,
                                                                           final PsiImportStaticStatement staticImport) {
    final List<PsiJavaCodeReferenceElement> expressionToExpand = new ArrayList<PsiJavaCodeReferenceElement>();
    file.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement expression) {
        if (refExpr == null || refExpr != expression) {
          final PsiElement resolveScope = expression.advancedResolve(true).getCurrentFileResolveScope();
          if (resolveScope == staticImport) {
            expressionToExpand.add(expression);
          }
        }
        super.visitElement(expression);
      }
    });
    return expressionToExpand;
  }

  public static void replaceAllAndDeleteImport(List<PsiJavaCodeReferenceElement> expressionToExpand,
                                               @Nullable PsiJavaCodeReferenceElement refExpr,
                                                PsiImportStaticStatement staticImport) {
    if (refExpr != null) {
      expressionToExpand.add(refExpr);
    }
    Collections.sort(expressionToExpand, new Comparator<PsiJavaCodeReferenceElement>() {
      @Override
      public int compare(PsiJavaCodeReferenceElement o1, PsiJavaCodeReferenceElement o2) {
        return o2.getTextOffset() - o1.getTextOffset();
      }
    });
    for (PsiJavaCodeReferenceElement expression : expressionToExpand) {
      expand(expression, staticImport);
    }
    staticImport.delete();
  }

  public static void expand(@NotNull PsiJavaCodeReferenceElement refExpr, PsiImportStaticStatement staticImport) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(refExpr.getProject());
    final PsiReferenceExpression referenceExpression = elementFactory.createReferenceExpression(staticImport.resolveTargetClass());
    if (refExpr instanceof PsiReferenceExpression) {
      ((PsiReferenceExpression)refExpr).setQualifierExpression(referenceExpression);
    }
    else {
      refExpr.replace(elementFactory.createReferenceFromText(referenceExpression.getText() + "." + refExpr.getText(), refExpr));
    }
  }
}
