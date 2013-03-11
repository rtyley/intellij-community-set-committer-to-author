/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util

import com.intellij.testFramework.UsefulTestCase
/**
 * @author peter
 */
class IntSLRUCacheTest extends UsefulTestCase {

  static IntSLRUCache<IntSLRUCache.CacheEntry<String>> createCache(int prot = 1, int prob = 1) {
    return new IntSLRUCache<IntSLRUCache.CacheEntry<String>>(prot, prob)
  }

  static IntSLRUCache.CacheEntry<String> entry(String s) {
    return new IntSLRUCache.CacheEntry<String>(s)
  }

  public void "test cached after add"() {
    def cache = createCache()
    cache.cacheEntry(0, entry("a"))
    assert cache.getCachedEntry(0)
    assert !cache.getCachedEntry(1)
  }

  public void "test evict infrequent"() {
    def cache = createCache(2, 2)
    cache.cacheEntry(0, entry("a"))
    cache.cacheEntry(1, entry("b"))
    cache.cacheEntry(2, entry("c"))
    assert !cache.getCachedEntry(0)
    assert cache.getCachedEntry(1)
    assert cache.getCachedEntry(2)
  }

  public void "test frequently accessed should survive"() {
    def cache = createCache(2, 2)
    cache.cacheEntry(0, entry("a"))
    cache.cacheEntry(1, entry("b"))
    assert cache.getCachedEntry(0)

    cache.cacheEntry(2, entry("c"))
    cache.cacheEntry(3, entry("d"))

    assert cache.getCachedEntry(0)
    assert !cache.getCachedEntry(1)
    assert cache.getCachedEntry(2)
  }

  public void "test drop frequently accessed in the past"() {
    def cache = createCache()
    cache.cacheEntry(0, entry("a"))
    assert cache.getCachedEntry(0) // protected now

    cache.cacheEntry(1, entry("b"))
    cache.cacheEntry(2, entry("c"))

    assert cache.getCachedEntry(0) // still protected
    assert !cache.getCachedEntry(1)
    assert cache.getCachedEntry(2) // protected now instead of 0

    cache.cacheEntry(1, entry("b"))

    assert !cache.getCachedEntry(0)
    assert cache.getCachedEntry(2)
    assert cache.getCachedEntry(1)
  }

  public void "test changing working set"() {
    def cache = createCache()
    cache.cacheEntry(0, entry("a"))
    assert cache.getCachedEntry(0)

    cache.cacheEntry(1, entry("b"))
    assert cache.getCachedEntry(1)
    assert cache.getCachedEntry(0)
    assert cache.getCachedEntry(1)
    assert cache.getCachedEntry(0)

    cache.cacheEntry(2, entry("c"))
    assert cache.getCachedEntry(2)
    assert cache.getCachedEntry(0)
    assert !cache.getCachedEntry(1)
  }

}
