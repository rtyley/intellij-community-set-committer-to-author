class Foo {
  def Foo(int p) {}
}

class Bar extends Foo {
  def Bar() {
    super(5)
  }

  def get() {return "a"}
}