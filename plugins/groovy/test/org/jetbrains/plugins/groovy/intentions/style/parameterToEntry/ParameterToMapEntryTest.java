package org.jetbrains.plugins.groovy.intentions.style.parameterToEntry;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import junit.framework.Assert;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.*;

/**
 * @author ilyas
 */
public class ParameterToMapEntryTest extends GroovyFormatterTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "paramToMap/" + getTestName(true) + "/";
  }

  /*
  @Override
  protected CodeStyleSettings getCurrentCodeStyleSettings() {
    return CodeStyleSettingsManager.getSettings(getProject());
  }
  */

  public void testParam1() throws Throwable {
    doTestImpl("A.groovy");
  }

  public void testFormatter() throws Throwable {
    doTestImpl("A.groovy");
  }

  public void testClosureAtEnd() throws Throwable {
    doTestImpl("A.groovy");
  }

  public void testClosure1() throws Throwable {
    doTestImpl("A.groovy");
  }

  public void testNewMap() throws Throwable {
    doTestImpl("A.groovy");
  }

  public void testTestError() throws Throwable {
    doTestImpl("A.groovy");
  }

  public void testSecondClosure() throws Throwable {
    doTestImpl("A.groovy");
  }

  private void doTestImpl(String filePath) throws Throwable {
    myFixture.configureByFile(filePath);
    int offset = myFixture.getEditor().getCaretModel().getOffset();
    final PsiFile file = myFixture.getFile();


    final ConvertParameterToMapEntryIntention intention = new ConvertParameterToMapEntryIntention();
    PsiElement element = file.findElementAt(offset);
    while (element != null && !(element instanceof GrReferenceExpression || element instanceof GrParameter)) {
      element = element.getParent();
    }
    Assert.assertNotNull(element);

    final PsiElementPredicate condition = intention.getElementPredicate();
    Assert.assertTrue(condition.satisfiedBy(element));

    // Launch it!
    intention.processIntention(element);
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    final String result = file.getText();
    //System.out.println(result);
    String expected = getExpectedResult(filePath);
    Assert.assertEquals(result, expected);
  }

  private String getExpectedResult(final String filePath) {
    Assert.assertTrue(filePath.endsWith(".groovy"));
    String testFilePath = StringUtil.trimEnd(filePath, "groovy") + "test";

    final File file = new File(getTestDataPath() + testFilePath);
    assertTrue(file.exists());
    String expected = "";

    try {
      BufferedReader reader = new BufferedReader(new FileReader(file));
      String line = reader.readLine();
      while (line != null) {
        expected += line;
        line = reader.readLine();
        if (line != null) expected += "\n";
      }
      reader.close();
    }
    catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return expected;
  }

}