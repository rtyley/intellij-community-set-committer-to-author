// "Make 'a.i' package local" "true"
import java.io.*;

class a {
  private int i;
}
class b extends a {
  void f() {
    int p = <caret>i;
  }
}
