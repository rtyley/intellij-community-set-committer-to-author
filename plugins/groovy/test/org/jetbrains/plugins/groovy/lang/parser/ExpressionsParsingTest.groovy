/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.parser

/**
 * @author peter
 */
public class ExpressionsParsingTest extends GroovyParsingTestCase {
  @Override
  protected String getBasePath() {
    return super.getBasePath() + "expressions";
  }

  public void testarguments$carg1() throws Throwable { doTest(); }
  public void testarguments$carg2() throws Throwable { doTest(); }
  public void testarguments$carg3() throws Throwable { doTest(); }
  public void testarguments$cargs1() throws Throwable { doTest(); }
  public void testarguments$cargs2() throws Throwable { doTest(); }
  public void testarguments$cargs3() throws Throwable { doTest(); }
  public void testarithmetic$add1() throws Throwable { doTest(); }
  public void testarithmetic$add2() throws Throwable { doTest(); }
  public void testarithmetic$addbug1() throws Throwable { doTest(); }
  public void testarithmetic$arif1() throws Throwable { doTest(); }
  public void testarithmetic$mul1() throws Throwable { doTest(); }
  public void testarithmetic$mul2() throws Throwable { doTest(); }
  public void testarithmetic$mul3() throws Throwable { doTest(); }
  public void testarithmetic$post1() throws Throwable { doTest(); }
  public void testarithmetic$sh1() throws Throwable { doTest(); }
  public void testarithmetic$shift5() throws Throwable { doTest(); }
  public void testarithmetic$shift6() throws Throwable { doTest(); }
  public void testarithmetic$un1() throws Throwable { doTest(); }
  public void testass1() throws Throwable { doTest(); }
  public void testass2() throws Throwable { doTest(); }
  public void testass3() throws Throwable { doTest(); }
  public void testclosures$appended() throws Throwable { doTest(); }
  public void testclosures$closparam1() throws Throwable { doTest(); }
  public void testclosures$closparam2() throws Throwable { doTest(); }
  public void testclosures$closparam3() throws Throwable { doTest(); }
  public void testclosures$closparam4() throws Throwable { doTest(); }
  public void testclosures$closparam5() throws Throwable { doTest(); }
  public void testclosures$closparam6() throws Throwable { doTest(); }
  public void testclosures$final_error() throws Throwable { doTest(); }
  public void testclosures$param6() throws Throwable { doTest(); }
  public void testclosures$param7() throws Throwable { doTest(); }
  public void testconditional$con1() throws Throwable { doTest(); }
  public void testconditional$con2() throws Throwable { doTest(); }
  public void testconditional$elvis1() throws Throwable { doTest(); }
  public void testconditional$elvis2() throws Throwable { doTest(); }
  public void testerrors$err_final() throws Throwable { doTest(); }
  public void testgstring$daniel_sun() throws Throwable { doTest(); }
  public void testgstring$gravy16532() throws Throwable { doTest("gstring/gravy-1653-2.test"); }
  public void testgstring$grvy1653() throws Throwable { doTest("gstring/grvy-1653.test"); }
  public void testgstring$gstr3() throws Throwable { doTest(); }
  public void testgstring$standTrooper() throws Throwable { doTest(); }
  public void testgstring$str1() throws Throwable { doTest(); }
  public void testgstring$str2() throws Throwable { doTest(); }
  public void testgstring$str3() throws Throwable { doTest(); }
  public void testgstring$str4() throws Throwable { doTest(); }
  public void testgstring$str5() throws Throwable { doTest(); }
  public void testgstring$str6() throws Throwable { doTest(); }
  public void testgstring$str7() throws Throwable { doTest(); }
  public void testgstring$str8() throws Throwable { doTest(); }
  public void testgstring$str_error1() throws Throwable { doTest(); }
  public void testgstring$str_error2() throws Throwable { doTest(); }
  public void testgstring$str_error3() throws Throwable { doTest(); }
  public void testgstring$str_error4() throws Throwable { doTest(); }
  public void testgstring$str_error5() throws Throwable { doTest(); }
  public void testgstring$str_error6() throws Throwable { doTest(); }
  public void testgstring$str_error7() throws Throwable { doTest(); }
  public void testgstring$str_error8() throws Throwable { doTest(); }
  public void testgstring$triple$triple1() throws Throwable { doTest(); }
  public void testgstring$triple$triple2() throws Throwable { doTest(); }
  public void testgstring$triple$triple3() throws Throwable { doTest(); }
  public void testgstring$triple$quote_and_slash() throws Throwable { doTest(); }
  public void testgstring$ugly_lexer() throws Throwable { doTest(); }
  public void testmapLiteral() throws Throwable { doTest(); }
  public void testnew$arr_decl() throws Throwable { doTest(); }
//  public void testnew$new1() throws Throwable { doTest(); }  
  public void testanonymous$anonymous() throws Throwable { doTest(); }
  public void testnumbers() throws Throwable { doTest(); }
  public void testparenthed$exprInParenth() throws Throwable { doTest(); }
  public void testparenthed$paren1() throws Throwable { doTest(); }
  public void testparenthed$paren2() throws Throwable { doTest(); }
  public void testparenthed$paren3() throws Throwable { doTest(); }
  public void testparenthed$paren4() throws Throwable { doTest(); }
  public void testparenthed$paren5() throws Throwable { doTest(); }
  public void testparenthed$paren6() throws Throwable { doTest(); }
  public void testpath$method$ass4() throws Throwable { doTest(); }
  public void testpath$method$clazz1() throws Throwable { doTest(); }
  public void testpath$method$clazz2() throws Throwable { doTest(); }
  public void testpath$method$clos1() throws Throwable { doTest(); }
  public void testpath$method$clos2() throws Throwable { doTest(); }
  public void testpath$method$clos3() throws Throwable { doTest(); }
  public void testpath$method$clos4() throws Throwable { doTest(); }
  public void testpath$method$ind1() throws Throwable { doTest(); }
  public void testpath$method$ind2() throws Throwable { doTest(); }
  public void testpath$method$ind3() throws Throwable { doTest(); }
  public void testpath$method$method1() throws Throwable { doTest(); }
  public void testpath$method$method10() throws Throwable { doTest(); }
  public void testpath$method$method11() throws Throwable { doTest(); }
  public void testpath$method$method12() throws Throwable { doTest(); }
  public void testpath$method$method13() throws Throwable { doTest(); }
  public void testpath$method$method2() throws Throwable { doTest(); }
  public void testpath$method$method3() throws Throwable { doTest(); }
  public void testpath$method$method4() throws Throwable { doTest(); }
  public void testpath$method$method5() throws Throwable { doTest(); }
  public void testpath$method$method6() throws Throwable { doTest(); }
  public void testpath$method$method7() throws Throwable { doTest(); }
  public void testpath$method$method8() throws Throwable { doTest(); }
  public void testpath$method$method9() throws Throwable { doTest(); }
  public void testpath$path1() throws Throwable { doTest(); }
  public void testpath$path13() throws Throwable { doTest(); }
  public void testpath$path14() throws Throwable { doTest(); }
  public void testpath$path2() throws Throwable { doTest(); }
  public void testpath$path3() throws Throwable { doTest(); }
  public void testpath$path4() throws Throwable { doTest(); }
  public void testpath$path5() throws Throwable { doTest(); }
  public void testpath$path6() throws Throwable { doTest(); }
  public void testpath$path7() throws Throwable { doTest(); }
  public void testpath$path8() throws Throwable { doTest(); }
  public void testpath$path9() throws Throwable { doTest(); }
  public void testpath$path10() throws Throwable {doTest(); }
  public void testpath$typeVsExpr() {doTest();}
  public void testreferences$ref1() throws Throwable { doTest(); }
  public void testreferences$ref2() throws Throwable { doTest(); }
  public void testreferences$ref3() throws Throwable { doTest(); }
  public void testreferences$ref4() throws Throwable { doTest(); }
  public void testreferences$ref5() throws Throwable { doTest(); }
  public void testreferences$ref6() throws Throwable { doTest(); }
  public void testreferences$ref7() throws Throwable { doTest(); }
  public void testregex$chen() throws Throwable { doTest(); }
  public void testregex$GRVY1509err() throws Throwable { doTest("regex/GRVY-1509err.test"); }
  public void testregex$GRVY1509norm() throws Throwable { doTest("regex/GRVY-1509norm.test"); }
  public void testregex$GRVY1509test() throws Throwable { doTest("regex/GRVY-1509test.test"); }
  public void testregex$regex1() throws Throwable { doTest(); }
  public void testregex$regex10() throws Throwable { doTest(); }
  public void testregex$regex11() throws Throwable { doTest(); }
  public void testregex$regex12() throws Throwable { doTest(); }
  public void testregex$regex13() throws Throwable { doTest(); }
  public void testregex$regex14() throws Throwable { doTest(); }
  public void testregex$regex2() throws Throwable { doTest(); }
  public void testregex$regex3() throws Throwable { doTest(); }
  public void testregex$regex33() throws Throwable { doTest(); }
  public void testregex$regex4() throws Throwable { doTest(); }
  public void testregex$regex5() throws Throwable { doTest(); }
  public void testregex$regex6() throws Throwable { doTest(); }
  public void testregex$regex7() throws Throwable { doTest(); }
  public void testregex$regex8() throws Throwable { doTest(); }
  public void testregex$regex9() throws Throwable { doTest(); }
  public void testregex$regex_begin() throws Throwable { doTest(); }
  public void testregex$regex_begin2() throws Throwable { doTest(); }
  public void testrelational$eq1() throws Throwable { doTest(); }
  public void testrelational$inst0() throws Throwable { doTest(); }
  public void testrelational$inst1() throws Throwable { doTest(); }
  public void testrelational$inst2() throws Throwable { doTest(); }
  public void testrelational$rel1() throws Throwable { doTest(); }
  public void testspecial$grvy1173() throws Throwable { doTest(); }
  public void testspecial$list1() throws Throwable { doTest(); }
  public void testspecial$list2() throws Throwable { doTest(); }
  public void testspecial$list3() throws Throwable { doTest(); }
  public void testspecial$map1() throws Throwable { doTest(); }
  public void testspecial$map2() throws Throwable { doTest(); }
  public void testspecial$map3() throws Throwable { doTest(); }
  public void testspecial$map4() throws Throwable { doTest(); }
  public void testspecial$map5() throws Throwable { doTest(); }
  public void testspecial$map6() throws Throwable { doTest(); }
  public void testspecial$map7() throws Throwable { doTest(); }
  public void testspecial$map8() throws Throwable { doTest(); }
  public void testspecial$paren13() throws Throwable { doTest(); }
  public void testtypecast$castToObject() throws Throwable { doTest(); }
  public void testtypecast$una1() throws Throwable { doTest(); }
  public void testtypecast$una2() throws Throwable { doTest(); }
  public void testtypecast$una3() throws Throwable { doTest(); }
  public void testtypecast$una4() throws Throwable { doTest(); }
  public void testtypecast$una5() throws Throwable { doTest(); }
  public void testtypecast$una6() throws Throwable { doTest(); }
  public void testtypecast$elvis() throws Throwable { doTest(); }
  public void testtypecast$conditional() throws Throwable { doTest(); }

