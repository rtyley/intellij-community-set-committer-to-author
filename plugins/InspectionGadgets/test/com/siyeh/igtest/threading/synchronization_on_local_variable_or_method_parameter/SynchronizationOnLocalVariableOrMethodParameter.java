package com.siyeh.igtest.threading.synchronization_on_local_variable_or_method_parameter;

class SynchronizationOnLocalVariableOrMethodParameter {

  static {
    final Object lock = new Object();
    new Object(){{
      synchronized (lock) {} // no warning
    }};
  }

  interface IntegerMath {
    int operation(int a, int b);
  }
  public int operateBinary(int a, int b, IntegerMath op) {
    return op.operation(a, b);
  }
  public static void foo() {
    final Object lock = new Object();
    final SynchronizationOnLocalVariableOrMethodParameter x = new SynchronizationOnLocalVariableOrMethodParameter();
    IntegerMath addition = (a, b) -> {
      synchronized(lock) {return a + b;} // no warning
    };
    System.out.println("40 + 2 = " +
                       x.operateBinary(40, 2, addition));
  }

  void bar() {
    final Object lock = new Object();
    synchronized (lock) {

    }
  }
}