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
package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class SmartType17CompletionTest extends LightFixtureCompletionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/smartType/";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST;
  }

  public void testDiamondCollapsed() throws Exception {
    doTest();
  }

  public void testDiamondNotCollapsed() throws Exception {
    doTest();
  }

  public void testDiamondPresentation() {
    configureByFile("/" + getTestName(false) + ".java");
    LookupElementPresentation presentation = new LookupElementPresentation();
    myItems[0].renderElement(presentation);
    assertEquals("MyDD<>", presentation.getItemText());
  }


  private void doTest() throws Exception {
    configureByFile("/" + getTestName(false) + ".java");
    if (myItems != null && myItems.length == 1) {
      final Lookup lookup = getLookup();
      if (lookup != null) {
        selectItem(lookup.getCurrentItem(), Lookup.NORMAL_SELECT_CHAR);
      }
    }
    checkResultByFile("/" + getTestName(false) + "-out.java");
  }
}
