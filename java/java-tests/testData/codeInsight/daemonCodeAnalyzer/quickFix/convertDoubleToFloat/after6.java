// "Convert '1e-9d' to float" "true"
class Test {
  void bar() {
    foo(1e-9f);
  }
  void foo(float f){}
}
