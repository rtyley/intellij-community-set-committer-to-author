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


public class AnnotationParserTest extends JavaParsingTestCase {
  public AnnotationParserTest() {
    super("parser-partial/annotations");
  }

  public void testMarker() { doParserTest("@Preliminary"); }
  public void testSimple0() { doParserTest("@Copyright(\"bla-bla-bla\")"); }
  public void testSimple1() { doParserTest("@Copyright(treatedAsValue)"); }
  public void testComplex() { doParserTest("@Author(first=\"Eugene\", second=\"Wampirchik\")"); }
  public void testMultiple() { doParserTest("@Preliminary @Other(name=value)"); }
  public void testArray() { doParserTest("@Endorsers({\"Children\", \"Unscrupulous dentists\"})"); }
  public void testNested() { doParserTest("@Author(@Name(first=\"Eugene\", second=\"Wampirchik\"))"); }
  public void testQualifiedAnnotation() { doParserTest("@org.jetbrains.annotations.Nullable"); }

  public void testParameterizedAnnotation () { doParserTest("@Nullable<T>"); }
  public void testFirstNameMissed() { doParserTest("@Anno(value1, param2=value2)"); }

  private void doParserTest(final String text) {
    doParserTest(text, new TestParser() {
      @Override
      public void parse(final PsiBuilder builder) {
        DeclarationParser.parseAnnotations(builder);
      }
    });
  }
}
