/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import junit.framework.Assert;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * @author ilyas
 */
public class InlineVariableTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/refactoring/inlineLocal/";
  }

  public void testGRVY_1232() throws Throwable { doTest(); }
  public void testGRVY_1248() throws Throwable { doTest(); }
  public void testVar1() throws Throwable { doTest(); }
  public void testVar2() throws Throwable { doTest(); }
  public void testVar3() throws Throwable { doTest(); }
  public void testVar4() throws Throwable { doTest(); }
  public void testVar5() throws Throwable { doTest(); }
  public void testVar6() throws Throwable { doTest(); }
  public void testVarInGString() throws Throwable { doTest(); }
  public void testVarInGString2() throws Throwable { doTest(); }
  public void testVarInGString3() throws Throwable { doTest(); }
  public void testVarInGString4() throws Throwable { doTest(); }

  public void testField() {doFieldTest();}

  public void testPartial1() {doTest();}
  public void testPartial2() {doTest();}
  public void testPartial3() {doTest();}
  public void testPartial4() {doTest();}

  public void testClosure1() {doTest();}
  public void testClosure2() {doTest();}
  public void testClosure3() {doTest();}

  protected void doFieldTest() {
    InlineMethodTest.doInlineTest(myFixture, getTestDataPath() + getTestName(true) + ".test", new GroovyInlineHandler());
  }

  private void doTest()  {
    doTest(false);
  }

  private void doTest(final boolean inlineDef) {
    final List<String> data = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");
    String fileText = data.get(0);

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText);

    final Editor editor = myFixture.getEditor();
    final PsiFile file = myFixture.getFile();
    setIndentationToNode(file.getNode());

    int startOffset = editor.getSelectionModel().getSelectionStart();
    int endOffset = editor.getSelectionModel().getSelectionEnd();
    editor.getCaretModel().moveToOffset(endOffset);

    GroovyPsiElement selectedArea =
      GroovyRefactoringUtil.findElementInRange(file, startOffset, endOffset, GrReferenceExpression.class);
    if (selectedArea == null) {
      PsiElement identifier = GroovyRefactoringUtil.findElementInRange(file, startOffset, endOffset, PsiElement.class);
      if (identifier != null) {
        Assert.assertTrue("Selected area doesn't point to var", identifier.getParent() instanceof GrVariable);
        selectedArea = (GroovyPsiElement)identifier.getParent();
      }
    }
    Assert.assertNotNull("Selected area reference points to nothing", selectedArea);
    PsiElement element = selectedArea instanceof GrExpression ? selectedArea.getReference().resolve() : selectedArea;
    Assert.assertNotNull("Cannot resolve selected reference expression", element);

    try {
      if (!inlineDef) {
        performInline(getProject(), editor);
      }
      else {
        performDefInline(getProject(), editor);
      }
      editor.getSelectionModel().removeSelection();
      myFixture.checkResult(data.get(1), true);
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals(data.get(1), "FAIL: " + e.getMessage());
    }
  }

  public static void performInline(Project project, Editor editor) {
    PsiElement element = TargetElementUtilBase.findTargetElement(editor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED |
                                                                         TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    assertTrue(element instanceof GrVariable);

    GroovyInlineLocalHandler.invoke(project, editor, (GrVariable)element);
  }

  public static void performDefInline(Project project, Editor editor) {
    PsiReference reference = TargetElementUtilBase.findReference(editor);
    assertTrue(reference instanceof PsiReferenceExpression);
    final PsiElement local = reference.resolve();
    assertTrue(local instanceof PsiLocalVariable);

    GroovyInlineLocalHandler.invoke(project, editor, (GrVariable)local);
  }

  private static void setIndentationToNode(ASTNode element){
    if (element instanceof TreeElement) {
      CodeEditUtil.setOldIndentation(((TreeElement)element), 0);
    }
    for (ASTNode node : element.getChildren(null)) {
      setIndentationToNode(node);
    }
  }

}
