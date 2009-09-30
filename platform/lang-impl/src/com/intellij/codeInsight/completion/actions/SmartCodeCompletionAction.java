package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.completion.CodeCompletionFeatures;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class SmartCodeCompletionAction extends BaseCodeCompletionAction{

  protected CodeInsightActionHandler getHandler() {
    return createHandler();
  }

  public static CodeInsightActionHandler createHandler() {
    return new CodeInsightActionHandler() {
      public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_SMARTTYPE_GENERAL);
        new CodeCompletionHandlerBase(CompletionType.SMART).invoke(project, editor, file);
      }

      public boolean startInWriteAction() {
        return false;
      }
    };
  }

}
