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
package com.intellij.util.containers.hash;


import org.jetbrains.annotations.NotNull;

import java.util.*;


public class LinkedHashMap<K, V> extends AbstractMap<K, V> implements Map<K, V> {
  private Entry<K, V>[] table;
  private Entry<K, V> top;
  private Entry<K, V> back;
  private int capacity;
  private int size;
  private final float loadFactor;
  private final EqualityPolicy<K> hashingStrategy;


  public LinkedHashMap() {
    this(0);
  }

  public LinkedHashMap(int capacity) {
    this(capacity, HashUtil.DEFAULT_LOAD_FACTOR);
  }

  public LinkedHashMap(int capacity, float loadFactor) {
    this(capacity, loadFactor, (EqualityPolicy)EqualityPolicy.CANONICAL);
  }

  public LinkedHashMap(EqualityPolicy hashingStrategy) {
    this(0, HashUtil.DEFAULT_LOAD_FACTOR, hashingStrategy);
  }

  public LinkedHashMap(int capacity, float loadFactor, EqualityPolicy<K> hashingStrategy) {
    this.loadFactor = loadFactor;
    this.hashingStrategy = hashingStrategy;
    clear(capacity);
  }

  public int size() {
    return size;
  }

  public boolean isEmpty() {
    return size() == 0;
  }

  public void clear() {
    clear(0);
  }

  public V get(final Object key) {
    final Entry<K, V>[] table = this.table;
    final int hash = HashUtil.hash(key, hashingStrategy);
    final int index = hash % table.length;

    for (Entry<K, V> e = table[index]; e != null; e = e.hashNext) {
      final K entryKey;
      if (e.keyHash == hash && ((entryKey = e.key) == key || hashingStrategy.isEqual(entryKey, (K)key))) {
        moveToTop(e);
        return e.value;

      }
    }
    return null;
  }

  public V put(final K key, @NotNull final V value) {
    final Entry<K, V>[] table = this.table;
    final int hash = HashUtil.hash(key, hashingStrategy);
    final int index = hash % table.length;
    for (Entry<K, V> e = table[index]; e != null; e = e.hashNext) {
      final K entryKey;
      if (e.keyHash == hash && ((entryKey = e.key) == key || hashingStrategy.isEqual(entryKey, key))) {
        moveToTop(e);
        return e.setValue(value);
      }
    }
    final Entry<K, V> e = new Entry<K, V>(key, value, hash);
    e.hashNext = table[index];
    table[index] = e;
    final Entry<K, V> top = this.top;
    e.next = top;
    if (top != null) {
      top.previous = e;
    }
    else {
      back = e;
    }
    this.top = e;
    size++;
    if (removeEldestEntry(back, back.key, back.value)) {
      doRemoveEldestEntry();
    }
    else if (size > capacity) {
      rehash((int)(capacity * HashUtil.CAPACITY_MULTIPLE));
    }
    return null;
  }

  public void doRemoveEldestEntry() {
    final V val = remove(back.key);
    assert val != null : "LinkedHashMap.Entry was not removed. Possibly mutable key: " + back.key;
  }

  public boolean containsKey(final Object key) {
    return get(key) != null;
  }

  public V remove(final Object key) {
    final Entry<K, V>[] table = this.table;
    final int hash = HashUtil.hash(key, hashingStrategy);
    final int index = hash % table.length;
    Entry<K, V> e = table[index];
    if (e == null) {
      return null;
    }
    K entryKey;
    if (e.keyHash == hash && ((entryKey = e.key) == key || hashingStrategy.isEqual(entryKey, (K)key))) {
      table[index] = e.hashNext;
    }
    else {
      for (; ;) {
        final Entry<K, V> last = e;
        e = e.hashNext;
        if (e == null) {
          return null;
        }
        if (e.keyHash == hash && ((entryKey = e.key) == key || hashingStrategy.isEqual(entryKey, (K)key))) {
          last.hashNext = e.hashNext;
          break;
        }
      }
    }
    unlink(e);
    size--;
    return e.value;
  }

  public Set<K> keySet() {
    return new KeySet();
  }

  public Collection<V> values() {
    return new Values();
  }

  public Set<Map.Entry<K, V>> entrySet() {
    return new EntrySet();
  }

