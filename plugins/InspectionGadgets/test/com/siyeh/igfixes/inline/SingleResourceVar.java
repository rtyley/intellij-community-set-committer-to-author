class C {
    void m() throws Exception {
        AutoCloseable r1 = null;
        try (AutoCloseable <caret>r2 = r1) {
            System.out.println(r1 + ", " + r2);
        }
    }
}