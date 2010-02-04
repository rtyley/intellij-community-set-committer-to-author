/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GroovyCompletionTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/completion/";
  }

  public void testFinishMethodWithLParen() throws Throwable {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "getBar", "getClass", "getFoo");
    myFixture.type('(');
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testSmartCompletionAfterNewInDeclaration() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART);
    assertOrderedEquals(myFixture.getLookupElementStrings(), "Bar", "Foo");
  }

  public void testSmartCompletionAfterNewInDeclarationWithInterface() throws Throwable {
    doSmartTest();
  }

  private void doSmartTest() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART);
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy", true);
  }

  public void testCaretAfterSmartCompletionAfterNewInDeclaration() throws Throwable {
    doSmartTest();
  }

  public void testSmartCompletionAfterNewInDeclarationWithAbstractClass() throws Throwable {
    doSmartTest();
  }

  public void testSmartCompletionAfterNewInDeclarationWithArray() throws Throwable {
    doSmartTest();
  }

  public void testSmartCompletionAfterNewInDeclarationWithIntArray() throws Throwable {
    doSmartTest();
  }

  public void testShortenNamesInSmartCompletionAfterNewInDeclaration() throws Throwable {
    doSmartTest();
  }

  public void testEachMethodForList() throws Throwable {
    doBasicTest();
  }

  private void doBasicTest() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy");
  }

  public void testEachMethodForMapWithKeyValue() throws Throwable {
    doBasicTest();
  }

  public void testEachMethodForMapWithEntry() throws Throwable {
    doBasicTest();
  }

  public void testWithMethod() throws Throwable {
    doBasicTest();
  }

  public void testInjectMethodForCollection() throws Throwable {
    doBasicTest();
  }

  public void testInjectMethodForArray() throws Throwable {
    doBasicTest();
  }

  public void testInjectMethodForMap() throws Throwable {
    doBasicTest();
  }

  public void testClosureDefaultParameterInEachMethod() throws Throwable {
    doBasicTest();
  }

  public void testArrayLikeAccessForList() throws Throwable {
    doBasicTest();
  }

  public void testArrayLikeAccessForMap() throws Throwable {
    doBasicTest();
  }

  public void testEachMethodForRanges() throws Throwable {
    doBasicTest();
  }

  public void testEachMethodForEnumRanges() throws Throwable {
    doBasicTest();
  }

  public void doVariantableTest(String... variants) throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.BASIC);
    assertOrderedEquals(myFixture.getLookupElementStrings(), variants);
  }

  public void testNamedParametersForApplication() throws Throwable {
    doVariantableTest("abx", "aby");
  }

  public void testNamedParametersForMethodCall() throws Throwable {
    doVariantableTest("abx", "aby");
  }

  public void testNamedParametersForNotMap() throws Throwable {
    doBasicTest();
  }

  public void testNamedParametersForConstructorCall() throws Throwable {
    doVariantableTest("hahaha", "hohoho");
  }

  public void testInstanceofHelpsDetermineType() throws Throwable {
    doBasicTest();
  }

  public void testNotInstanceofDoesntHelpDetermineType() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy");
  }

  public void testNotInstanceofDoesntHelpDetermineType2() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy");
  }

  public void testTypeParameterCompletion() throws Throwable {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "put", "putAll");
  }

  public void testCatchClauseParameter() throws Throwable {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "getStackTrace", "getStackTraceDepth", "getStackTraceElement");
  }

  public void testFieldSuggestedOnce1() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy");
    assertNull(myFixture.getLookupElements());
  }

  public void testFieldSuggestedOnce2() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy");
    assertNull(myFixture.getLookupElements());
  }

  public void testFieldSuggestedOnce3() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy");
    assertNull(myFixture.getLookupElements());
  }

  public void testFieldSuggestedOnce4() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy");
    assertNull(myFixture.getLookupElements());
  }

  public void testFieldSuggestedOnce5() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + ".groovy");
    assertNull(myFixture.getLookupElements());
  }

  public void testFieldSuggestedInMethodCall() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy");
  }

  public void testMethodParameterNoSpace() throws Throwable {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy");
  }

  public void testGroovyDocParameter() throws Throwable {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "xx", "xy");
  }

  public void testInnerClassExtendsImplementsCompletion() throws Throwable {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "extends", "implements");
  }

  public void testInnerClassCompletion() throws Throwable {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "Inner1", "Inner2");
  }

  public void testInnerClassInStaticMethodCompletion() throws Throwable {
    doSmartTest();
  }


  public void testQualifiedThisCompletion() throws Throwable {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "foo1", "foo2");
  }

  public void testQualifiedSuperCompletion() throws Throwable {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "foo1", "foo2");
  }

  public void testThisKeywordCompletionAfterClassName1() throws Throwable {
    doBasicTest();
  }

  public void testThisKeywordCompletionAfterClassName2() throws Throwable {
    doBasicTest();
  }

  public void testCompletionInParameterListInClosableBlock() throws Throwable {
    doBasicTest();
  }

  public void testCompletionInParameterListInClosableBlock2() throws Throwable {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "aDouble");
  }

  public void testStaticMemberFromInstanceContext() throws Throwable {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "var1", "var2");
  }

  public void testInstanceMemberFromStaticContext() throws Throwable {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "var3", "var4");
  }

  public void testTypeCompletionInVariableDeclaration1() throws Throwable {
    doBasicTest();
  }

  public void testTypeCompletionInVariableDeclaration2() throws Throwable {
    doBasicTest();
  }

  public void testTypeCompletionInParameter() throws Throwable {
    doBasicTest();
  }

  public void testGStringConcatenationCompletion() throws Throwable {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "substring", "substring", "subSequence");
  }

  public void testPropertyWithSecondUpperLetter() throws Exception {
    myFixture.testCompletionVariants(getTestName(false) + ".groovy", "geteMail", "getePost");
  }

  public void testSmartCompletionInAssignmentExpression() throws Throwable {
    doSmartTest();
  }

  public void doSmartCompletion(String... variants) throws Exception {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART);
    final List<String> list = myFixture.getLookupElementStrings();
    assertNotNull(list);
    UsefulTestCase.assertSameElements(list, variants);
  }

  public void testSimpleMethodParameter() throws Throwable {
    doSmartCompletion("d1", "d2");
  }

  public void testReturnStatement() throws Exception {
    doSmartCompletion("b", "b1", "b2", "foo");
  }
}
