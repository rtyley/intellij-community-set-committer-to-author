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
package com.intellij.execution.filters;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public final class OpenFileHyperlinkInfo implements FileHyperlinkInfo {
  private final OpenFileDescriptor myDescriptor;

  public OpenFileHyperlinkInfo(@NotNull OpenFileDescriptor descriptor) {
    myDescriptor = descriptor;
  }

  public OpenFileHyperlinkInfo(Project project, @NotNull final VirtualFile file, final int line, final int column) {
    this (new OpenFileDescriptor(project, file, line, column));
  }

  public OpenFileHyperlinkInfo(Project project, @NotNull final VirtualFile file, final int line) {
    this (project, file, line, 0);
  }

  public OpenFileDescriptor getDescriptor() {
    return myDescriptor;
  }

  public void navigate(final Project project) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final VirtualFile file = myDescriptor.getFile();
        if(file.isValid()) {
          FileEditorManager.getInstance(project).openTextEditor(myDescriptor, true);
        }
      }
    });
  }
}
