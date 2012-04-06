package com.intellij.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.LightRefactoringTestCase;
import org.jetbrains.annotations.NonNls;

public class InlineConstantFieldTest extends LightRefactoringTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testQualifiedExpression() throws Exception {
    doTest();
  }

  public void testQualifiedConstantExpression() throws Exception {
    doTest();
  }

   public void testQualifiedConstantExpressionReplacedWithAnotherOne() throws Exception {
    doTest();
  }
  
  public void testStaticallyImportedQualifiedExpression() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    String name = getTestName(false);
    @NonNls String fileName = "/refactoring/inlineConstantField/" + name + ".java";
    configureByFile(fileName);
    performAction();
    checkResultByFile(fileName + ".after");
  }

  private void performAction() {
    PsiElement element = TargetElementUtilBase
      .findTargetElement(myEditor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    final PsiReference ref = myFile.findReferenceAt(myEditor.getCaretModel().getOffset());
    PsiReferenceExpression refExpr = ref instanceof PsiReferenceExpression ? (PsiReferenceExpression)ref : null;
    assertTrue(element instanceof PsiField);
    PsiField field = (PsiField)element;
    new InlineConstantFieldProcessor(field, getProject(), refExpr, false).run();
  }
}