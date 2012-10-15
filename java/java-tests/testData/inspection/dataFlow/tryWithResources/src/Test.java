class Test {
  static class MyResource implements AutoCloseable {
    @Override public void close() { }
  }

  interface MyResourceProvider {
    MyResource getResource();
  }

  void m1() throws Exception {
    MyResourceProvider provider = null;
    try (MyResource r = provider.getResource()) {
      System.out.println(r);
    }
  }

  void m2() {
    try (MyResource r = null) {
      System.out.println(r);
    }
  }
}