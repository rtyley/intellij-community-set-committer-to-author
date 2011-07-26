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
package com.intellij.openapi.roots.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class PushedFilePropertiesUpdater {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater");

  private final Project myProject;
  private final FilePropertyPusher[] myPushers;
  private final FilePropertyPusher[] myFilePushers;

  public static PushedFilePropertiesUpdater getInstance(Project project) {
    return project.getComponent(PushedFilePropertiesUpdater.class);
  }

  public PushedFilePropertiesUpdater(final Project project, final MessageBus bus) {
    myProject = project;
    myPushers = Extensions.getExtensions(FilePropertyPusher.EP_NAME);
    myFilePushers = ContainerUtil.findAllAsArray(myPushers, new Condition<FilePropertyPusher>() {
      public boolean value(FilePropertyPusher pusher) {
        return !pusher.pushDirectoriesOnly();
      }
    });

    StartupManager.getInstance(project).registerPreStartupActivity(new Runnable() {
      public void run() {
        long l = System.currentTimeMillis();
        pushAll(myPushers);
        LOG.info("File properties pushed in " + (System.currentTimeMillis() - l) + " ms");

        final MessageBusConnection connection = bus.connect();
        connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
          public void beforeRootsChange(final ModuleRootEvent event) {
          }

          public void rootsChanged(final ModuleRootEvent event) {
            pushAll(myPushers);
            for (FilePropertyPusher pusher : myPushers) {
              pusher.afterRootsChanged(project);
            }
          }
        });

        connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(new VirtualFileAdapter() {
          @Override
          public void fileCreated(final VirtualFileEvent event) {
            final VirtualFile file = event.getFile();
            final FilePropertyPusher[] pushers = file.isDirectory() ? myPushers : myFilePushers;
            pushRecursively(file, pushers);
          }

          @Override
          public void fileMoved(final VirtualFileMoveEvent event) {
            final VirtualFile file = event.getFile();
            final FilePropertyPusher[] pushers = file.isDirectory() ? myPushers : myFilePushers;
            for (FilePropertyPusher pusher : pushers) {
              file.putUserData(pusher.getFileDataKey(), null);
            }
            pushRecursively(file, pushers);
          }
        }));
        for (final FilePropertyPusher pusher : myPushers) {
          pusher.initExtra(project, bus, new FilePropertyPusher.Engine() {
            public void pushAll() {
              PushedFilePropertiesUpdater.this.pushAll(pusher);
            }

            public void pushRecursively(VirtualFile file, Project project) {
              PushedFilePropertiesUpdater.this.pushRecursively(file, pusher);
            }
          });
        }
      }
    });
  }

  private void pushRecursively(final VirtualFile dir, final FilePropertyPusher... pushers) {

    final Object[] values = new Object[pushers.length];
    VirtualFile parent = dir.getParent();
    for (int i = 0, pushersLength = pushers.length; i < pushersLength; i++) {
      FilePropertyPusher pusher = pushers[i];
      if (parent != null) {
        values[i] = parent.getUserData(pusher.getFileDataKey());
      }
      if (values[i] == null) {
        values[i] = pusher.getDefaultValue();
      }
    }
    iterateContentUnderDirectory(dir, values, pushers);
  }

  public void pushAll(final FilePropertyPusher... pushers) {
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      final Object[] values = new Object[pushers.length];
      for (int i = 0; i < values.length; i++) {
        values[i] = pushers[i].getImmediateValue(module);
        if (values[i] == null) {
          values[i] = pushers[i].getDefaultValue();
        }
      }
      for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
        iterateContentUnderDirectory(root, values, pushers);
      }
    }
  }

  private void iterateContentUnderDirectory(VirtualFile root,
                                            final Object[] values,
                                            final FilePropertyPusher[] pushers) {
    FileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    index.iterateContentUnderDirectory(root, new ContentIterator() {
      public boolean processFile(final VirtualFile fileOrDir) {
        final boolean isDir = fileOrDir.isDirectory();
        for (int i = 0, pushersLength = pushers.length; i < pushersLength; i++) {
          final FilePropertyPusher<Object> pusher = pushers[i];
          if (!isDir && (pusher.pushDirectoriesOnly() || !pusher.acceptsFile(fileOrDir))) continue;
          values[i] = findAndUpdateValue(myProject, fileOrDir, pusher, values[i]);
        }
        return true;
      }
    });
  }

  @Nullable
  public static <T> T findAndUpdateValue(final Project project,
                                         final VirtualFile fileOrDir,
                                         final FilePropertyPusher<T> pusher,
                                         final T parentValue) {
    final T immediateValue = pusher.getImmediateValue(project, fileOrDir);
    final T value = immediateValue != null ? immediateValue : parentValue;
    updateValue(fileOrDir, value, pusher);
    return value;
  }

  private static <T> void updateValue(final VirtualFile fileOrDir, final T value, final FilePropertyPusher<T> pusher) {
    final T oldValue = fileOrDir.getUserData(pusher.getFileDataKey());
    if (value != oldValue) {
      fileOrDir.putUserData(pusher.getFileDataKey(), value);
      try {
        pusher.persistAttribute(fileOrDir, value);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }
}
