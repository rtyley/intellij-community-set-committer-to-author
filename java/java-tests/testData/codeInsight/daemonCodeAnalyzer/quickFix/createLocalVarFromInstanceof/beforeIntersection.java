// "Insert '(String)o' declaration" "true"
class C {
    void f(Object o, Object f) {
        if (o instanceof String && f == null) {
          <caret>
        }
    }
}