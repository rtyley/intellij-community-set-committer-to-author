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
package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseRefactoringAction;

public class WrapReturnValueAction extends BaseRefactoringAction{

  protected RefactoringActionHandler getHandler(DataContext context){
        return new WrapReturnValueHandler();
    }

  public boolean isAvailableInEditorOnly(){
      return false;
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
      if (elements.length != 1) {
          return false;
      }
      final PsiElement element = elements[0];
    final PsiMethod containingMethod =
            PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    return containingMethod != null;
  }
}
