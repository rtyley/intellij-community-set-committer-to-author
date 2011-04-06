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
package com.intellij.openapi.util;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A helper object for {@link RecursionManager}. Is obtained from {@link RecursionManager#createGuard(String)}.
 *
 * @author peter
*/
public interface RecursionGuard {

  /**
   * @param key an id of the computation. Is stored internally to ensure that a recursive calls with the same key won't lead to endless recursion.
   * @param computation a piece of code to compute.
   * @return the result of the computation or null if we're entering a computation with this key on this thread recursively,
   */
  @Nullable
  <T> T doPreventingRecursion(Object key, Computable<T> computation);

  /**
   * Used in pair with {@link com.intellij.openapi.util.RecursionGuard.StackStamp#mayCacheNow()} to ensure that cached are only the reliable values,
   * not depending on anything incomplete due to recursive prevention policies.
   * A typical usage is this:
   * <code>
   *  RecursionGuard.StackStamp stamp = RecursionManager.createGuard("id").markStack();
   *
   *   Result result = doComputation();
   *
   *   if (stamp.mayCacheNow()) {
   *     cache(result);
   *   }
   *   return result;
   * </code>

   * @return an object representing the current stack state, managed by {@link RecursionManager}
   */
  StackStamp markStack();

  /**
   * @return the current thread-local stack of keys passed to {@link #doPreventingRecursion(Object, Computable)}
   */
  List<Object> currentStack();

  /**
   * Makes {@link com.intellij.openapi.util.RecursionGuard.StackStamp#mayCacheNow()} return false for all stamps created since a computation with
   * key <code>since</code> began.
   *
   * Used to prevent caching of results that are non-reliable NOT due to recursion prevention: for example, too deep recursion
   * ({@link #currentStack()} may help in determining the recursion depth)
   *
   * @param since the id of a computation whose result is safe to cache whilst for more nested ones it's not.
   */
  void prohibitResultCaching(Object since);

  interface StackStamp {

    /**
     * @return whether a computation that started at the moment of this {@link StackStamp} instance creation does not depend on any re-entrant recursive
     * results. When such non-reliable results exist in the thread's call stack, returns false, otherwise true
     */
    boolean mayCacheNow();
  }
}
