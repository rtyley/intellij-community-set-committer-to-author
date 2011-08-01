/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util.treeView;

import java.util.Comparator;

public class AlphaComparator implements Comparator<NodeDescriptor>{
  public static final AlphaComparator INSTANCE = new AlphaComparator();

  protected AlphaComparator() {
  }

  public int compare(NodeDescriptor nodeDescriptor1, NodeDescriptor nodeDescriptor2) {
    int weight1 = nodeDescriptor1.getWeight();
    int weight2 = nodeDescriptor2.getWeight();
    if (weight1 != weight2) {
      return weight1 - weight2;
    }
    String s1 = nodeDescriptor1.toString();
    String s2 = nodeDescriptor2.toString();
    if (s1 == null) return s2 == null ? 0 : -1;
    if (s2 == null) return +1;

    if (startsWithDigit(s1) && startsWithDigit(s2)) {
      int p1 = calculatePrefix(s1);
      int p2 = calculatePrefix(s2);
      if (p1 != p2) return p1 - p2;
    }

    return s1.compareToIgnoreCase(s2);
  }

  private static int calculatePrefix(String s) {
    int acc = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < '0' || c > '9') break;
      acc = acc * 10 + c - '0';
    }
    return acc;
  }

  private static boolean startsWithDigit(String s) {
    if (s.isEmpty()) return false;
    char c = s.charAt(0);
    return c >= '0' && c <= '9';
  }
}
