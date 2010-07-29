package org.jetbrains.plugins.groovy.lang

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.psi.search.searches.ReferencesSearch

/**
 * @author peter
 */
class LiteralConstructorUsagesTest extends LightCodeInsightFixtureTestCase {
  @Override protected void setUp() {
    super.setUp();
    myFixture.addClass("package groovy.lang; public @interface Typed {}");
  }

  public void testList_Variable() throws Exception {
    def foo = myFixture.addClass("""class Foo {
    Foo() {}
    }
""")
    myFixture.addFileToProject "a.groovy", "Foo x = []"
    assertOneElement(ReferencesSearch.search(foo.constructors[0]).findAll())
  }
  
  public void testList_ReturnValue() throws Exception {
    def foo = myFixture.addClass("""class Foo {
    Foo() {}
    }
""")
    myFixture.addFileToProject "a.groovy", "Foo foo() { if (true) [] else return [] }"
    assertEquals(2, ReferencesSearch.search(foo.constructors[0]).findAll().size())
  }

  public void testList_Cast() throws Exception {
    def foo = myFixture.addClass("""class Foo {
    Foo() {}
    }
""")
    myFixture.addFileToProject "a.groovy", "def x = (Foo) []"
    assertOneElement(ReferencesSearch.search(foo.constructors[0]).findAll())
  }

  public void testList_AsCast() throws Exception {
    def foo = myFixture.addClass("""class Foo {
    Foo() {}
    }
}""")
    myFixture.addFileToProject "a.groovy", "def x = [] as Foo"
    assertOneElement(ReferencesSearch.search(foo.constructors[0]).findAll())
  }

  public void testMap_AsCast() throws Exception {
    def foo = myFixture.addClass("""class Foo {
    Foo() {}
    }
}""")
    myFixture.addFileToProject "a.groovy", "def x = [:] as Foo"
    assertOneElement(ReferencesSearch.search(foo.constructors[0]).findAll())
  }

  public void testMapSuper_AsCast() throws Exception {
    def foo = myFixture.addClass("""class Foo {
    Foo(int a) {}
    }
}""")
    myFixture.addFileToProject "a.gpp", "def x = ['super':[2]] as Foo"
    myFixture.addFileToProject "c.gpp", "def x = [super:2] as Foo"

    assertEquals(2, ReferencesSearch.search(foo.constructors[0]).findAll().size())
  }

  public void testList_GppMethodCall() throws Exception {
    //------------------------declarations
    def foo = myFixture.addClass("""
    package z;
    class Foo {
        public Foo() {}
    }
    """)

    myFixture.addClass("""
    package z;
    public class Bar {
      public static void giveMeFoo(int a, Foo f) {}
    }
""")
    myFixture.addFileToProject("Decl.groovy", "static def giveMeFooAsWell(z.Foo f) {}")

    //----------------------usages
    myFixture.addFileToProject "a.gpp", "z.Bar.giveMeFoo(2, []) //usage"
    myFixture.addFileToProject "b.groovy", """
      @Typed package aa;
      z.Bar.giveMeFoo(3, []) //usage
      """
    myFixture.addFileToProject "c.groovy", """
      @Typed def someMethod() {
        z.Bar.giveMeFoo 4, [] //usage
        Decl.giveMeFooAsWell([]) //usage
      }
      z.Bar.giveMeFoo 5, [] //non-typed context
      Decl.giveMeFooAsWell([])
      """
    myFixture.addFileToProject "invalid.gpp", "z.Bar.giveMeFoo 42, 239, []"
    myFixture.addFileToProject "nonGpp.groovy", "z.Bar.giveMeFoo(6, [])"
    assertEquals(4, ReferencesSearch.search(foo.constructors[0]).findAll().size())
  }

  public void testList_GppConstructorCallWithSeveralParameters() throws Exception {
    def foo = myFixture.addClass("""
      class Foo {
          Foo() {}
      }
      """)

    myFixture.addClass("""
    class Bar {
      Bar(Foo f1, Foo f2, Foo f3) {}
    }
    """)
    myFixture.addFileToProject "a.gpp", "new Bar([],[],[])"
    assertEquals(3, ReferencesSearch.search(foo.constructors[0]).findAll().size())
  }

  public void testMap_GppOverloads() throws Exception {
    def foo = myFixture.addClass("""
      class Foo {
          Foo() {}
          Foo(int a) {}
      }
      """)

    myFixture.addClass("""
    class Bar {
      static void foo(Foo f1, Foo f2) {}
    }
    """)
    myFixture.addFileToProject "a.gpp", "Bar.foo([:], [super:2])"
    assertEquals(1, ReferencesSearch.search(foo.constructors[0]).findAll().size())
    assertEquals(1, ReferencesSearch.search(foo.constructors[1]).findAll().size())
  }

}
