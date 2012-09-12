/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.siyeh.ig.inheritance;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.PluginPathManager;
import org.jetbrains.annotations.NotNull;

/**
 * User: cdr
 */
public class StaticInheritanceTest extends LightQuickFixTestCase {
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new StaticInheritanceInspection()};
  }

  public void test() throws Exception {
    doAllTests();
  }

  @Override
  protected String getBasePath() {
    return "/com/siyeh/igtest/inheritance/staticInheritance/";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("InspectionGadgets") + "/test";
  }
}