  protected boolean removeEldestEntry(Map.Entry<K, V> eldest, K key, V value) {
    return removeEldestEntry(eldest);
  }

  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    return false;
  }

  private void init(int capacity) {
    table = new Entry[HashUtil.adjustTableSize((int)(capacity / loadFactor))];
    top = back = null;
    this.capacity = capacity;
  }

  private void clear(int capacity) {
    if (capacity < HashUtil.MIN_CAPACITY) {
      capacity = HashUtil.MIN_CAPACITY;
    }
    init(capacity);
    size = 0;
  }

  private void moveToTop(final Entry<K, V> e) {
    final Entry<K, V> top = this.top;
    if (top != e) {
      final Entry<K, V> prev = e.previous;
      final Entry<K, V> next = e.next;
      prev.next = next;
      if (next != null) {
        next.previous = prev;
      }
      else {
        back = prev;
      }
      top.previous = e;
      e.next = top;
      e.previous = null;
      this.top = e;
    }
  }

  private void unlink(final Entry<K, V> e) {
    final Entry<K, V> prev = e.previous;
    final Entry<K, V> next = e.next;
    if (prev != null) {
      prev.next = next;
    }
    else {
      top = next;
    }
    if (next != null) {
      next.previous = prev;
    }
    else {
      back = prev;
    }

    // Help GC
    e.previous = null;
    e.next = null;
  }

  private void rehash(int capacity) {
    table = new Entry[HashUtil.adjustTableSize((int)(capacity / loadFactor))];
    this.capacity = capacity;
    final Entry<K, V>[] table = this.table;
    final int tableLen = table.length;
    for (Entry<K, V> e = back; e != null; e = e.previous) {
      final int hash = e.keyHash % tableLen;
      e.hashNext = table[hash];
      table[hash] = e;
    }
  }

  private static class Entry<K, V> implements Map.Entry<K, V> {

    private final K key;
    private final int keyHash;
    private V value;
    private Entry<K, V> next;
    private Entry<K, V> previous;
    private Entry<K, V> hashNext;

    public Entry(final K key, final V value, int hash) {
      this.key = key;
      keyHash = hash;
      this.value = value;
    }

    public K getKey() {
      return key;
    }

    public V getValue() {
      return value;
    }

    public V setValue(final V value) {
      final V result = this.value;
      this.value = value;
      return result;
    }
  }

  private abstract class LinkedHashIterator<T> implements Iterator<T> {

    private LinkedHashMap.Entry<K, V> e = top;
    private LinkedHashMap.Entry<K, V> last;

    public boolean hasNext() {
      return e != null;
    }

    public void remove() {
      if (last == null) {
        throw new IllegalStateException();
      }
      LinkedHashMap.this.remove(last.key);
      last = null;
    }

    protected Entry<K, V> nextEntry() {
      final Entry<K, V> result = last = e;
      e = result.next;
      return result;
    }
  }

  private final class EntrySet extends AbstractSet<Map.Entry<K, V>> {

    public Iterator<Map.Entry<K, V>> iterator() {
      return new LinkedHashIterator<Map.Entry<K, V>>() {
        public Map.Entry<K, V> next() {
          return nextEntry();
        }
      };
    }

    public boolean contains(Object o) {
      if (!(o instanceof Map.Entry)) {
        return false;
      }
      final Map.Entry<K, V> e = (Map.Entry<K, V>)o;
      final V value = get(e.getKey());
      return value != null && value.equals(e.getValue());
    }

    public boolean remove(Object o) {
      if (!(o instanceof Map.Entry)) {
        return false;
      }
      final Map.Entry<K, V> e = (Map.Entry<K, V>)o;
      return LinkedHashMap.this.remove(e.getKey()) != null;
    }

    public int size() {
      return size;
    }

    public void clear() {
      LinkedHashMap.this.clear();
    }
  }

  private final class KeySet extends AbstractSet<K> {

    public Iterator<K> iterator() {
      return new LinkedHashIterator<K>() {
        public K next() {
          return nextEntry().key;
        }
      };
    }

    public int size() {
      return size;
    }

    public boolean contains(Object o) {
      return LinkedHashMap.this.containsKey(o);
    }

    public boolean remove(Object o) {
      return LinkedHashMap.this.remove(o) != null;
    }

    public void clear() {
      LinkedHashMap.this.clear();
    }
  }

  private final class Values extends AbstractCollection<V> {

    public Iterator<V> iterator() {
      return new LinkedHashIterator<V>() {
        public V next() {
          return nextEntry().value;
        }
      };
    }

    public int size() {
      return size;
    }

    public boolean contains(Object o) {
      return containsValue(o);
    }

    public void clear() {
      LinkedHashMap.this.clear();
    }
  }
}
