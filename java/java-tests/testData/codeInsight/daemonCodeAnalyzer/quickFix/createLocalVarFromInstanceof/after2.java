// "Insert '(Runnable)this' declaration" "true"
class C {
  void f() {
      if (!(this instanceof Runnable)) {
          return;
      }
      Runnable runnable = (Runnable) this;
      <caret>
  }
}

