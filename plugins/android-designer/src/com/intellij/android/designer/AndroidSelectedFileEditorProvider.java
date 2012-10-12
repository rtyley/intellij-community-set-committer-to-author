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
package com.intellij.android.designer;

import com.intellij.openapi.components.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
public class AndroidSelectedFileEditorProvider implements SelectedFileEditorProvider {
  @Nullable
  @Override
  public FileEditorProvider getSelectedProvider(Project project, VirtualFile openedFile) {
    if (!AndroidDesignerEditorProvider.acceptLayout(project, openedFile)) {
      return null;
    }

    String editorTypeId = MyState.getInstance(project).editorTypeId;
    if (editorTypeId != null) {
      return FileEditorProviderManager.getInstance().getProvider(editorTypeId);
    }

    return null;
  }

  public static class MyEditorListener implements ProjectComponent {
    private final Project myProject;

    public MyEditorListener(Project project) {
      myProject = project;
    }

    @Override
    public void projectOpened() {
      StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
        public void run() {
          myProject.getMessageBus().connect(myProject)
            .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
              @Override
              public void fileOpened(FileEditorManager source, VirtualFile file) {
              }

              @Override
              public void fileClosed(FileEditorManager source, VirtualFile file) {
              }

              @Override
              public void selectionChanged(FileEditorManagerEvent event) {
                VirtualFile file = event.getNewFile();
                if (file != null && AndroidDesignerEditorProvider.acceptLayout(myProject, file)) {
                  FileEditorProvider provider = EditorHistoryManager.getInstance(myProject).getSelectedProvider(file);
                  if (provider != null) {
                    MyState.getInstance(myProject).editorTypeId = provider.getEditorTypeId();
                  }
                }
              }
            });
        }
      });
    }

    @Override
    public void projectClosed() {
    }

    @Override
    public void initComponent() {
    }

    @Override
    public void disposeComponent() {
    }

    @NotNull
    @Override
    public String getComponentName() {
      return "AndroidLayoutSelectedEditorListener";
    }
  }

  @State(name = "AndroidLayoutSelectedEditor", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
  public static class MyState implements PersistentStateComponent<MyState> {
    public String editorTypeId;

    @Nullable
    @Override
    public MyState getState() {
      return this;
    }

    @Override
    public void loadState(MyState state) {
      XmlSerializerUtil.copyBean(state, this);
    }

    public static MyState getInstance(Project project) {
      return ServiceManager.getService(project, MyState.class);
    }
  }
}