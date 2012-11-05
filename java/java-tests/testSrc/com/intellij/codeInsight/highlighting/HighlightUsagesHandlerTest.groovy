package com.intellij.codeInsight.highlighting;

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;

/**
 * @author cdr
 */
public class HighlightUsagesHandlerTest extends LightCodeInsightFixtureTestCase {
  private void ctrlShiftF7() {
    HighlightUsagesHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
  }

  public void testSimpleThrows() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("throws", "Exception");
    checkUnselect();
  }
  public void testThrowsExpression() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("throws", "(Exception)detail");
    checkUnselect();
  }
  public void testThrowsReference() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("throws", "detail");
    checkUnselect();
  }

  private void checkUnselect() {
    ctrlShiftF7();
    assertRangeText();
  }

  public void testUnselectUsage() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("foo", "foo", "foo");
    checkUnselect();
  }

  public void testHighlightOverridden() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("extends", "foo");
    checkUnselect();
  }
  public void testHighlightOverriddenImplements() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("implements", "foo");
    checkUnselect();
  }
  public void testHighlightOverriddenNothing() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText();
    checkUnselect();
  }
  public void testHighlightOverriddenMultiple() throws Exception {
    configureFile();
    ctrlShiftF7();
    assertRangeText("implements", "foo", "other");
    checkUnselect();
  }

  public void testIDEADEV28822() throws Exception {
    myFixture.configureByText("Foo.java",
                          """public class Foo {public String foo(String s) {
    while (s.length() > 0) {
      if (s.length() < 0) {
        s = "";
        continue;
      }
      else {
      }
    }
    re<caret>turn s;
  }
}""");
    ctrlShiftF7();
    assertRangeText("return s;");
  }
            
  public void testReturnsInTryFinally() throws Exception {
    // See IDEADEV-14028
    myFixture.configureByText("Foo.java",
                          """public class Foo {
  int foo(boolean b) {
    try {
      if (b) return 1;
    }
    finally {
      if (b) return 2;
    }
    r<caret>eturn 3;
  }
}""");

    ctrlShiftF7();
    assertRangeText("return 1;", "return 2;", "return 3;");
  }

  public void testReturnsInLambda() throws Exception {
    // See IDEADEV-14028
    myFixture.configureByText("Foo.java",
                          """public class Foo {
  {
    Runnable r = () -> {
           if (true) return;
           retur<caret>n;
    }
  }
}""");

    ctrlShiftF7();
    assertRangeText("return;", "return;");
  }

  public void testSuppressedWarningsHighlights() throws Exception {
    myFixture.configureByText("Foo.java", """public class Foo {
        @SuppressWarnings({"Sil<caret>lyAssignment"})
        void foo() {
            int i = 0;
            i = i;
        }
    }""");
    myFixture.enableInspections(new SillyAssignmentInspection())
    ctrlShiftF7();
    assertRangeText("i = i");
  }

  private void assertRangeText(@NonNls String... texts) {
    def highlighters = myFixture.editor.getMarkupModel().getAllHighlighters()
    def actual = highlighters.collect { myFixture.file.text.substring(it.startOffset, it.endOffset) }
    assertSameElements(actual, texts)
  }
  
  private void configureFile() throws Exception {
    def file = myFixture.copyFileToProject("/codeInsight/highlightUsagesHandler/" + getTestName(false) + ".java", getTestName(false) + ".java")
    myFixture.configureFromExistingVirtualFile(file)
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.relativeJavaTestDataPath
  }
}
