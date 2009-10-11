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
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class IntArrayValueProvider implements ByteBufferMap.ValueProvider<int[]> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.IntArrayValueProvider");
  public static final IntArrayValueProvider INSTANCE = new IntArrayValueProvider(-1);

  private final int myArraySize;

  public IntArrayValueProvider(int arraySize) {
    myArraySize = arraySize;
  }

  public void write(DataOutput out, int[] value) throws IOException {
    //if (value instanceof IntArrayList) {
    //  IntArrayList list = (IntArrayList) value;
    //  LOG.assertTrue(myArraySize == -1 || list.size() == myArraySize);
    //  if (myArraySize == -1) out.writeInt(list.size());
    //  for (int i = 0; i < list.size(); i++) {
    //    out.writeInt(list.get(i));
    //  }
    //} else if (value instanceof TIntArrayList) {
    //  TIntArrayList list = (TIntArrayList) value;
    //  LOG.assertTrue(myArraySize == -1 || list.size() == myArraySize);
    //  if (myArraySize == -1) out.writeInt(list.size());
    //  for (int i = 0; i < list.size(); i++) {
    //    out.writeInt(list.get(i));
    //  }
    //} else {
      int[] array = (int[])value;
      LOG.assertTrue(myArraySize == -1 || array.length == myArraySize);
      if (myArraySize == -1) out.writeInt(array.length);
      for(int i = 0; i < array.length; i++){
        out.writeInt(array[i]);
      }
    //}
  }

  public int length(int[] value) {
    //if (value instanceof IntArrayList) {
    //  IntArrayList list = (IntArrayList) value;
    //  LOG.assertTrue(myArraySize == -1 || list.size() == myArraySize);
    //
    //  if (myArraySize == -1) return 4 * (list.size() + 1);
    //
    //  return 4 * myArraySize;
    //} else if (value instanceof TIntArrayList) {
    //  TIntArrayList list = (TIntArrayList) value;
    //  LOG.assertTrue(myArraySize == -1 || list.size() == myArraySize);
    //
    //  if (myArraySize == -1) return 4 * (list.size() + 1);
    //
    //  return 4 * myArraySize;
    //} else {
      int[] array = (int[])value;
      LOG.assertTrue(myArraySize == -1 || array.length == myArraySize);

      if (myArraySize == -1) return 4 * (array.length + 1);

      return 4 * myArraySize;
    //}
  }

  public int[] get(DataInput in) throws IOException {
    final int[] result;

    if (myArraySize >= 0) {
      result = ArrayUtil.newIntArray(myArraySize);
    } else {
      result = ArrayUtil.newIntArray(in.readInt());
    }

    for(int i = 0; i < result.length; i++){
      result[i] = in.readInt();
    }
    return result;
  }
}
