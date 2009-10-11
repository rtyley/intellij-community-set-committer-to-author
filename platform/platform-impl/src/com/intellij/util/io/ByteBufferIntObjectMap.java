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
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class ByteBufferIntObjectMap<V> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.ByteBufferMap");

  private final RandomAccessDataInput myBuffer;
  private final int myStartOffset;
  private final ByteBufferMap.ValueProvider<V> myValueProvider;
  private int myMod;
  private final int myEndOffset;

  public ByteBufferIntObjectMap(@NotNull MappedBufferWrapper buffer,
                       int startOffset,
                       int endOffset,
                       @NotNull ByteBufferMap.ValueProvider<V> valueProvider) {
    assert startOffset < endOffset;

    myBuffer = new ByteBufferRADataInput(buffer);
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myValueProvider = valueProvider;

    myBuffer.setPosition(startOffset);
    try {
      myMod = myBuffer.readInt();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public V get(int key) {
    int hash = hash(key);
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
        if (key == myBuffer.readInt()) {
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

  public TIntArrayList getKeys() {
    TIntArrayList result = new TIntArrayList();
    getKeys(result);
    return result;
  }

  public void getKeys(TIntArrayList dst) {
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
          dst.add(myBuffer.readInt());

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
