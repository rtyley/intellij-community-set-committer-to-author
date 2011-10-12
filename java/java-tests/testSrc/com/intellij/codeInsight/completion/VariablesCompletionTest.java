package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

import java.io.IOException;

public class VariablesCompletionTest extends CompletionTestCase {
  public static final String FILE_PREFIX = "/codeInsight/completion/variables/";

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testObjectVariable() throws Exception {
    configureByFile(FILE_PREFIX + "locals/" + getTestName(false) + ".java");
    checkResultByFile(FILE_PREFIX + "locals/" + getTestName(false) + "_after.java");
  }

  public void testStringVariable() throws Exception {
    configureByFile(FILE_PREFIX + "locals/" + getTestName(false) + ".java");
    checkResultByFile(FILE_PREFIX + "locals/" + getTestName(false) + "_after.java");
  }

  public void testInputMethodEventVariable() throws Exception {
    createClass("package java.awt.event; public interface InputMethodEvent {}");

    configureByFile(FILE_PREFIX + "locals/" + getTestName(false) + ".java");
    checkResultByFile(FILE_PREFIX + "locals/" + getTestName(false) + "_after.java");
  }

  public void testLocals1() throws Exception {
    doTest("TestSource1.java", "TestResult1.java");
  }

  public void testInterfaceMethod() throws Exception {
    configureByFile(FILE_PREFIX + "locals/" + "InterfaceMethod.java");
    assertStringItems("calcGooBarDoo", "calcBarDoo", "calcDoo");
  }

  public void testLocals2() throws Exception {
    configureByFile(FILE_PREFIX + "locals/" + "TestSource2.java");
    compareLookup(FILE_PREFIX + "locals/test2-lst.txt");
    checkResultByFile(FILE_PREFIX + "locals/" + "TestResult2.java");
  }

  public void testLocals3() throws Exception {
    doTest("TestSource3.java", "TestResult3.java");
  }

  public void testLocals4() throws Exception {
    doTest("TestSource4.java", "TestResult4.java");
  }

  public void testLocals5() throws Exception {
    doTest("TestSource5.java", "TestResult5.java");
  }

  public void testLocals6() throws Exception {
    doTest("TestSource6.java", "TestResult6.java");
  }

  public void testLocals7() throws Exception {
    doTest("TestSource7.java", "TestResult7.java");
  }

  public void testLocalReserved() throws Exception {
    doTest("LocalReserved.java", "LocalReserved_after.java");
  }

  public void testUniqueNameInFor() throws Exception {
    doTest(getTestName(false) + ".java", getTestName(false) + "_after.java");
  }
  public void testWithBuilderParameter() throws Exception {
    doTest(getTestName(false) + ".java", getTestName(false) + "_after.java");
  }

  private void doTest(String before, String after) throws Exception {
    configureByFile(FILE_PREFIX + "locals/" + before);
    checkResultByFile(FILE_PREFIX + "locals/" + after);
  }

  public void testLocals8() throws Exception {
    doTest("TestSource8.java", "TestResult8.java");
  }

  public void testFieldNameCompletion1() throws Exception {
    configureByFileNoCompletion(FILE_PREFIX + "locals/" + "FieldNameCompletion1.java");
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(myProject);
    String oldPrefix = settings.FIELD_NAME_PREFIX;
    settings.FIELD_NAME_PREFIX = "my";
    complete();
    settings.FIELD_NAME_PREFIX = oldPrefix;
    checkResultByFile(FILE_PREFIX + "locals/" + "FieldNameCompletion1-result.java");
  }

  public void testFieldNameCompletion2() throws Exception {
    configureByFileNoCompletion(FILE_PREFIX + "locals/" + "FieldNameCompletion2.java");
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(myProject);
    String oldPrefix = settings.FIELD_NAME_PREFIX;
    settings.FIELD_NAME_PREFIX = "my";
    complete();
    settings.FIELD_NAME_PREFIX = oldPrefix;
    checkResultByFile(FILE_PREFIX + "locals/" + "FieldNameCompletion2-result.java");
  }

  public void testFieldNameCompletion3() throws Exception {
    configureByFileNoCompletion(FILE_PREFIX + "locals/" + "FieldNameCompletion3.java");
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(myProject);
    String oldPrefix = settings.FIELD_NAME_PREFIX;
    settings.FIELD_NAME_PREFIX = "my";
    complete();
    settings.FIELD_NAME_PREFIX = oldPrefix;
    checkResultByFile(FILE_PREFIX + "locals/" + "FieldNameCompletion3-result.java");
  }

  public void testLocals9() throws Exception {
    doTest("TestSource9.java", "TestResult9.java");
  }

  public void testFieldOutOfAnonymous() throws Exception {
    configureByFile(FILE_PREFIX + "locals/" + "TestFieldOutOfAnonymous.java");
    complete();
    checkResultByFile(FILE_PREFIX + "locals/" + "TestFieldOutOfAnonymousResult.java");
  }

  public void testUnresolvedMethodName() throws Exception {
    configureByFile(FILE_PREFIX + "locals/" + "UnresolvedMethodName.java");
    complete();
    checkResultByFile(FILE_PREFIX + "locals/" + "UnresolvedMethodName.java");
    doTestByCount(2, "creAnInt", "createStylesheetCombobox");
  }

  public void testArrayMethodName() throws Throwable {
    doTest("ArrayMethodName.java", "ArrayMethodName-result.java");
  }

  public void testInitializerMatters() throws Exception {
    configureByText(JavaFileType.INSTANCE, "class Foo {{ String f<caret>x = getFoo(); }; String getFoo() {}; }");
    complete();
    assertStringItems("foo", "fS");
  }

  public void testFieldInitializerMatters() throws Exception {
    configureByText(JavaFileType.INSTANCE, "class Foo { String f<caret>x = getFoo(); String getFoo() {}; }");
    complete();
    assertStringItems("foo", "fString");
  }

  public void testNoKeywordsInForLoopVariableName() throws Throwable {
    configureByFile(FILE_PREFIX + getTestName(false) + ".java");
    assertStringItems("stringBuffer", "buffer");
  }

  protected void compareLookup(String fileName) throws IOException{
    String fullPath = getTestDataPath() + fileName;
    VirtualFile result = LocalFileSystem.getInstance().findFileByPath(fullPath);
    assertNotNull("file " + fullPath + " not found", result);


    assertStringItems(LineTokenizer.tokenize(FileDocumentManager.getInstance().getDocument(result).getCharsSequence(), false));
  }
}
