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

import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntProcedure;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 05.11.11
 * Time: 0:00
 * To change this template use File | Settings | File Templates.
 */
public class IntIntTransientMaplet extends IntIntMaplet {
  private final TIntIntHashMap myMap = new TIntIntHashMap();
  
  @Override
  public boolean containsKey(final int key) {
    return myMap.containsKey(key);
  }

  @Override
  public int get(final int key) {
    return myMap.get(key);
  }

  @Override
  public void put(final int key, final int value) {
    myMap.put(key, value);
  }

  @Override
  public void putAll(final IntIntMaplet m) {
    m.forEachEntry(new TIntIntProcedure() {
      @Override
      public boolean execute(int key, int value) {
        myMap.put(key, value);
        return true;
      }
    });
  }

  @Override
  public void remove(final int key) {
    myMap.remove(key);
  }

  @Override
  public void close() {
    myMap.clear();
  }

  public void flush(boolean memoryCachesOnly) {
  }

  @Override
  public void forEachEntry(TIntIntProcedure proc) {
    myMap.forEachEntry(proc);
  }
}
