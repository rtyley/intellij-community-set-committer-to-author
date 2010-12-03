/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.tasks.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class TaskUtil {
  public static String getChangeListName(Task task) {
    return task.isIssue() ? task.getId() + ": " + task.getSummary() : task.getSummary();
  }

  @Nullable
  public static String getChangeListComment(TaskManagerImpl manager, Task task) {
    final TaskRepository repository = task.getRepository();
    if (repository != null) return repository.getTaskComment(task);
    for (TaskRepository repo : manager.getAllRepositories()) {
      try {
        final Task origin = repo.findTask(task.getId());
        if (origin != null) return repo.getTaskComment(origin);
      } catch (Exception ignored) {}
    }
    return null;
  }

  public static String getTrimmedSummary(LocalTask task) {
    String text;
    if (task.isIssue()) {
      text = task.getId() + ": " + task.getSummary();
    } else {
      text = task.getSummary();
    }
    return StringUtil.first(text, 60, true);
  }
}
