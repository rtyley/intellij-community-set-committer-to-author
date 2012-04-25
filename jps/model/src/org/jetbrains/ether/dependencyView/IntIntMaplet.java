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

import gnu.trove.TIntIntProcedure;

import java.io.PrintStream;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 04.11.11
 * Time: 23:48
 * To change this template use File | Settings | File Templates.
 */
abstract class IntIntMaplet implements Streamable {
  abstract boolean containsKey(final int key);
  abstract int get(final int key);
  abstract void put(final int key, final int value);
  abstract void putAll(IntIntMaplet m);
  abstract void remove(final int key);
  abstract void close();
  abstract void forEachEntry(TIntIntProcedure proc);
  abstract void flush(boolean memoryCachesOnly);

  public void toStream(final DependencyContext context, final PrintStream stream) {
    forEachEntry(new TIntIntProcedure() {
      @Override
      public boolean execute(final int a, final int b) {
        stream.print("  ");
        stream.print(context.getValue(a));
        stream.print(" -> ");
        stream.println(context.getValue(b));
        return true;
      }
    });
  }
}
