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
package com.intellij.psi.formatter.java;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.IncorrectOperationException;

/**
 * Is intended to hold specific java formatting tests for alignment settings (
 * <code>Project Settings - Code Style - Alignment and Braces</code>).
 *
 * @author Denis Zhdanov
 * @since Apr 27, 2010 6:42:00 PM
 */
public class JavaFormatterAlignmentTest extends AbstractJavaFormatterTest {

  public void testChainedMethodsAlignment() throws Exception {
    // Inspired by IDEA-30369
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    getSettings().METHOD_CALL_CHAIN_WRAP = CodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().getIndentOptions(StdFileTypes.JAVA).CONTINUATION_INDENT_SIZE = 8;
    doTest();
  }

  public void testMultipleMethodAnnotationsCommentedInTheMiddle() throws Exception {
    getSettings().BLANK_LINES_AFTER_CLASS_HEADER = 1;
    getSettings().getIndentOptions(StdFileTypes.JAVA).INDENT_SIZE = 4;

    // Inspired by IDEA-53942
    doTextTest(
      "public class Test {\n" +
      "          @Override\n" +
      "//       @XmlElement(name = \"Document\", required = true, type = DocumentType.class)\n" +
      "       @XmlTransient\n" +
      "  void foo() {\n" +
      "}\n" +
      "}",

      "public class Test {\n" +
      "\n" +
      "    @Override\n" +
      "//       @XmlElement(name = \"Document\", required = true, type = DocumentType.class)\n" +
      "    @XmlTransient\n" +
      "    void foo() {\n" +
      "    }\n" +
      "}"
    );
  }

  public void testTernaryOperator() throws Exception {
    // Inspired by IDEADEV-13018
    getSettings().ALIGN_MULTILINE_TERNARY_OPERATION = true;

    doMethodTest("int i = a ? x\n" + ": y;", "int i = a ? x\n" + "          : y;");
  }

  public void testMethodCallArgumentsAndSmartTabs() throws IncorrectOperationException {
    // Inspired by IDEADEV-20144.
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    getSettings().getIndentOptions(StdFileTypes.JAVA).SMART_TABS = true;
    getSettings().getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = true;
    doTextTest("class Foo {\n" +
               "    void foo() {\n" +
               "        bar(new Object[] {\n" +
               "            \"hello1\",\n" +
               "            \"hello2\", add(\"hello3\",\n" +
               "                           \"world\")\n" +
               "});" +
               "    }}", "class Foo {\n" +
                         "\tvoid foo() {\n" +
                         "\t\tbar(new Object[]{\n" +
                         "\t\t\t\t\"hello1\",\n" +
                         "\t\t\t\t\"hello2\", add(\"hello3\",\n" +
                         "\t\t\t\t              \"world\")\n" +
                         "\t\t});\n" +
                         "\t}\n" +
                         "}");
  }

  public void testArrayInitializer() throws IncorrectOperationException {
    // Inspired by IDEADEV-16136
    getSettings().ARRAY_INITIALIZER_WRAP = CodeStyleSettings.WRAP_ALWAYS;
    getSettings().ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = true;

    doTextTest(
      "@SuppressWarnings({\"UseOfSystemOutOrSystemErr\", \"AssignmentToCollectionOrArrayFieldFromParameter\", \"ReturnOfCollectionOrArrayField\"})\n" +
      "public class Some {\n" +
      "}", "@SuppressWarnings({\"UseOfSystemOutOrSystemErr\",\n" +
           "                   \"AssignmentToCollectionOrArrayFieldFromParameter\",\n" +
           "                   \"ReturnOfCollectionOrArrayField\"})\n" +
           "public class Some {\n" +
           "}");
  }

  public void testMethodBrackets() throws Exception {
    // Inspired by IDEA-53013
    getSettings().ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = true;
    getSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    getSettings().METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true;

    doClassTest(
      "public void foo(int i,\n" +
      "                  int j) {\n" +
      "}\n" +
      "\n" +
      "  public void bar() {\n" +
      "    foo(1,\n" +
      "        2);\n" +
      "  }",

      "public void foo(int i,\n" +
      "                int j\n" +
      "               ) {\n" +
      "}\n" +
      "\n" +
      "public void bar() {\n" +
      "    foo(1,\n" +
      "        2\n" +
      "       );\n" +
      "}"
    );
  }
}
