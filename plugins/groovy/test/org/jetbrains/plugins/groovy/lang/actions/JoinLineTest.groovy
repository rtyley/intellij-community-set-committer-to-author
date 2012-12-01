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
package org.jetbrains.plugins.groovy.lang.actions
import com.intellij.openapi.actionSystem.IdeActions
import org.jetbrains.plugins.groovy.GroovyFileType
/**
 * @author Max Medvedev
 */
class JoinLineTest extends GroovyEditorActionTestBase {
  final String basePath = null

  void testVariable() {
    doTest('''\
d<caret>ef a;
a = 4
''', '''\
def a = 4;
''')
  }

  void testVar2()  {
    doTest('''\
d<caret>ef a, b;
a = 4
''', '''\
def a = 4, b;
''')
  }

  void testVar3() {
    doTest('''\
d<caret>ef a
a = 4
''', '''\
def a = 4
''')
  }

  void testVar4()  {
    doTest('''\
d<caret>ef a, b
a = 4
''', '''\
def a = 4, b
''')
  }

  void testIf() {
    doTest('''\
if (cond)<caret> {
  doSmth()
}
''', '''\
if (cond) doSmth()
''')
  }

  void testElse() {
    doTest('''\
if (cond) {
  doSmth()
}
els<caret>e {
  doSmthElse()
}
''', '''\
if (cond) {
  doSmth()
}
else doSmthElse()
''')
  }


  private void doTest(String before, String after) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, before);
    performAction(IdeActions.ACTION_EDITOR_JOIN_LINES);
    myFixture.checkResult(after);

  }
}
