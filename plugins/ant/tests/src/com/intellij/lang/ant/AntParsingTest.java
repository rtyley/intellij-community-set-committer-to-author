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

package com.intellij.lang.ant;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.ParsingTestCase;

public class AntParsingTest extends ParsingTestCase {

  public AntParsingTest() {
    super("", "ant");
  }

  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ant") + "/tests/data";
  }

  public void testSingleProject() throws Exception {
    doTest(true);
  }

  public void testProjectWithDefaultTarget() throws Exception {
    doTest(true);
  }

  public void testProjectWithDescriptions() throws Exception {
    doTest(true);
  }

  public void testProjectWithDependentTargets() throws Exception {
    doTest(true);
  }

  public void testSimpleProperties() throws Exception {
    doTest(true);
  }

  public void testPropertyLocation() throws Exception {
    doTest(true);
  }

  public void testPropertyFile() throws Exception {
    doTest(true);
  }

  public void testSimpleAntCall() throws Exception {
    doTest(true);
  }

  public void testAntCallNestedTargets() throws Exception {
    doTest(true);
  }

  public void testAntComments() throws Exception {
    doTest(true);
  }

  public void testPrologEpilogue() throws Exception {
    doTest(true);
  }

  public void testMacroDef() throws Exception {
    doTest(true);
  }

  public void testPresetDef() throws Exception {
    doTest(true);
  }

  public void testForwardMacroDef() throws Exception {
    doTest(true);
  }

  public void testSequentialParallel() throws Exception {
    doTest(true);
  }

  public void testAvailable() throws Exception {
    doTest(true);
  }

  public void testChecksum() throws Exception {
    doTest(true);
  }

  public void testChecksum1() throws Exception {
    doTest(true);
  }

  public void testCondition() throws Exception {
    doTest(true);
  }

  public void testUptodate() throws Exception {
    doTest(true);
  }

  public void testDirname() throws Exception {
    doTest(true);
  }

  public void testBasename() throws Exception {
    doTest(true);
  }

  public void testLoadFile() throws Exception {
    doTest(true);
  }

  public void testTempFile() throws Exception {
    doTest(true);
  }

  public void testLength() throws Exception {
    doTest(true);
  }

  public void testPathConvert() throws Exception {
    doTest(true);
  }

  public void testWhichResource() throws Exception {
    doTest(true);
  }

  public void testP4Counter() throws Exception {
    doTest(true);
  }

  public void testJarLibResolve() throws Exception {
    doTest(true);
  }

  public void testAntTask() throws Exception {
    doTest(true);
  }

  public void testJava() throws Exception {
    doTest(true);
  }

   public void testBuildNumber() throws Exception {
    doTest(true);
  }
}