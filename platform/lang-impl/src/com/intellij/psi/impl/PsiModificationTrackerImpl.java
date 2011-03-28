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
package com.intellij.psi.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.messages.MessageBus;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author mike
 * Date: Jul 18, 2002
 */
public class PsiModificationTrackerImpl implements PsiModificationTracker, PsiTreeChangePreprocessor {
  private final AtomicLong myModificationCount = new AtomicLong(0);
  private final AtomicLong myOutOfCodeBlockModificationCount = new AtomicLong(0);
  private final AtomicLong myJavaStructureModificationCount = new AtomicLong(0);
  private final Listener myPublisher;

  public PsiModificationTrackerImpl(Project project) {
    final MessageBus bus = project.getMessageBus();
    myPublisher = bus.syncPublisher(ProjectTopics.MODIFICATION_TRACKER);
    bus.connect().subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {

      public void enteredDumbMode() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            incCounter();
          }
        });
      }

      public void exitDumbMode() {
        enteredDumbMode();
      }
    });
  }

  public void incCounter() {
    myModificationCount.getAndIncrement();
    myJavaStructureModificationCount.getAndIncrement();
    incOutOfCodeBlockModificationCounter();
  }

  public void incOutOfCodeBlockModificationCounter() {
    myOutOfCodeBlockModificationCount.getAndIncrement();
    myPublisher.modificationCountChanged();
  }

  public void treeChanged(PsiTreeChangeEventImpl event) {
    myModificationCount.getAndIncrement();
    if (event.getParent() instanceof PsiDirectory) {
      incOutOfCodeBlockModificationCounter();
    }

    myPublisher.modificationCountChanged();
  }

  public long getModificationCount() {
    return myModificationCount.get();
  }

  public long getOutOfCodeBlockModificationCount() {
    return myOutOfCodeBlockModificationCount.get();
  }

  public long getJavaStructureModificationCount() {
    return myJavaStructureModificationCount.get();
  }
}
