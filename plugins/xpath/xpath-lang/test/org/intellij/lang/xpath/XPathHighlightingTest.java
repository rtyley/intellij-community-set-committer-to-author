/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.intellij.lang.xpath;

import com.intellij.util.ArrayUtil;

public class XPathHighlightingTest extends TestBase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new XPathSupportLoader().getInspectionClasses());
    }

    public void testPathTypeMismatch() throws Throwable {
        doXPathHighlighting();
    }

    public void testUnknownFunction() throws Throwable {
        doXPathHighlighting();
    }

    public void testMissingArgument() throws Throwable {
        doXPathHighlighting();
    }

    public void testInvalidArgument() throws Throwable {
        doXPathHighlighting();
    }

    public void testIndexZero() throws Throwable {
        doXPathHighlighting();
    }

    public void testSillyStep() throws Throwable {
        doXPathHighlighting();
    }

    public void testNonSillyStepIDEADEV33539() throws Throwable {
        doXPathHighlighting();
    }

    public void testHardwiredPrefix() throws Throwable {
        doXPathHighlighting();
    }

    private void doXPathHighlighting(String... moreFiles) throws Throwable {
        final String name = getTestFileName();
        myFixture.testHighlighting(true, false, false, ArrayUtil.append(moreFiles, name + ".xpath"));
    }

    @Override
    protected String getSubPath() {
        return "xpath/highlighting";
    }
}
