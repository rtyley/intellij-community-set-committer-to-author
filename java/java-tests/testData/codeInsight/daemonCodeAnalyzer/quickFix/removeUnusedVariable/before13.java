// "Remove variable 'o1'" "true"
import java.io.*;

class a {
    int k;
    private int run() {
      Object o0, <caret>o1, /*ddddd*/ oo = (Object) new Integer(0);

      return 0;
    }
}

