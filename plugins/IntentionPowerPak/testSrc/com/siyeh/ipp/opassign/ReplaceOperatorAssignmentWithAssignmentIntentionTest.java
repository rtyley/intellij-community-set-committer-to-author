package com.siyeh.ipp.opassign;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

public class ReplaceOperatorAssignmentWithAssignmentIntentionTest extends IPPTestCase {
  public void testOperatorAssignment1() { doTest(); }
  public void DoubleOpAssign() { doTest(); }
  public void StringOpAssign() { doTest(); }
  public void ByteOpAssign() { doTest(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("replace.operator.assignment.with.assignment.intention.name", "+=");
  }

  @Override
  protected String getRelativePath() {
    return "opassign/assignment";
  }
}
