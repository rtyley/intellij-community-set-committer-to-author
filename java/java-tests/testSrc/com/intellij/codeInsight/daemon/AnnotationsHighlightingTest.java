package com.intellij.codeInsight.daemon;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */
public class AnnotationsHighlightingTest extends LightDaemonAnalyzerTestCase {

  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/annotations";

  private void doTest(boolean checkWarnings) throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, false);
  }

  public void testnotValueNameOmmited() throws Exception { doTest(false); }
  public void testcannotFindMethod() throws Exception { doTest(false); }
  public void testincompatibleType1() throws Exception { doTest(false); }
  public void testincompatibleType2() throws Exception { doTest(false); }
  public void testincompatibleType3() throws Exception { doTest(false); }
  public void testincompatibleType4() throws Exception { doTest(false); }
  public void testmissingAttribute() throws Exception { doTest(false); }
  public void testduplicateAnnotation() throws Exception { doTest(false); }
  public void testnonconstantInitializer() throws Exception { doTest(false); }
  public void testinvalidType() throws Exception { doTest(false); }
  public void testinapplicable() throws Exception { doTest(false); }
  public void testduplicateAttribute() throws Exception { doTest(false); }
  public void testduplicateTarget() throws Exception { doTest(false); }
  public void testTypeAnnotations() throws Exception { doTest(false); }

  public void testInvalidPackageAnnotationTarget() throws Exception {
    doTest(BASE_PATH + "/" + getTestName(true) + "/package-info.java", false, false);
  }

  public void testPackageAnnotationNotInPackageInfo() throws Exception {
    doTest(BASE_PATH + "/" + getTestName(true) + "/notPackageInfo.java", false, false);
  }
}
