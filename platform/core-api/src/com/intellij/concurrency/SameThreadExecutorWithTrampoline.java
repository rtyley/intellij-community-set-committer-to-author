/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.concurrency;

import com.intellij.util.containers.Queue;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

/**
 * Author: dmitrylomov
 */
public class SameThreadExecutorWithTrampoline implements Executor {

  private ThreadLocal<Queue<Runnable>> myExecutionTrampoline = new ThreadLocal<Queue<Runnable>>();

  public SameThreadExecutorWithTrampoline() {}


  @Override
  public void execute(@NotNull Runnable command) {
    if (myExecutionTrampoline.get() != null) {
      myExecutionTrampoline.get().addLast(command);
      return;
    }
    try {
      final Queue<Runnable> queue = new Queue<Runnable>(2);
      myExecutionTrampoline.set(queue);
      queue.addLast(command);
      while(!queue.isEmpty()) {
        final Runnable runnable = queue.pullFirst();
        runnable.run();
      }
    } finally {
      myExecutionTrampoline.set(null);
    }
  }
}
