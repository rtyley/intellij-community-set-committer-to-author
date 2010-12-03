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
package org.jetbrains.plugins.groovy.completion;


import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.StaticallyImportable
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.util.TestUtils

 /**
 * @author Maxim.Medvedev
 */
public class GroovyClassNameCompletionTest extends LightCodeInsightFixtureTestCase {
  private boolean old;

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/completion/classNameCompletion";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    old = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION = true;
  }

  @Override
  protected void tearDown() throws Exception {
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CLASS_NAME_COMPLETION = old;
    super.tearDown();
  }

  public void doTest(boolean force) throws Exception {
    addClassToProject("a", "FooBar");
    myFixture.configureByFile(getTestName(false) + ".groovy");
    CommandProcessor.getInstance().executeCommand(new Runnable(){
                                                  @Override
                                                  void run() {
                                                    myFixture.complete(CompletionType.CLASS_NAME);
                                                    if (force) forceCompletion();
                                                  }
                                                  },"xxx", this);
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  private void forceCompletion() {
    CodeCompletionHandlerBase handler = new CodeCompletionHandlerBase(CompletionType.CLASS_NAME);
    handler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
    final LookupManager instance = LookupManager.getInstance(myFixture.getProject());
    if(instance.getActiveLookup() != null)
      instance.forceSelection(Lookup.NORMAL_SELECT_CHAR, 1);
  }

  private void addClassToProject(@Nullable String packageName, @NotNull String name) throws IOException {
    myFixture.addClass("package $packageName; public class $name {}");
  }

  public void testInFieldDeclaration() throws Exception {doTest(false);}
  public void testInParameter() throws Exception {doTest(false);}
  public void testInImport() throws Exception {doTest(false);}
  public void testWhenClassExistsInSamePackage() throws Exception {doTest(true);}
  public void testInComment() throws Exception {doTest(false);}
  public void testInTypeElementPlace() throws Exception {doTest(false);}
  public void testWhenImportExists() throws Exception{doTest(false);}

  public void testFinishByDot() throws Exception{
    addClassToProject("a", "FooBar");
    myFixture.configureByText("a.groovy", "FB<caret>a")
    myFixture.complete(CompletionType.CLASS_NAME)
    myFixture.type '.'.charAt(0)
    myFixture.checkResult "a.FooBar.<caret>a"
  }
  
  public void testDelegateBasicToClassName() throws Exception{
    addClassToProject("a", "FooBarGooDoo");
    myFixture.configureByText("a.groovy", "FBGD<caret>a")
    myFixture.completeBasic()
    myFixture.type '.'.charAt(0)
    myFixture.checkResult "a.FooBarGooDoo.<caret>a"
  }

  public void testDelegateBasicToClassNameAutoinsert() throws Exception{
    addClassToProject("a", "FooBarGooDoo");
    myFixture.configureByText("a.groovy", "FBGD<caret>")
    myFixture.completeBasic()
    myFixture.checkResult "a.FooBarGooDoo<caret>"
  }

  public void testImportedStaticMethod() throws Exception {
    myFixture.addFileToProject("b.groovy", """
class Foo {
  static def abcmethod1(int a) {}
  static def abcmethod2(int a) {}
}""")
    myFixture.configureByText("a.groovy", """def foo() {
  abcme<caret>
}""")
    def item = myFixture.complete(CompletionType.CLASS_NAME)[0]
    ((StaticallyImportable) item).shouldBeImported = true
    myFixture.type('\n')
    myFixture.checkResult """import static Foo.abcmethod1

def foo() {
  abcmethod1<caret>
}"""

  }

  public void testQualifiedStaticMethod() throws Exception {
    myFixture.addFileToProject("foo/b.groovy", """package foo
class Foo {
  static def abcmethod(int a) {}
}""")
    myFixture.configureByText("a.groovy", """def foo() {
  abcme<caret>
}""")
    myFixture.complete(CompletionType.CLASS_NAME)
    myFixture.checkResult """import foo.Foo

def foo() {
  Foo.abcmethod<caret>
}"""

  }

  public void testQualifiedStaticMethodIfThereAreAlreadyStaticImportsFromThatClass() throws Exception {
    myFixture.addFileToProject("foo/b.groovy", """package foo
class Foo {
  static def abcMethod() {}
  static def anotherMethod() {}
}""")
    myFixture.configureByText("a.groovy", """
import static foo.Foo.anotherMethod

anotherMethod()
abcme<caret>x""")
    assertOneElement myFixture.complete(CompletionType.CLASS_NAME)[0]
    myFixture.type('\t')
    myFixture.checkResult """
import static foo.Foo.anotherMethod
import static foo.Foo.abcMethod

anotherMethod()
abcMethod()<caret>"""

  }

  public void testNewClassName() {
    addClassToProject("foo", "Fxoo")
    myFixture.configureByText("a.groovy", "new Fxo<caret>\n")
    myFixture.completeBasic()
    myFixture.checkResult """import foo.Fxoo

new Fxoo()<caret>\n"""

  }

  public void testNewImportedClassName() {
    myFixture.configureByText("a.groovy", "new ArrayLi<caret>\n")
    myFixture.completeBasic()
    myFixture.checkResult "new ArrayList(<caret>)\n"

  }



}
