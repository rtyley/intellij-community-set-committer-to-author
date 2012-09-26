/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions;

/**
 * @author Max Medvedev
 */
public class GrAliasImportTest extends GrIntentionTestCase {
  public GrAliasImportTest() {
    super(GroovyIntentionsBundle.message("gr.alias.import.intention.name"));
  }

  void testSimple() {
    myFixture.addFileToProject('Foo.groovy', '''\
class Foo {
  static void foo(){}
}
''')
    doTextTest('''\
import static Foo.foo

fo<caret>o()
''', '''\
import static Foo.foo as aliased

aliased()
''');
  }

  void testOverriden() {
    myFixture.addFileToProject('Foo.groovy', '''\
class Foo {
  static void foo(){}
  static void foo(int x){}
}
''')
    doTextTest('''\
import static Foo.foo

fo<caret>o()
foo(2)
''', '''\
import static Foo.foo as aliased

aliased()
aliased(2)
''');
  }

  void testOnDemand() {
    myFixture.addFileToProject('Foo.groovy', '''\
class Foo {
  static void foo(){}
}
''')
    doTextTest('''\
import static Foo.*

fo<caret>o()
''', '''\
import static Foo.*
import static Foo.foo as aliased

aliased()
''');
  }

  void testSimpleOnImportStatement() {
    myFixture.addFileToProject('Foo.groovy', '''\
class Foo {
  static void foo(){}
}
''')
    doTextTest('''\
import static Foo.f<caret>oo

foo()
''', '''\
import static Foo.foo as aliased

aliased()
''');
  }

}