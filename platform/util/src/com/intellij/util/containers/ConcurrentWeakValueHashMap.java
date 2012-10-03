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

import com.intellij.openapi.util.Comparing;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;

public final class ConcurrentWeakValueHashMap<K,V> extends ConcurrentRefValueHashMap<K,V> {
  public ConcurrentWeakValueHashMap(Map<K, V> map) {
    super(map);
  }

  public ConcurrentWeakValueHashMap() {
    super();
  }

  public ConcurrentWeakValueHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
    super(initialCapacity, loadFactor, concurrencyLevel);
  }

  private static class MyWeakReference<K,T> extends WeakReference<T> implements MyReference<K,T> {
    private final K key;
    private MyWeakReference(K key, T referent, ReferenceQueue<T> q) {
      super(referent, q);
      this.key = key;
    }

    @Override
    public K getKey() {
      return key;
    }

    // MUST work with gced references too for the code in processQueue to work
    public final boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final MyReference that = (MyReference)o;

      return key.equals(that.getKey()) && Comparing.equal(get(), that.get());
    }

    public final int hashCode() {
      return key.hashCode();
    }
  }

  @Override
  protected MyReference<K, V> createRef(K key, V value) {
    return new MyWeakReference<K,V>(key, value, myQueue);
  }
}
