// "Add 'java.io.IOException' to 'a.f' throws list" "true"
import java.io.*;

class a {
 void f() {
 }
}

class b extends a {
 void f() throws <caret>IOException {
 }
}