  public void testAtHang() throws Throwable { doTest(); }
  public void testDollar() throws Throwable { doTest(); }

  public void testNoArrowClosure() throws Throwable { doTest(); }
  public void testNoArrowClosure2() throws Throwable { doTest(); }

  public void testPropertyAccessError() throws Throwable {
    checkParsing "a[b{}}", """Groovy script
  Property by index
    Reference expression
      PsiElement(identifier)('a')
    Arguments
      PsiElement([)('[')
      Method call
        Reference expression
          PsiElement(identifier)('b')
        Arguments
          <empty list>
        Closable block
          PsiElement({)('{')
          Parameter list
            <empty list>
          PsiElement(})('}')
      PsiErrorElement:',' or ']' expected
        <empty list>
  PsiErrorElement:';' or new line expected
    PsiElement(})('}')"""
  }
  public void testthis$qualifiedThis() throws Throwable {doTest();}
  public void testsuper$qualifiedSuper() throws Throwable {doTest();}
  public void testthis$this() throws Throwable {doTest();}
  public void testsuper$super() throws Throwable {doTest();}

  public void testTripleEqual() throws Exception {
    checkParsing "2===3", """Groovy script
  Relational expression
    Literal
      PsiElement(Integer)('2')
    PsiElement(==)('===')
    Literal
      PsiElement(Integer)('3')
"""
  }

