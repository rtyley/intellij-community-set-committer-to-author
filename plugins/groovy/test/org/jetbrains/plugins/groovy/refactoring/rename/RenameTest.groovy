package org.jetbrains.plugins.groovy.refactoring.rename;


import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author ven
 */
public class RenameTest extends LightCodeInsightFixtureTestCase {

  public void testClosureIt() throws Throwable { doTest(); }
  public void testTo_getter() throws Throwable { doTest(); }
  public void testTo_prop() throws Throwable { doTest(); }
  public void testTo_setter() throws Throwable { doTest(); }
  public void testScriptMethod() throws Throwable { doTest(); }

  public void testParameterIsNotAUsageOfGroovyParameter() throws Exception {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
def foo(f) {
  // Parameter
  println 'Parameter' // also
  return <caret>f
}
""")
    def txt = "Just the Parameter word, which shouldn't be renamed"
    def txtFile = myFixture.addFileToProject("a.txt", txt)

    def parameter = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset).resolve()
    myFixture.renameElement(parameter, "newName", true, true)
    myFixture.checkResult """
def foo(newName) {
  // Parameter
  println 'Parameter' // also
  return <caret>newName
}
"""
    assertEquals txt, txtFile.text
  }

  public void testPreserveUnknownImports() throws Exception {
    def someClass = myFixture.addClass("public class SomeClass {}")

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
import foo.bar.Zoo
SomeClass c = new SomeClass()
""")
    myFixture.renameElement(someClass, "NewClass")
    myFixture.checkResult """
import foo.bar.Zoo
NewClass c = new NewClass()
"""
  }

  public void testRenameGetter() throws Exception {
    def clazz = myFixture.addClass("class Foo { def getFoo(){}}")
    def methods = clazz.findMethodsByName("getFoo", false)
    myFixture.configureByText("a.groovy", "print new Foo().foo")
    myFixture.renameElement methods[0], "get"
    myFixture.checkResult "print new Foo().get()"
  }

  public void testRenameSetter() throws Exception {
    def clazz = myFixture.addClass("class Foo { def setFoo(def foo){}}")
    def methods = clazz.findMethodsByName("setFoo", false)
    myFixture.configureByText("a.groovy", "print new Foo().foo = 2")
    myFixture.renameElement methods[0], "set"
    myFixture.checkResult "print new Foo().set(2)"
  }


  public void doTest() throws Throwable {
    final String testFile = getTestName(true).replace('$', '/') + ".test";
    final List<String> list = TestUtils.readInput(TestUtils.getAbsoluteTestDataPath() + "groovy/refactoring/rename/" + testFile);

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, list.get(0));

    PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    final PsiElement resolved = ref == null ? null : ref.resolve();
    if (resolved instanceof PsiMethod && !(resolved instanceof GrAccessorMethod)) {
      PsiMethod method = (PsiMethod)resolved;
      String name = method.getName();
      String newName = createNewNameForMethod(name);
      myFixture.renameElementAtCaret(newName);
    } else if (resolved instanceof GrAccessorMethod) {
      GrField field = ((GrAccessorMethod)resolved).getProperty();
      RenameProcessor processor = new RenameProcessor(myFixture.getProject(), field, "newName", true, true);
      processor.addElement(resolved, createNewNameForMethod(((GrAccessorMethod)resolved).getName()));
      processor.run();
    } else {
      myFixture.renameElementAtCaret("newName");
    }
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    myFixture.checkResult(list.get(1));
  }

  private String createNewNameForMethod(final String name) {
    String newName = "newName";
    if (name.startsWith("get")) {
      newName = "get" + StringUtil.capitalize(newName);
    } else if (name.startsWith("is")) {
      newName = "is" + StringUtil.capitalize(newName);
    } else if (name.startsWith("set")) {
      newName = "set" + StringUtil.capitalize(newName);
    }
    return newName;
  }

  public void testDontAutoRenameDynamicallyTypeUsage() throws Exception {
    myFixture.configureByText "a.groovy", """
class Goo {
  def pp<caret>roject() {}
}

new Goo().pproject()

def foo(p) {
  p.pproject()
}
"""
    def method = PsiTreeUtil.findElementOfClassAtOffset(myFixture.file, myFixture.editor.caretModel.offset, GrMethod.class, false)
    def usages = RenameUtil.findUsages(method, "project", false, false, [method:"project"])
    assert !usages[0].isNonCodeUsage
    assert usages[1].isNonCodeUsage
  }

}
