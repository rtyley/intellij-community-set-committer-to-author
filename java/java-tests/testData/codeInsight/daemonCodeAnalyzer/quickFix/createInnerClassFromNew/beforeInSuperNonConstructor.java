// "Create Inner Class 'Generic'" "true"
class Base {
  void foo(){}
}
class Test extends Base {
  Test() {
    super.foo(new <caret>Generic<String> ());
  }
}