  public void testcommandExpr$closureArg() {doTest()}
  public void testcommandExpr$simple() {doTest()}
  public void testcommandExpr$callArg1() {doTest()}
  public void testcommandExpr$callArg2() {doTest()}
  public void testcommandExpr$threeArgs1() {doTest()}
  public void testcommandExpr$threeArgs2() {doTest()}
  public void testcommandExpr$threeArgs3() {doTest()}
  public void testcommandExpr$fourArgs() {doTest()}
  public void testcommandExpr$fiveArgs() {doTest()}
  public void testcommandExpr$multiArgs() {doTest()}
  public void testcommandExpr$RHS() {doTest()}
  public void testcommandExpr$oddArgCount() {doTest()}
  public void testcommandExpr$indexAccess1() {doTest()}
  public void testcommandExpr$indexAccess2() {doTest()}
  public void testcommandExpr$indexAccess3() {doTest()}
  public void testcommandExpr$closureArg2() {doTest()}
  public void testcommandExpr$closureArg3() {doTest()}
  public void testcommandExpr$not() {doTest()}
  /*def testCommandExpr$() {doTest()}
  def testCommandExpr$() {doTest()}
  def testCommandExpr$() {doTest()}
  def testCommandExpr$() {doTest()}
  def testCommandExpr$() {doTest()}*/

}
