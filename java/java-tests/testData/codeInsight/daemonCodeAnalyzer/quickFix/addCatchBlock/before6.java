// "Add Catch Clause(s)" "true"
class Test {
    static class E1 extends Exception { }
    static class E2 extends Exception { }

    static class MyResource implements AutoCloseable {
        public void doSomething() throws E1 { }
        public void close() throws E2 { }
    }

    void m() {
        try (<caret>MyResource r = new MyResource()) {
            r.doSomething();
        } catch (E1 ignore) {
        }
    }
}