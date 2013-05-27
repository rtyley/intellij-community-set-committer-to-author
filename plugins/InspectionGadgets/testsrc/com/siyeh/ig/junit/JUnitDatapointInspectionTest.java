/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * User: ddt
 * Date: 22-Mai-2013
 */
package com.siyeh.ig.junit;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class JUnitDatapointInspectionTest extends LightCodeInsightFixtureTestCase {
  public void testWrongdatapoint() throws Exception {
    myFixture.addClass("package org.junit.experimental.theories;\n" +
                       "public @interface DataPoint {}");
    myFixture.testHighlighting(true, false, false, getTestName(true) + ".java");
  }


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new JUnitDatapointInspection());
  }

  @NotNull
   @Override
   protected String getTestDataPath() {
     return PluginPathManager.getPluginHomePath("InspectionGadgets") + "/test/com/siyeh/igtest/junit/rule/";
   }
}
