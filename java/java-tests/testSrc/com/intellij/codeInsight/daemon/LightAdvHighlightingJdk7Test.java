package com.intellij.codeInsight.daemon;

import com.intellij.ExtensionPoints;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * This class is for "lightweight" tests only, i.e. those which can run inside default light project set up
 * For "heavyweight" tests use AdvHighlightingTest
 */
public class LightAdvHighlightingJdk7Test extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting7";

  private void doTest(boolean checkWarnings, boolean checkInfos) throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, checkInfos);
  }

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new UnusedSymbolLocalInspection()};
  }

  public void testDuplicateAnnotations() throws Exception {
    doTest(false, false);
  }

  public void testSwitchByString() throws Exception {
    doTest(false, false);
  }

  public void testDiamondPos1() throws Exception {
    doTest(false, false);
  }

  public void testDiamondPos2() throws Exception {
    doTest(false, false);
  }

  public void testDiamondPos3() throws Exception {
    doTest(false, false);
  }

  public void testDiamondPos4() throws Exception {
    doTest(false, false);
  }

  public void testDiamondNeg1() throws Exception {
    doTest(false, false);
  }

  public void testDiamondNeg2() throws Exception {
    doTest(false, false);
  }

  public void testDiamondNeg3() throws Exception {
    doTest(false, false);
  }

  public void testDiamondNeg4() throws Exception {
    doTest(false, false);
  }

  public void testDiamondNeg5() throws Exception {
    doTest(false, false);
  }

  public void testDiamondMisc() throws Exception {
    doTest(false, false);
  }

  public void testDynamicallyAddIgnoredAnnotations() throws Exception {
    ExtensionPoint<EntryPoint> point = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.DEAD_CODE_TOOL);
    EntryPoint extension = new EntryPoint() {
      @NotNull
      @Override
      public String getDisplayName() {
        return "duh";
      }

      @Override
      public boolean isEntryPoint(RefElement refElement, PsiElement psiElement) {
        return false;
      }

      @Override
      public boolean isEntryPoint(PsiElement psiElement) {
        return false;
      }

      @Override
      public boolean isSelected() {
        return false;
      }

      @Override
      public void setSelected(boolean selected) {

      }

      @Override
      public void readExternal(Element element) {

      }

      @Override
      public void writeExternal(Element element) {

      }

      @Override
      public String[] getIgnoreAnnotations() {
        return new String[]{"MyAnno"};
      }
    };

    UnusedDeclarationInspection deadCodeInspection = new UnusedDeclarationInspection();
    enableInspectionTool(deadCodeInspection);

    doTest(true, false);
    List<HighlightInfo> infos = DaemonAnalyzerTestCase.filter(doHighlighting(), HighlightSeverity.WARNING);
    assertEquals(2, infos.size()); // unused class and unused method

    try {
      point.registerExtension(extension);

      infos = DaemonAnalyzerTestCase.filter(doHighlighting(), HighlightSeverity.WARNING);
      HighlightInfo info = assertOneElement(infos);
      assertEquals("Class 'WithMain' is never used", info.description);
    }
    finally {
      point.unregisterExtension(extension);
    }
  }

}
