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
package org.intellij.lang.xpath;

public class XPath2CompletionTest extends TestBase {

  public void testCastInsert() throws Throwable {
    final String name = getTestFileName();
    myFixture.testCompletion(name + ".xpath2", name + "_after.xpath2");
  }

  public void testTreatInsert() throws Throwable {
    final String name = getTestFileName();
    myFixture.configureByFile(name + ".xpath2");
    myFixture.completeBasic();
    myFixture.type('\n');
    myFixture.checkResultByFile(name + "_after.xpath2");
  }

  @Override
  protected String getSubPath() {
    return "xpath2/completion";
  }
}