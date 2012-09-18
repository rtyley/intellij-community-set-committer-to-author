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
package com.intellij.psi.codeStyle.arrangement

import org.junit.Before

import static com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType.*
import static com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier.PUBLIC
/**
 * @author Denis Zhdanov
 * @since 7/20/12 2:45 PM
 */
class JavaRearrangerByTypeTest extends AbstractJavaRearrangerTest {

  @Before
  void setUp() {
    super.setUp()
    commonSettings.BLANK_LINES_AROUND_METHOD = 0
    commonSettings.BLANK_LINES_AROUND_CLASS = 0
  }

  void testFieldsBeforeMethods() {
    doTest(
      initial: '''\
class Test {
  public void test() {}
   private int i;
}
class Test2 {
public void test() {
}
    private int i;
  private int j;
}''',
      expected: '''\
class Test {
   private int i;
  public void test() {}
}
class Test2 {
    private int i;
  private int j;
public void test() {
}
}''',
      rules: [rule(FIELD)]
    )
  }

  void testAnonymousClassAtFieldInitializer() {
    doTest(
      initial: '''\
class Test {
  private Object first = new Object() {
    int inner1;
    public String toString() { return "test"; }
    int inner2;
  };
  public Object test(Object ... args) {
    return null;
  }
  private Object second = test(test(new Object() {
    public String toString() {
      return "test";
    }
    private Object inner = new Object() {
      public String toString() { return "innerTest"; }
    };
  }));
}''',
      expected: '''\
class Test {
  private Object first = new Object() {
    int inner1;
    int inner2;
    public String toString() { return "test"; }
  };
  private Object second = test(test(new Object() {
    private Object inner = new Object() {
      public String toString() { return "innerTest"; }
    };
    public String toString() {
      return "test";
    }
  }));
  public Object test(Object ... args) {
    return null;
  }
}''',
      rules: [rule(FIELD)]
    )
  }

  void testAnonymousClassAtMethod() {
    doTest(
      initial: '''\
class Test {
   void declaration() {
     Object o = new Object() {
       private int test() { return 1; }
       String s;
     }
   }
   double d;
   void call() {
     test(test(1, new Object() {
       public void test() {}
       int i;
     });
   }
}''',
      expected: '''\
class Test {
   double d;
   void declaration() {
     Object o = new Object() {
       String s;
       private int test() { return 1; }
     }
   }
   void call() {
     test(test(1, new Object() {
       int i;
       public void test() {}
     });
   }
}''',
      rules: [rule(FIELD)]
    )
  }

  void testInnerClassInterfaceAndEnum() {
    doTest(
      initial: '''\
class Test {
   enum E { ONE, TWO }
   class Inner {}
   interface Intf {}
}''',
      expected: '''\
class Test {
   interface Intf {}
   enum E { ONE, TWO }
   class Inner {}
}''',
      rules: [rule(INTERFACE),
              rule(ENUM),
              rule(CLASS)]
    )
  }

  void testRanges() {
    doTest(
      initial: '''\
class Test {
  void outer1() {}
<range>  String outer2() {}
  int i;</range>
  void test() {
    method(new Object() {
      void inner1() {}
      Object field = new Object() {
<range>        void inner2() {}
        String s;</range>
        Integer i;
      }
    });
  }
}''',
      expected: '''\
class Test {
  void outer1() {}
  int i;
  String outer2() {}
  void test() {
    method(new Object() {
      void inner1() {}
      Object field = new Object() {
        String s;
        void inner2() {}
        Integer i;
      }
    });
  }
}''',
      rules: [rule(FIELD)]
    )
  }

  void testMethodsAndConstructors() {
    doTest(
      initial: '''\
class Test {
  abstract void method1();
  Test() {}
  abstract void method2();
}''',
      expected: '''\
class Test {
  Test() {}
  abstract void method1();
  abstract void method2();
}''',
      rules: [rule(CONSTRUCTOR), rule(METHOD)])
  }

  void testConstructorAsMethod() {
    doTest(
      initial: '''\
class Test {
  private int i;
  Test() {}
  public int j;
}''',
      expected: '''\
class Test {
  public int j;
  Test() {}
  private int i;
}''',
      rules: [rule(FIELD, PUBLIC), rule(METHOD), rule(FIELD)])
  }
}
