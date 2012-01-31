/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.ether.dependencyView;

import java.util.Collection;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 04.11.11
 * Time: 23:48
 * To change this template use File | Settings | File Templates.
 */
interface Maplet<K, V> {
  boolean containsKey(final Object key);
  V get(final Object key);
  void put(final K key, final V value);
  void putAll(Maplet<K, V> m);
  void remove(final Object key);
  void close();
  Collection<K> keyCollection();
  Collection<Map.Entry<K, V>> entrySet();

  void flush(boolean memoryCachesOnly);
}
