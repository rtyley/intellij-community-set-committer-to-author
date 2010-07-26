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
package com.intellij.lang.java.parser.partial;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.DeclarationParser;
import com.intellij.lang.java.parser.JavaParsingTestCase;


public class DeclarationParserTest extends JavaParsingTestCase {
  public DeclarationParserTest() {
    super("parser-partial/declarations");
  }

  public void testEmptyBody0() { doParserTest("{ }", false, false); }
  public void testEmptyBody1() { doParserTest("{ ", false, false); }

  public void testEnumBody0() { doParserTest("{ ; }", false, true); }
  public void testEnumBody1() { doParserTest("{ RED, GREEN, BLUE; }", false, true); }
  public void testEnumBody2() { doParserTest("{ RED, GREEN, BLUE }", false, true); }
  public void testEnumBody3() { doParserTest("{ RED, GREEN, BLUE, }", false, true); }
  public void testEnumBody4() { doParserTest("{ RED(0), GREEN(1), BLUE(2); }", false, true); }
  public void testEnumBody5() { doParserTest("{ @ANNOTATION A(10) }", false, true); }

  public void testFieldSimple() { doParserTest("{ int field = 0; }", false, false); }
  public void testFieldMulti() { doParserTest("{ int field1 = 0, field2; }", false, false); }
  public void testUnclosedBracket() { doParserTest("{ int field[ }", false, false); }
  public void testMissingInitializer() { doParserTest("{ int field = }", false, false); }
  public void testUnclosedComma() { doParserTest("{ int field, }", false, false); }
  public void testUnclosedSemicolon() { doParserTest("{ int field }", false, false); }
  public void testMissingInitializerExpression() { doParserTest("{ int field=; }", false, false); }
  //public void testMultiLineUnclosed() { doParserTest("{ int \n  Object o; }", false, false); }  // todo: implement

  private void doParserTest(final String text, final boolean isAnnotation, final boolean isEnum) {
    doParserTest(text, new Parser() {
      public void parse(final PsiBuilder builder) {
        DeclarationParser.parseClassBodyWithBraces(builder, isAnnotation, isEnum);
      }
    });
  }
}
