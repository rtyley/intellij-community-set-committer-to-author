// "Create Inner Class 'MyInteger'" "true"
public class Test {
    public static void main() {
        int xxx = 3;
        Integer i = new MyInteger(xxx);
    }

    private static class MyInteger {
        public MyInteger(int xxx) {<caret>
        }
    }
}