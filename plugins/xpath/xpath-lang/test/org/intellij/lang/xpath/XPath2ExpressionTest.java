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
package org.intellij.lang.xpath;

import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.psi.XPath2SequenceType;
import org.intellij.lang.xpath.psi.XPathBinaryExpression;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathType;

import static org.intellij.lang.xpath.psi.XPath2Type.*;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 10.03.11
*/
public class XPath2ExpressionTest extends TestBase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ContextProvider.DefaultProvider.NULL_NAMESPACE_CONTEXT = TestNamespaceContext.INSTANCE;
  }

  public void testIntegerPlusInteger() throws Throwable {
   assertEquals(INTEGER, doTest(true));
  }

  public void testIntegerPlusDecimal() throws Throwable {
    assertEquals(DECIMAL, doTest(true));
  }

  public void testIntegerPlusDouble() throws Throwable {
    assertEquals(DOUBLE, doTest(true));
  }

  public void testIntegerIdivInteger() throws Throwable {
    assertEquals(INTEGER, doTest(true));
  }

  public void testIntegerDivInteger() throws Throwable {
    assertEquals(DECIMAL, doTest(true));
  }

  public void testDoubleDivInteger() throws Throwable {
    assertEquals(DOUBLE, doTest(true));
  }

  public void testDatePlusYmd() throws Throwable {
    assertEquals(DATE, doTest(true));
  }

  public void testDatePlusDtd() throws Throwable {
    assertEquals(DATE, doTest(true));
  }

  public void testTimePlusDtd() throws Throwable {
    assertEquals(TIME, doTest(true));
  }

  public void testDateTimePlusYmd() throws Throwable {
    assertEquals(DATETIME, doTest(true));
  }

  public void testDateTimePlusDtd() throws Throwable {
    assertEquals(DATETIME, doTest(true));
  }

  public void testYmdPlusYmd() throws Throwable {
    assertEquals(YEARMONTHDURATION, doTest(true));
  }

  public void testDtdPlusDtd() throws Throwable {
    assertEquals(DAYTIMEDURATION, doTest(true));
  }

  public void testDoubleMinusInteger() throws Throwable {
    assertEquals(DOUBLE, doTest(true));
  }

  public void testDateMinusDate() throws Throwable {
    assertEquals(DAYTIMEDURATION, doTest(true));
  }

  public void testDateMinusYmd() throws Throwable {
    assertEquals(DATE, doTest(false));
  }

  public void testDateMinusDtd() throws Throwable {
    assertEquals(DATE, doTest(false));
  }

  public void testTimeMinusTime() throws Throwable {
    assertEquals(DAYTIMEDURATION, doTest(true));
  }

  public void testTimeMinusDtd() throws Throwable {
    assertEquals(TIME, doTest(false));
  }

  public void testYmdMinusYmd() throws Throwable {
    assertEquals(YEARMONTHDURATION, doTest(true));
  }

  public void testDoubleMultInteger() throws Throwable {
    assertEquals(DOUBLE, doTest(true));
  }

  public void testYmdMultInteger() throws Throwable {
    assertEquals(YEARMONTHDURATION, doTest(true));
  }

  public void testYmdMultDecimal() throws Throwable {
    assertEquals(YEARMONTHDURATION, doTest(true));
  }

  protected XPathType doTest(boolean symmetric) throws Throwable {
    myFixture.configureByFile(getTestFileName() + ".xpath2");

    final XPathExpression expression = getExpression();

    // all these cases must be green
    myFixture.checkHighlighting();

    if (symmetric && expression instanceof XPathBinaryExpression) {
      final XPathBinaryExpression expr = (XPathBinaryExpression)expression;
      if (expr.getLOperand().getType() != expr.getROperand().getType()) {
        myFixture.configureByText(XPathFileType.XPATH2,
                                  expr.getROperand().getText() + " " + expr.getOperationSign() + " " + expr.getLOperand().getText());

        assertEquals(getExpression().getType(), expression.getType());

        myFixture.checkHighlighting();
      }
    }

    final XPathType type = expression.getType();
    if (type instanceof XPath2SequenceType) {
      return ((XPath2SequenceType)type).getType();
    }
    return type;
  }

  private XPathExpression getExpression() throws NoSuchMethodException {
    final XPathFile file = (XPathFile)myFixture.getFile();
    final XPathExpression expression = file.getExpression();
    assertNotNull(expression);

    return expression;
  }

  @Override
  protected String getSubPath() {
    return "xpath2/types";
  }
}
