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
package com.intellij.openapi.util;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod", "UnusedDeclaration"})
public class Key<T> {
  private static int ourKeysCounter = 0;
  private final int myIndex = ourKeysCounter++;

  private final String myName; // for debug purposes only

  public Key(@NotNull @NonNls String name) {
    myName = name;
  }

  public int hashCode() {
    return myIndex;
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this;
  }

  public String toString() {
    return myName;
  }

  public static <T> Key<T> create(@NotNull @NonNls String name) {
    return new Key<T>(name);
  }
}
