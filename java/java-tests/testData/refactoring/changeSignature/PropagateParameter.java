public class C {
    void su<caret>bject(String s) {
    }

    void caller() {
        int s;
        subject(null);
    }
}
