// "Create Inner Class 'Foo'" "true"
public class Test {
  void foo(Class<? extends Number> n){}
  void bar() {
    foo(Fo<caret>o.class);
  }

    private class Foo extends Number {
    }
}