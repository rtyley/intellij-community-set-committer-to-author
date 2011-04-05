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
package com.intellij.openapi.application.impl;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.util.Condition;
import com.intellij.util.Processor;
import com.intellij.util.containers.TransferToEDTQueue;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to process elements in the Application pooled thread.
 * Processes elements in batches, no longer than 200ms per batch, and reschedules processing later for longer batches.
 * Usage: {@link #offer(Object)} } : schedules element for processing in a pooled thread
 */
public class TransferToPooledThreadQueue<T> extends TransferToEDTQueue<T> {
  public TransferToPooledThreadQueue(@NotNull String name,
                                     @NotNull Processor<T> processorInEDT,
                                     @NotNull Condition<?> shutUpCondition,
                                     int maxUnitOfWorkThresholdMs) {
    super(name, processorInEDT, shutUpCondition, maxUnitOfWorkThresholdMs);
  }

  @Override
  protected void schedule(@NotNull Runnable updateRunnable) {
    JobScheduler.getScheduler().execute(updateRunnable);
  }
}
