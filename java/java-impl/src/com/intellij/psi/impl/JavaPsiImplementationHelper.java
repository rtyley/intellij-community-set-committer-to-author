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
package com.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

/**
 * @author yole
 */
public abstract class JavaPsiImplementationHelper {
  public static JavaPsiImplementationHelper getInstance(Project project) {
    return ServiceManager.getService(project, JavaPsiImplementationHelper.class);
  }

  public abstract PsiClass getOriginalClass(PsiClass psiClass);

  public abstract PsiElement getClsFileNavigationElement(PsiJavaFile clsFile);

  public abstract ASTNode getDefaultImportAnchor(PsiImportList list, PsiImportStatementBase statement);
}
