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
package com.intellij.codeInspection.wrongPackageStatement;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class AdjustPackageNameFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.wrongPackageStatement.AdjustPackageNameFix");
  private final PsiJavaFile myFile;
  private final PsiPackageStatement myStatement;
  private final PsiPackage myTargetPackage;

  public AdjustPackageNameFix(PsiJavaFile file, PsiPackageStatement statement, PsiPackage targetPackage) {
    myFile = file;
    myStatement = statement;
    myTargetPackage = targetPackage;
  }

  @NotNull
  public String getName() {
    return QuickFixBundle.message("adjust.package.text", myTargetPackage.getQualifiedName());
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("adjust.package.family");
  }

  public boolean isAvailable() {
    return myFile != null
        && myFile.isValid()
        && myFile.getManager().isInProject(myFile)
        && myTargetPackage != null
        && myTargetPackage.isValid()
        && (myStatement == null || myStatement.isValid())
        ;
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    if (!CodeInsightUtilBase.prepareFileForWrite(myFile)) return;

    try {
      PsiElementFactory factory = JavaPsiFacade.getInstance(myFile.getProject()).getElementFactory();
      if (myTargetPackage.getQualifiedName().length() == 0) {
        if (myStatement != null) {
          myStatement.delete();
        }
      }
      else {
        if (myStatement != null) {
          PsiJavaCodeReferenceElement packageReferenceElement = factory.createPackageReferenceElement(myTargetPackage);
          myStatement.getPackageReference().replace(packageReferenceElement);
        }
        else {
          PsiPackageStatement packageStatement = factory.createPackageStatement(myTargetPackage.getQualifiedName());
          myFile.addAfter(packageStatement, null);
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
