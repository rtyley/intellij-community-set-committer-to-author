package com.siyeh.ipp.parentheses;
class Inversion {
  public void context(boolean a, boolean b, boolean c) {
    if ((!a || <caret>!b) && !c) {
      System.out.println(1);
    }
    else {
      System.out.println(0);
    }
  }
}