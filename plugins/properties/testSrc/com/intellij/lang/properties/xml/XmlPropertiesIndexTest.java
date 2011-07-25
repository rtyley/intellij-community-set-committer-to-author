package com.intellij.lang.properties.xml;

import com.intellij.util.indexing.FileContent;
import junit.framework.TestCase;

import java.util.Map;

/**
 * @author Dmitry Avdeev
 *         Date: 7/25/11
 */
public class XmlPropertiesIndexTest extends TestCase {

  public void testIndex() throws Exception {
    Map<String,String> map = new XmlPropertiesIndex().map(new FileContent(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                                                           "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\n" +
                                                                           "<properties>\n" +
                                                                           "<comment>Hi</comment>\n" +
                                                                           "<entry key=\"foo\">bar</entry>\n" +
                                                                           "<entry key=\"fu\">baz</entry>\n" +
                                                                           "</properties>").getBytes()));

    assertEquals(2, map.size());
    assertEquals("bar", map.get("foo"));
    assertEquals("baz", map.get("fu"));
  }

  public void testSystemId() throws Exception {
    Map<String,String> map = new XmlPropertiesIndex().map(new FileContent(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                                                           "<!DOCTYPE properties SYSTEM \"unknown\">\n" +
                                                                           "<properties>\n" +
                                                                           "<comment>Hi</comment>\n" +
                                                                           "<entry key=\"foo\">bar</entry>\n" +
                                                                           "<entry key=\"fu\">baz</entry>\n" +
                                                                           "</properties>").getBytes()));

    assertEquals(0, map.size());
  }
}
