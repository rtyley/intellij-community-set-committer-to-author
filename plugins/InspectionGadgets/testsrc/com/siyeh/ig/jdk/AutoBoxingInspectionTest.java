package com.siyeh.ig.jdk;

import com.IGInspectionTestCase;

public class AutoBoxingInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/jdk/auto_boxing", new AutoBoxingInspection());
    }
}