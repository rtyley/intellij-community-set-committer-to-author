package com.intellij.psi;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

@PlatformTestCase.WrapInCommand
public class NormalizeDeclarationTest extends PsiTestCase{
  private static final String BASE_PATH = JavaTestUtil.getJavaTestDataPath() + "/psi/normalizeDeclaration";

  public void test1() throws Exception { doTest(); }
  public void test2() throws Exception { doTest(); }

  public void testSCR6549() throws Exception { doTest(); }
  public void testSCR9467() throws Exception { doTest(); }
  public void testSCR9467_1() throws Exception { doTest(); }

  private void doTest() throws Exception {
    PsiElement element = configureByFileWithMarker(BASE_PATH + "/" + getTestName(false) + ".java", "<var>");
    assertTrue(element instanceof PsiIdentifier);
    assertTrue(element.getParent() instanceof PsiVariable);

    ((PsiVariable)element.getParent()).normalizeDeclaration();

    String textAfter = loadFile(getTestName(false) + "_after.java");
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    String fileText = myFile.getText();
    assertEquals(textAfter, fileText);
    PsiTestUtil.checkFileStructure(myFile);
  }

  private static String loadFile(String name) throws Exception {
    String fullName = BASE_PATH + File.separatorChar + name;
    String text = FileUtil.loadFile(new File(fullName));
    text = StringUtil.convertLineSeparators(text);
    return text;
  }
}
