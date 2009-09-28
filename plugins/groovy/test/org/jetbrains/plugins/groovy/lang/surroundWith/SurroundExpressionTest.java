package org.jetbrains.plugins.groovy.lang.surroundWith;

import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.GroovyWithParenthesisExprSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.GroovyWithTypeCastSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.GroovyWithWithExprSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions.GroovyWithIfElseExprSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions.GroovyWithIfExprSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions.GroovyWithWhileExprSurrounder;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * User: Dmitry.Krasilschikov
 * Date: 01.06.2007
 */
public class SurroundExpressionTest extends SurroundTestCase {

  public void testBrackets1() throws Exception { doTest(new GroovyWithParenthesisExprSurrounder()); }
  public void testIf1() throws Exception { doTest(new GroovyWithIfExprSurrounder()); }
  public void testIf_else1() throws Exception { doTest(new GroovyWithIfElseExprSurrounder()); }
  public void testType_cast1() throws Exception { doTest(new GroovyWithTypeCastSurrounder()); }
  public void testWhile1() throws Exception { doTest(new GroovyWithWhileExprSurrounder()); }
  public void testWith2() throws Exception { doTest(new GroovyWithWithExprSurrounder()); }

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/surround/expr/";
  }

}
