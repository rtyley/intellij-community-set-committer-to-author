// "Access static 'AClass.stat' via class 'AClass' reference" "true"

class AClass {
    static boolean stat;
    void foo (boolean stat) {
        AClass.stat<caret> = stat;
    }
}