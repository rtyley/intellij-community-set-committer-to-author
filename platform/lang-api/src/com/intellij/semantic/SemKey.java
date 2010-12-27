/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.semantic;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Type-safe key for access to {@link SemElement}.
 * <p/>
 * Use {@link #createKey(String, SemKey[])} to create a new "root" key, on which you can attach "sub"-keys via
 * {@link #subKey(String, SemKey[])}.
 *
 * @author peter
 */
public class SemKey<T extends SemElement> {
  private static final AtomicInteger counter = new AtomicInteger(0);
  private final String myDebugName;
  private final SemKey<? super T>[] mySupers;
  private final int myUniqueId;

  private SemKey(String debugName, SemKey<? super T>... supers) {
    myDebugName = debugName;
    mySupers = supers;
    myUniqueId = counter.getAndIncrement();
  }

  public SemKey<? super T>[] getSupers() {
    return mySupers;
  }

  public boolean isKindOf(SemKey<?> another) {
    if (another == this) return true;
    for (final SemKey<? super T> superKey : mySupers) {
      if (superKey.isKindOf(another)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return myDebugName;
  }

  public static <T extends SemElement> SemKey<T> createKey(String debugName, SemKey<? super T>... supers) {
    return new SemKey<T>(debugName, supers);
  }

  public int getUniqueId() {
    return myUniqueId;
  }

  public <K extends T> SemKey<K> subKey(@NonNls String debugName, SemKey<? super T>... otherSupers) {
    if (otherSupers.length == 0) {
      return new SemKey<K>(debugName, this);
    }
    return new SemKey<K>(debugName, ArrayUtil.append(otherSupers, this));
  }
}
