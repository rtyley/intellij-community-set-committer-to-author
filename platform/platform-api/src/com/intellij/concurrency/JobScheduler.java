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

/*
 * @author max
 */
package com.intellij.concurrency;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NonNls;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public abstract class JobScheduler {
  private static final ScheduledThreadPoolExecutor ourScheduledExecutorService = ConcurrencyUtil.newSingleScheduledThreadExecutor("Periodic tasks thread");

  public static JobScheduler getInstance() {
    return ServiceManager.getService(JobScheduler.class);
  }

  public abstract <T> Job<T> createJob(@NonNls String title, int priority);

  public static ScheduledExecutorService getScheduler() {
    return ourScheduledExecutorService;
  }
}