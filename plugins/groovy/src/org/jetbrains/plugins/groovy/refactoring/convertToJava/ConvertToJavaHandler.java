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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

/**
 * @author Maxim.Medvedev
 */
public class ConvertToJavaHandler implements RefactoringActionHandler {
  private static final String REFACTORING_NAME = GroovyRefactoringBundle.message("convert.to.java.refactoring.name");

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    invokeInner(project, new PsiElement[]{file}, editor);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    Editor editor = null;
    if (dataContext != null) {
      editor = PlatformDataKeys.EDITOR.getData(dataContext);
    }
    invokeInner(project, elements, editor);
  }

  private void invokeInner(Project project, PsiElement[] elements, Editor editor) {
    for (PsiElement element : elements) {
      if (!(element instanceof GroovyFile)) {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          CommonRefactoringUtil.showErrorHint(project, editor, GroovyRefactoringBundle
            .message(GroovyRefactoringBundle.message("convert.to.java.can.work.only.with.groovy")), REFACTORING_NAME,
                                              HelpID.EXTRACT_METHOD);
        }
      }
    }
    GroovyFile[] files = new GroovyFile[elements.length];
    System.arraycopy(elements, 0, files, 0, elements.length);
    new ConvertToJavaProcessor(project, files).run();
  }
}
