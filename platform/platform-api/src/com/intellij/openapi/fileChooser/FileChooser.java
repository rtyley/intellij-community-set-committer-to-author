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
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public class FileChooser {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileChooser.FileChooser");

  private FileChooser() { }

  /**
   * @deprecated use {@linkplain #chooseFiles(FileChooserDescriptor,
   *                                          com.intellij.openapi.project.Project,
   *                                          com.intellij.openapi.vfs.VirtualFile)} (to remove in IDEA 13)
   */
  @SuppressWarnings("UnusedDeclaration")
  @NotNull
  public static VirtualFile[] chooseFiles(Project project, FileChooserDescriptor descriptor) {
    return chooseFiles(descriptor, null, project, null);
  }

  /**
   * @deprecated use {@linkplain #chooseFiles(FileChooserDescriptor,
   *                                          java.awt.Component,
   *                                          com.intellij.openapi.project.Project,
   *                                          com.intellij.openapi.vfs.VirtualFile)} (to remove in IDEA 13)
   */
  @SuppressWarnings("UnusedDeclaration")
  @NotNull
  public static VirtualFile[] chooseFiles(Component parent, FileChooserDescriptor descriptor) {
    return chooseFiles(descriptor, parent, null, null);
  }

  /**
   * @deprecated use {@linkplain #chooseFiles(FileChooserDescriptor,
   *                                          com.intellij.openapi.project.Project,
   *                                          com.intellij.openapi.vfs.VirtualFile)} (to remove in IDEA 13)
   */
  @SuppressWarnings("UnusedDeclaration")
  @NotNull
  public static VirtualFile[] chooseFiles(Project project, FileChooserDescriptor descriptor, @Nullable VirtualFile toSelect) {
    return chooseFiles(descriptor, null, project, toSelect);
  }

  /**
   * @deprecated use {@linkplain #chooseFiles(FileChooserDescriptor,
   *                                          java.awt.Component,
   *                                          com.intellij.openapi.project.Project,
   *                                          com.intellij.openapi.vfs.VirtualFile)} (to remove in IDEA 13)
   */
  @SuppressWarnings("UnusedDeclaration")
  @NotNull
  public static VirtualFile[] chooseFiles(Component parent, FileChooserDescriptor descriptor, @Nullable VirtualFile toSelect) {
    return chooseFiles(descriptor, parent, null, toSelect);
  }

  @NotNull
  public static VirtualFile[] chooseFiles(@NotNull final FileChooserDescriptor descriptor,
                                          @Nullable final Project project,
                                          @Nullable final VirtualFile toSelect) {
    return chooseFiles(descriptor, null, project, toSelect);
  }

  @NotNull
  public static VirtualFile[] chooseFiles(@NotNull final FileChooserDescriptor descriptor,
                                          @Nullable final Component parent,
                                          @Nullable final Project project,
                                          @Nullable final VirtualFile toSelect) {
    final FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, parent);
    return chooser.choose(toSelect, project);
  }

  /**
   * @deprecated use {@linkplain #chooseFile(FileChooserDescriptor,
   *                                         com.intellij.openapi.project.Project,
   *                                         com.intellij.openapi.vfs.VirtualFile)} (to remove in IDEA 13)
   */
  @SuppressWarnings("UnusedDeclaration")
  @Nullable
  public static VirtualFile chooseFile(Project project, FileChooserDescriptor descriptor) {
    return chooseFile(descriptor, null, project, null);
  }

  /**
   * @deprecated use {@linkplain #chooseFile(FileChooserDescriptor,
   *                                         java.awt.Component,
   *                                         com.intellij.openapi.project.Project,
   *                                         com.intellij.openapi.vfs.VirtualFile)} (to remove in IDEA 13)
   */
  @SuppressWarnings("UnusedDeclaration")
  @Nullable
  public static VirtualFile chooseFile(Component parent, FileChooserDescriptor descriptor) {
    return chooseFile(descriptor, parent, null, null);
  }

  /**
   * @deprecated use {@linkplain #chooseFile(FileChooserDescriptor,
   *                                         com.intellij.openapi.project.Project,
   *                                         com.intellij.openapi.vfs.VirtualFile)} (to remove in IDEA 13)
   */
  @SuppressWarnings("UnusedDeclaration")
  @Nullable
  public static VirtualFile chooseFile(Project project, FileChooserDescriptor descriptor, @Nullable VirtualFile toSelect) {
    return chooseFile(descriptor, null, project, toSelect);
  }

  /**
   * @deprecated use {@linkplain #chooseFile(FileChooserDescriptor,
   *                                         java.awt.Component,
   *                                         com.intellij.openapi.project.Project,
   *                                         com.intellij.openapi.vfs.VirtualFile)} (to remove in IDEA 13)
   */
  @SuppressWarnings("UnusedDeclaration")
  @Nullable
  public static VirtualFile chooseFile(Component parent, FileChooserDescriptor descriptor, @Nullable VirtualFile toSelect) {
    return chooseFile(descriptor, parent, null, toSelect);
  }

  @Nullable
  public static VirtualFile chooseFile(@NotNull final FileChooserDescriptor descriptor,
                                       @Nullable final Project project,
                                       @Nullable final VirtualFile toSelect) {
    return chooseFile(descriptor, null, project, toSelect);
  }

  @Nullable
  public static VirtualFile chooseFile(@NotNull final FileChooserDescriptor descriptor,
                                       @Nullable final Component parent,
                                       @Nullable final Project project,
                                       @Nullable final VirtualFile toSelect) {
    LOG.assertTrue(!descriptor.isChooseMultiple());
    return ArrayUtil.getFirstElement(chooseFiles(descriptor, parent, project, toSelect));
  }

  /**
   * @deprecated use {@linkplain #chooseFiles(FileChooserDescriptor,
   *                                          com.intellij.openapi.project.Project,
   *                                          com.intellij.openapi.vfs.VirtualFile,
   *                                          com.intellij.util.Consumer)} (to remove in IDEA 13)
   */
  @SuppressWarnings("UnusedDeclaration")
  public static void chooseFilesWithSlideEffect(@NotNull final FileChooserDescriptor descriptor,
                                                @Nullable final Project project,
                                                @Nullable final VirtualFile toSelect,
                                                @NotNull final Consumer<VirtualFile[]> callback) {
    chooseFiles(descriptor, project, toSelect, new Consumer<List<VirtualFile>>() {
      @Override
      public void consume(final List<VirtualFile> files) {
        callback.consume(VfsUtil.toVirtualFileArray(files));
      }
    });
  }

  /**
   * @deprecated use {@linkplain #chooseFiles(FileChooserDescriptor,
   *                                          com.intellij.openapi.project.Project,
   *                                          java.awt.Component,
   *                                          com.intellij.openapi.vfs.VirtualFile,
   *                                          com.intellij.util.Consumer)} (to remove in IDEA 13)
   */
  @SuppressWarnings("UnusedDeclaration")
  public static void chooseFilesWithSlideEffect(@NotNull final FileChooserDescriptor descriptor,
                                                @Nullable final Project project,
                                                @Nullable final Component parent,
                                                @Nullable final VirtualFile toSelect,
                                                @NotNull final Consumer<VirtualFile[]> callback) {
    chooseFiles(descriptor, project, parent, toSelect, new Consumer<List<VirtualFile>>() {
      @Override
      public void consume(final List<VirtualFile> files) {
        callback.consume(VfsUtil.toVirtualFileArray(files));
      }
    });
  }

  /**
   * Shows file/folder open dialog, allows user to choose files/folders and then passes result to callback in EDT.
   * On MacOS Open Dialog will be shown with slide effect if Macish UI is turned on.
   *
   * @param descriptor file chooser descriptor
   * @param project    project
   * @param toSelect   file to preselect
   * @param callback   callback will be invoked after user have closed dialog
   * @since 11.1
   */
  public static void chooseFiles(@NotNull final FileChooserDescriptor descriptor,
                                 @Nullable final Project project,
                                 @Nullable final VirtualFile toSelect,
                                 @NotNull final Consumer<List<VirtualFile>> callback) {
    chooseFiles(descriptor, project, null, toSelect, callback);
  }

  /**
   * Shows file/folder open dialog, allows user to choose files/folders and then passes result to callback in EDT.
   * On MacOS Open Dialog will be shown with slide effect if Macish UI is turned on.
   *
   * @param descriptor file chooser descriptor
   * @param project    project
   * @param parent     parent component
   * @param toSelect   file to preselect
   * @param callback   callback will be invoked after user have closed dialog
   * @since 11.1
   */
  public static void chooseFiles(@NotNull final FileChooserDescriptor descriptor,
                                 @Nullable final Project project,
                                 @Nullable final Component parent,
                                 @Nullable final VirtualFile toSelect,
                                 @NotNull final Consumer<List<VirtualFile>> callback) {
    final FileChooserFactory factory = FileChooserFactory.getInstance();
    final PathChooserDialog pathChooser = factory.createPathChooser(descriptor, project, parent);
    pathChooser.choose(toSelect, callback);
  }
}