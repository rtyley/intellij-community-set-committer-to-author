class Test {
  void foo() {
      final String str = newMethod();
      if (str == null) return;
    new Runnable() {
      public void run() {
        System.out.println(str);
      }
    }
  }

    private String newMethod() {
        final String str = "";
        if (str == "") {
            return null;
        }
        return str;
    }
}