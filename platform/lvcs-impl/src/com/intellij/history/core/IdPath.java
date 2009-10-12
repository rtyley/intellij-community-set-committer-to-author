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

package com.intellij.history.core;

import com.intellij.history.core.storage.Stream;

import java.io.IOException;
import java.util.Arrays;

public class IdPath {
  private final int[] myParts;

  public IdPath(int... parts) {
    myParts = parts;
  }

  public IdPath(Stream s) throws IOException {
    myParts = new int[s.readInteger()];
    for (int i = 0; i < myParts.length; i++) {
      myParts[i] = s.readInteger();
    }
  }

  public void write(Stream s) throws IOException {
    s.writeInteger(myParts.length);
    // todo try to change with writeArray method
    for (int id : myParts) {
      s.writeInteger(id);
    }
  }

  public int getId() {
    return myParts[myParts.length - 1];
  }

  public IdPath getParent() {
    if (myParts.length == 1) return null;
    int[] newPath = new int[myParts.length - 1];
    System.arraycopy(myParts, 0, newPath, 0, newPath.length);
    return new IdPath(newPath);
  }

  public IdPath appendedWith(int id) {
    // todo use Arrays.copyOf after going to 1.6 
    int[] newPath = new int[myParts.length + 1];
    System.arraycopy(myParts, 0, newPath, 0, myParts.length);
    newPath[newPath.length - 1] = id;
    return new IdPath(newPath);
  }

  public boolean isChildOrParentOf(IdPath p) {
    return startsWith(p) || p.startsWith(this);
  }

  public boolean contains(int id) {
    for (int part : myParts) {
      if (part == id) return true;
    }
    return false;
  }

  public boolean startsWith(IdPath p) {
    if (myParts.length < p.myParts.length) return false;
    for (int i = 0; i < p.myParts.length; i++) {
      if (myParts[i] != p.myParts[i]) return false;
    }
    return true;
  }

  public boolean rootEquals(int id) {
    return myParts[0] == id;
  }

  public IdPath withoutRoot() {
    int[] newPath = new int[myParts.length - 1];
    System.arraycopy(myParts, 1, newPath, 0, newPath.length);
    return new IdPath(newPath);
  }

  @Override
  public String toString() {
    String result = "";
    for (int part : myParts) {
      result += part + ".";
    }
    return result.substring(0, result.length() - 1);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !o.getClass().equals(getClass())) return false;
    return Arrays.equals(myParts, ((IdPath)o).myParts);
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }
}
