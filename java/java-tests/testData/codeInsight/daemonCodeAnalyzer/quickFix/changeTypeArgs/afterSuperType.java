import java.io.Serializable;

// "Change type arguments to <Serializable>" "true"
class Generic<E> {
  Generic(E arg, E arg1) {
  }
}

class Tester {
  void method() {
    new Generic<Serializable>("hi", 1);
  }
}