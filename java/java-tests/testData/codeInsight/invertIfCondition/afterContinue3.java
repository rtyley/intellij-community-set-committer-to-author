// "Invert If Condition" "true"
class B {
    public static void foo() {
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                if (i != 0) {

                    System.out.println("!= 0");
                    continue;
                }
                else {
                    System.out.println("== 0");
                    continue;
                }
            }

            System.out.println("i = " + i);
        }
    }
}