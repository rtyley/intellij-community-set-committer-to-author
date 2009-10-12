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

package com.intellij.refactoring.rename;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.beanProperties.BeanProperty;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.openapi.impl.JavaRenameRefactoringImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class BeanPropertyRenameHandler implements RenameHandler {

  public boolean isAvailableOnDataContext(DataContext dataContext) {
    return false;
  }

  public boolean isRenaming(DataContext dataContext) {
    return getProperty(dataContext) != null;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    final BeanProperty property = getProperty(dataContext);
    new PropertyRenameDialog(property, editor).show();
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {

  }

  public static void doRename(@NotNull final BeanProperty property, final String newName, final boolean searchInComments) {
    final PsiElement psiElement = property.getPsiElement();
    final RenameRefactoring rename = new JavaRenameRefactoringImpl(psiElement.getProject(), psiElement, newName, searchInComments, false);

    final PsiMethod setter = property.getSetter();
    if (setter != null) {
      final String setterName = PropertyUtil.suggestSetterName(newName);
      rename.addElement(setter, setterName);
    }

    final PsiMethod getter = property.getGetter();
    if (getter != null) {
      final String getterName = PropertyUtil.suggestGetterName(newName, getter.getReturnType());
      rename.addElement(getter, getterName);
    }

    rename.run();
  }

  @Nullable
  protected abstract BeanProperty getProperty(DataContext context);

  private static class PropertyRenameDialog extends RenameDialog {

    private final BeanProperty myProperty;

    protected PropertyRenameDialog(BeanProperty property, final Editor editor) {
      super(property.getMethod().getProject(), property.getPsiElement(), null, editor);
      myProperty = property;
    }

    protected void doAction() {
      final String newName = getNewName();
      final boolean searchInComments = isSearchInComments();
      doRename(myProperty, newName, searchInComments);
      close(DialogWrapper.OK_EXIT_CODE);
    }

  }
}
