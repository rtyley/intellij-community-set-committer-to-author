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
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;

public class ByteBufferMap<K,V> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.ByteBufferMap");

  private final RandomAccessDataInput myBuffer;
  private final int myStartOffset;
  private final KeyProvider<K> myKeyProvider;
  private final ValueProvider<V> myValueProvider;
  private int myMod;
  private final int myEndOffset;

  public static interface KeyProvider<K> {
    int hashCode(K key);

    void write(DataOutput out, K key) throws IOException;

    int length(K key);

    K get(DataInput in) throws IOException;

    /**
     * Should move the buffer pointer to the key end.
     */
    boolean equals(DataInput in, K key) throws IOException;
  }

  public static interface ValueProvider<V> {
    void write(DataOutput out, V value) throws IOException;

    int length(V value);

    V get(DataInput in) throws IOException;
  }

  public static <V> void writeMap(DataOutput stream,
                              ValueProvider<V> valueProvider,
                              WriteableMap<V> map,
                              double searchFactor) throws IOException {
    new ByteBufferMapWriteHandler<V>(stream, valueProvider, map, searchFactor).execute();
  }

  public static <V> int calcMapLength(ValueProvider<V> valueProvider,
                                  WriteableMap<V> map,
                                  double searchFactor) throws IOException {
    return new ByteBufferMapWriteHandler<V>(null, valueProvider, map, searchFactor).calcLength();
  }

  public ByteBufferMap(@NotNull MappedBufferWrapper holder,
                       int startOffset,
                       int endOffset,
                       @NotNull KeyProvider<K> keyProvider,
                       @NotNull ValueProvider<V> valueProvider) {
    this(new ByteBufferRADataInput(holder), startOffset, endOffset, keyProvider, valueProvider);
  }

  public ByteBufferMap(@NotNull RandomAccessDataInput buffer,
                       int startOffset,
                       int endOffset,
                       @NotNull KeyProvider<K> keyProvider,
                       @NotNull ValueProvider<V> valueProvider) {
    assert startOffset < endOffset;

    myBuffer = buffer;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myKeyProvider = keyProvider;
    myValueProvider = valueProvider;

    buffer.setPosition(startOffset);
    try {
      myMod = buffer.readInt();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public V get(K key) {
    int hash = hash(myKeyProvider.hashCode(key));
    int keyGroupOffset = readKeyGroupOffset(hash);
    if (keyGroupOffset == -1) return null;
    if (!(myStartOffset < keyGroupOffset && keyGroupOffset < myEndOffset)){
      LOG.error("keyGroupOffset = " + keyGroupOffset + " myStartOffset = " + myStartOffset + " myEndOffset = " + myEndOffset);
    }

    try {
      myBuffer.setPosition(keyGroupOffset);
      int keyGroupSize = myBuffer.readInt();
      assert (keyGroupSize > 0);
      for (int i = 0; i < keyGroupSize; i++) {
        if (myKeyProvider.equals(myBuffer, key)) {
          int valueOffset = myBuffer.readInt();
          assert (valueOffset > 0);

          myBuffer.setPosition(myStartOffset + valueOffset);
          return myValueProvider.get(myBuffer);
        }
        else {
          myBuffer.readInt(); //read offset;
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }

    return null;
  }

  @SuppressWarnings({"unchecked"})
  public K[] getKeys(Class<K> keyClass) {
    ArrayList<K> result = new ArrayList<K>();
    getKeys(keyClass, result);
    return result.toArray((K[])Array.newInstance(keyClass, result.size()));
  }

  public void getKeys(Class<K> keyClass, Collection<K> dst) {
    try {
      myBuffer.setPosition(myStartOffset + 4 /* mod */);

      int firstKeyGroupOffset = -1;
      int lastKeyGroupOffset = -1;
      for (int i = 0; i < myMod; i++) {
        int value = myBuffer.readInt();
        if (value != -1) {
          int offset = value + myStartOffset;
          if (firstKeyGroupOffset == -1) firstKeyGroupOffset = offset;
          lastKeyGroupOffset = offset;
        }
      }
      if (firstKeyGroupOffset == -1) {
        return;
      }
      assert (firstKeyGroupOffset > myStartOffset);
      assert (lastKeyGroupOffset > myStartOffset);
      assert (lastKeyGroupOffset >= firstKeyGroupOffset);

      int firstValueOffset = -1;

      myBuffer.setPosition(firstKeyGroupOffset);
      while (myBuffer.getPosition() <= lastKeyGroupOffset) {
        int groupSize = myBuffer.readInt();
        for (int i = 0; i < groupSize; i++) {
          dst.add(myKeyProvider.get(myBuffer));

          int valueOffset = myBuffer.readInt(); /* value offset */
          if( firstValueOffset == -1 ) firstValueOffset = valueOffset + myStartOffset;
        }
      }
      assert myBuffer.getPosition() == firstValueOffset;
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private int readKeyGroupOffset(int hash) {
    myBuffer.setPosition(myStartOffset + 4 /* mod */ + 4 * hash);
    int offset = -1;
    try {
      offset = myBuffer.readInt();
    }
    catch (IOException e) {
      LOG.error(e);
    }
    if (offset == -1) return -1;
    return offset + myStartOffset;
  }

  private int hash(int hashCode) {
    return Math.abs(hashCode) % myMod;
  }
}
