class List<T> {}

class C {
  void foo () {
    List<? extends String>[] array = new List<? extends String>[<caret>];
  }
}