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
package com.intellij.openapi.util;

import java.util.*;

/**
 * Simple timer that keeps order of scheduled tasks
 */
public class SimpleTimer {
  private final Timer ourTimer = new Timer(THREAD_NAME, true);

  private static final SimpleTimer ourInstance = new SimpleTimer();
  private static final String THREAD_NAME = "SimpleTimer";

  private long myNextScheduledTime = Long.MAX_VALUE;
  private TimerTask myNextProcessingTask;

  private final Map<Long, ArrayList<SimpleTimerTask>> myTime2Task = new TreeMap<Long, ArrayList<SimpleTimerTask>>();

  private SimpleTimer() {
  }

  public static SimpleTimer getInstance() {
    return ourInstance;
  }

  public SimpleTimerTask setUp(final Runnable runnable, long delay) {
    synchronized (myTime2Task) {
      final long current = System.currentTimeMillis();
      final long targetTime = current + delay;

      final SimpleTimerTask result = new SimpleTimerTask(targetTime, runnable);

      ArrayList<SimpleTimerTask> tasks = myTime2Task.get(targetTime);
      if (tasks == null) {
        tasks = new ArrayList<SimpleTimerTask>(2);
        myTime2Task.put(targetTime, tasks);
      }
      tasks.add(result);

      if (targetTime < myNextScheduledTime) {
        if (myNextProcessingTask != null) {
          myNextProcessingTask.cancel();
        }
        scheduleNext(delay, targetTime);
      }

      return result;
    }
  }

  private void scheduleNext(long delay, long targetTime) {
    myNextScheduledTime = targetTime;
    myNextProcessingTask = new TimerTask() {
      public void run() {
        processNext();
      }
    };
    ourTimer.schedule(myNextProcessingTask, delay);
  }

  private void processNext() {
    Ref<ArrayList<SimpleTimerTask>> tasks = new Ref<ArrayList<SimpleTimerTask>>();

    synchronized (myTime2Task) {
      final long current = System.currentTimeMillis();

      final Iterator<Long> times = myTime2Task.keySet().iterator();
      final Long time = times.next();
      tasks.set(myTime2Task.get(time));
      times.remove();

      if (!times.hasNext()) {
        myNextScheduledTime = Long.MAX_VALUE;
        myNextProcessingTask = null;
      } else {
        Long nextEffectiveTime = null;
        while (times.hasNext()) {
          Long nextTime = times.next();
          if (nextTime <= current) {
            tasks.get().addAll(myTime2Task.get(nextTime));
            times.remove();
          } else {
            nextEffectiveTime = nextTime;
            break;
          }
        }

        if (nextEffectiveTime == null) {
          myNextProcessingTask = null;
          myNextScheduledTime = Long.MAX_VALUE;
        } else {
          scheduleNext(nextEffectiveTime - current, nextEffectiveTime);
        }
      }
    }

    final ArrayList<SimpleTimerTask> toRun = tasks.get();
    for (SimpleTimerTask each : toRun) {
      if (!each.isCancelled()) {
        each.run();
      }
    }
  }

  public boolean isTimerThread() {
    return isTimerThread(Thread.currentThread());
  }

  public boolean isTimerThread(Thread thread) {
    return THREAD_NAME.equals(thread.getName());
  }

}