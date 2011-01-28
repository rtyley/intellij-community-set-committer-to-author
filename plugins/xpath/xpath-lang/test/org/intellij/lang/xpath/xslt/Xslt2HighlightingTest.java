/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.intellij.lang.xpath.xslt;

import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.util.ArrayUtil;
import org.intellij.lang.xpath.TestBase;
import org.intellij.lang.xpath.xslt.impl.XsltStuffProvider;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 12.06.2008
*/
public class Xslt2HighlightingTest extends TestBase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(XsltStuffProvider.INSPECTION_CLASSES);
    ExternalResourceManagerEx.getInstanceEx().addIgnoredResource("urn:my");
  }

  public void testCurrentMode() throws Throwable {
    doXsltHighlighting();
  }

  public void testXslt2Example() throws Throwable {
    doXsltHighlighting();
  }

  public void testTypeResolving() throws Throwable {
    doXsltHighlighting();
  }

  private void doXsltHighlighting(String... moreFiles) throws Throwable {
    final String name = getTestFileName();
    myFixture.testHighlighting(true, false, false, ArrayUtil.append(moreFiles, name + ".xsl"));
  }

  @Override
  protected String getSubPath() {
    return "xslt2/highlighting";
  }
}
