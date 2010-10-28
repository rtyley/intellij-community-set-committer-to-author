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
package com.intellij.ide;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

/**
 * @author mike
 */
public class OpenFileXmlRpcHandler {
  @SuppressWarnings({"MethodMayBeStatic"})
  public boolean open(final String absolutePath) {
    final Application application = ApplicationManager.getApplication();

    application.invokeLater(new Runnable() {
      public void run() {
        final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length == 0) return;
        Project project = openProjects[0];

        String correctPath = absolutePath.replace(File.separatorChar, '/');
        final VirtualFile[] virtualFiles = new VirtualFile[1];
        final String correctPath1 = correctPath;
        application.runWriteAction(new Runnable() {
          public void run() {
            virtualFiles[0] = LocalFileSystem.getInstance().refreshAndFindFileByPath(correctPath1);
          }
        });
        if (virtualFiles[0] == null) return;

        FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();
        if (editorProviderManager.getProviders(project, virtualFiles[0]).length == 0) return;
        OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFiles[0]);
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
      }
    });

    return true;
  }
}
