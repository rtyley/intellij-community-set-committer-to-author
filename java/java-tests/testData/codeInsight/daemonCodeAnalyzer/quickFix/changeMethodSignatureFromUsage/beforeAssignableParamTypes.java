// "Add 'B' as 2nd parameter to method 'foo'" "true"
public class S {

  void foo(A a) {
  }

  void bar(B b) {
    A a = getA();
    foo(a, <caret>b);
  }

  A getA() {
    return new A();
  }
}

class A {
}

class B extends A {
}
  