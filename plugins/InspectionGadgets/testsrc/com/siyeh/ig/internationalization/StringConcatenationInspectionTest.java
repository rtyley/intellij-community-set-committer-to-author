package com.siyeh.ig.internationalization;

import com.siyeh.ig.IGInspectionTestCase;

public class StringConcatenationInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/internationalization/string_concatenation",
           new StringConcatenationInspection());
  }
}