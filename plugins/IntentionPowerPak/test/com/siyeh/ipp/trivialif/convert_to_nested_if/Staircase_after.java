package com.siyeh.ipp.trivialif.convert_to_nested_if;

public class X {
  boolean f(boolean a, boolean b) {
      if (a) if (b) return true;
      return false;
  }
}
