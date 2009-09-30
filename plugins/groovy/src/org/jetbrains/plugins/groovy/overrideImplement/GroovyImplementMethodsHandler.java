package org.jetbrains.plugins.groovy.overrideImplement;

import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * User: Dmitry.Krasilschikov
 * Date: 14.09.2007
 */
public class GroovyImplementMethodsHandler implements LanguageCodeInsightActionHandler {
  public boolean isValidFor(Editor editor, PsiFile psiFile) {
    return psiFile != null && GroovyFileType.GROOVY_FILE_TYPE.equals(psiFile.getFileType());
  }

  public void invoke(@NotNull final Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    GroovyOverrideImplementUtil.invokeOverrideImplement(project, editor, file, true);
  }

  public boolean startInWriteAction() {
    return false;
  }
}
