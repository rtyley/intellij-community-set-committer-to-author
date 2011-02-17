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
package com.siyeh.ig.errorhandling;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author yole
 */
public class TryWithIdenticalCatchesTest extends LightCodeInsightFixtureTestCase {
  public void test() {
    myFixture.enableInspections(TryWithIdenticalCatchesInspection.class);
    myFixture.configureByFile("com/siyeh/igtest/errorhandling/try_identical_catches/TryIdenticalCatches.java");
    myFixture.checkHighlighting(true, false, false);
  }

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("InspectionGadgets") + "/test";
  }
}
