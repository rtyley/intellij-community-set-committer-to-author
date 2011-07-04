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
package com.intellij.util.containers;

import java.util.Arrays;
import java.util.List;

public class Queue<T> {
  private Object[] myArray;
  private int myFirst;
  private int myLast;

  // if true, elements are located at myFirst..myArray.length and 0..myLast
  // otherwise, they are at myFirst..myLast
  private boolean isWrapped;

  public Queue(int initialCapacity) {
    myArray = new Object[initialCapacity];
  }

  public void addLast(T object) {
    int currentSize = size();
    if (currentSize == myArray.length) {
      myArray = normalize(currentSize * 2);
      myFirst = 0;
      myLast = currentSize;
      isWrapped = false;
    }
    myArray[myLast] = object;
    myLast++;
    if (myLast == myArray.length) {
      isWrapped = !isWrapped;
      myLast = 0;
    }
  }

  public boolean isEmpty() {
    return size() == 0;
  }

  public int size() {
    return isWrapped ? myArray.length - myFirst + myLast : myLast - myFirst;
  }

  public List<T> toList() {
    return Arrays.asList(normalize(size()));
  }

  public Object[] toArray() {
    return normalize(size());
  }

  public T pullFirst() {
    T result = (T)myArray[myFirst];
    myArray[myFirst] = null;
    myFirst++;
    if (myFirst == myArray.length) {
      myFirst = 0;
      isWrapped = !isWrapped;
    }
    return result;
  }

  public T peekFirst() {
    return (T)myArray[myFirst];
  }

  private int copyFromTo(int first, int last, T[] result, int destPos) {
    int length = last - first;
    System.arraycopy(myArray, first, result, destPos, length);
    return length;
  }

  private T[] normalize(int capacity) {
    T[] result = (T[])new Object[capacity];
    if (isWrapped) {
      int tailLength = copyFromTo(myFirst, myArray.length, result, 0);
      copyFromTo(0, myLast, result, tailLength);
    }
    else {
      copyFromTo(myFirst, myLast, result, 0);
    }
    return result;
  }

  public void clear() {
    for (int i = 0; i < myArray.length; i++) {
      myArray[i] = null;
    }
    isWrapped = false;
    myFirst = myLast = 0;
  }
}
