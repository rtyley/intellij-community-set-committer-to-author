class XXX {
  void foo() {
    int k = 0;
    int n = 2;
    Runnable r = ()->{
      <error descr="Variable used in lambda expression should be effectively final">k</error> = n;
    };
  }
}
