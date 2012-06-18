package com.siyeh.ipp.exceptions.mergeTry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class Simple {
  void foo(File file1, File file2) throws IOException {
    <caret>try (FileInputStream in = new FileInputStream(file1)) {
      try (FileOutputStream out = new FileOutputStream(file2)) {
        // do something
      }
    }
  }
}