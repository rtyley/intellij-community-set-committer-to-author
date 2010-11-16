package com.intellij.codeInspection;

import com.intellij.ExtensionPoints;
import com.intellij.codeInspection.ex.EntryPointsManagerImpl;
import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.JDOMUtil;
import junit.framework.TestCase;
import org.jdom.Element;

public class EntryPointsConverterTest extends TestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Extensions.getRootArea().registerExtensionPoint(ExtensionPoints.DEAD_CODE_TOOL, EntryPoint.class.getName());
  }

  @Override
  protected void tearDown() throws Exception {
    Extensions.getRootArea().unregisterExtensionPoint(ExtensionPoints.DEAD_CODE_TOOL);
    super.tearDown();
  }

  public void testMethodConverter1() throws Exception {
    doTest("method", "String java.lang.String.replace(char oldChar, char newChar)", "java.lang.String String replace(char oldChar, char newChar)");
  }

  public void testMethodConverter2() throws Exception {
    doTest("method", "void java.lang.String.replace(char oldChar, char newChar)", "java.lang.String void replace(char oldChar, char newChar)");
  }

  public void testMethodConverter3() throws Exception {
    doTest("method", "java.lang.String.String(char oldChar)", "java.lang.String String(char oldChar)");
  }

  public void testFieldConverter() throws Exception {
    doTest("field", "java.lang.String.myFakeField", "java.lang.String myFakeField");
  }

  private static void doTest(String type, String fqName, String expectedFQName) throws Exception {
    final Element entryPoints = setUpEntryPoint(type, fqName);

    final EntryPointsManagerImpl manager = new EntryPointsManagerImpl(null);
    manager.convert(entryPoints);

    final Element testElement = new Element("comp");
    manager.writeExternal(testElement);

    final Element expectedEntryPoints = setUpEntryPoint(type, expectedFQName);
    expectedEntryPoints.setAttribute("version", "2.0");
    final Element expected = new Element("comp");
    expected.addContent(expectedEntryPoints);

    assertTrue(JDOMUtil.areElementsEqual(testElement, expected));
  }

  private static Element setUpEntryPoint(String type, String fqName) {
    Element entryPoints = new Element("entry_points");
    Element entryPoint = new Element("entry_point");
    entryPoint.setAttribute("TYPE", type);
    entryPoint.setAttribute("FQNAME", fqName);
    entryPoints.addContent(entryPoint);
    return entryPoints;
  }

}
