package com.intellij.testIntegration;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class GenerateTestMethodAction extends BaseGenerateAction {
  public GenerateTestMethodAction() {
    super(new MyHandler());
  }

  @Override
  protected PsiClass getTargetClass(Editor editor, PsiFile file) {
    return findTargetClass(editor, file);
  }

  private static PsiClass findTargetClass(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    return TestIntegrationUtils.findOuterClass(element);
  }

  @Override
  protected boolean isValidForClass(PsiClass targetClass) {
    return TestIntegrationUtils.isTest(targetClass) && findDescriptor(targetClass) != null;
  }

  private static TestFrameworkDescriptor findDescriptor(PsiClass targetClass) {
    for (TestFrameworkDescriptor each : Extensions.getExtensions(TestFrameworkDescriptor.EXTENSION_NAME)) {
      if (each.isTestClass(targetClass)) {
        return each;
      }
    }
    return null;
  }

  private static class MyHandler implements CodeInsightActionHandler {
    public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
      try {
        PsiMethod method = generateDummyMethod(editor, file);

        PsiClass targetClass = findTargetClass(editor, file);
        TestIntegrationUtils.runTestMethodTemplate(TestIntegrationUtils.MethodKind.TEST,
                                                   findDescriptor(targetClass),
                                                   editor,
                                                   targetClass,
                                                   method,
                                                   "name",
                                                   false);
      }
      catch (IncorrectOperationException e) {
        throw new RuntimeException(e);
      }
    }

    private PsiMethod generateDummyMethod(Editor editor, PsiFile file) throws IncorrectOperationException {
      List<GenerationInfo> members = new ArrayList<GenerationInfo>();

      final PsiMethod method = TestIntegrationUtils.createDummyMethod(file.getProject());
      final PsiMethod[] result = new PsiMethod[1];

      members.add(new GenerationInfo() {
        @NotNull
        public PsiMember getPsiMember() {
          return method;
        }

        public void insert(PsiClass aClass, PsiElement anchor, boolean before) throws IncorrectOperationException {
          result[0] = (PsiMethod)GenerateMembersUtil.insert(aClass, method, anchor, before);
        }
      });

      int offset = findOffetToInsertMethodTo(editor, file);
      GenerateMembersUtil.insertMembersAtOffset(file, offset, members);

      return CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(result[0]);
    }

    private int findOffetToInsertMethodTo(Editor editor, PsiFile file) {
      int result = editor.getCaretModel().getOffset();

      PsiClass classAtCursor = PsiTreeUtil.getParentOfType(file.findElementAt(result), PsiClass.class, false);

      while (classAtCursor != null && !(classAtCursor.getParent() instanceof PsiFile)) {
        result = classAtCursor.getTextRange().getEndOffset();
        classAtCursor = PsiTreeUtil.getParentOfType(classAtCursor, PsiClass.class);
      }

      return result;
    }

    public boolean startInWriteAction() {
      return true;
    }
  }
}
