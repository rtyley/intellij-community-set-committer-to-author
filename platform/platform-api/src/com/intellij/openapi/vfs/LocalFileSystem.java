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
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.util.Processor;
import com.intellij.util.io.fs.IFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

public abstract class LocalFileSystem extends NewVirtualFileSystem {
  @NonNls public static final String PROTOCOL = "file";
  @NonNls public static final String PROTOCOL_PREFIX = PROTOCOL + "://";

  private static LocalFileSystem ourInstance = CachedSingletonsRegistry.markCachedField(LocalFileSystem.class);

  public static LocalFileSystem getInstance() {
    if (ourInstance == null) {
      ourInstance = ApplicationManager.getApplication().getComponent(LocalFileSystem.class);
    }
    return ourInstance;
  }

  @Nullable
  public abstract VirtualFile findFileByIoFile(File file);

  @Nullable
  public abstract VirtualFile findFileByIoFile(IFile file);

  @Nullable
  public abstract VirtualFile refreshAndFindFileByIoFile(@NotNull File file);

  @Nullable
  public abstract VirtualFile refreshAndFindFileByIoFile(final IFile ioFile);

  /**
   * Performs a nonrecursive synchronous refresh of specified files
   * @param files files to refresh
   * @since 6.0
   */
  public abstract void refreshIoFiles(Iterable<File> files);

  /**
   * Performs a nonrecursive synchronous refresh of specified files
   * @param files files to refresh
   * @since 6.0
   */
  public abstract void refreshFiles(Iterable<VirtualFile> files);


  public abstract byte[] physicalContentsToByteArray(final VirtualFile virtualFile) throws IOException;
  public abstract long physicalLength(final VirtualFile virtualFile) throws IOException;

  public interface WatchRequest {
    @NotNull String getRootPath();

    @NotNull String getFileSystemRootPath();

    boolean isToWatchRecursively();

    boolean dominates (WatchRequest other);
  }

  /**
   * Adds this rootFile as the watch root for file system
   * @param rootPath
   * @param toWatchRecursively whether the whole subtree should be monitored
   * @return request handle or null if rootFile does not belong to this file system
   */
  @Nullable
  public abstract WatchRequest addRootToWatch(@NotNull final String rootPath, final boolean toWatchRecursively);

  @NotNull
  public abstract Set<WatchRequest> addRootsToWatch(@NotNull final Collection<String> rootPaths, final boolean toWatchRecursively);

  public abstract void removeWatchedRoots(@NotNull final Collection<WatchRequest> rootsToWatch);

  public abstract void removeWatchedRoot(@NotNull final WatchRequest watchRequest);

  public abstract void registerAuxiliaryFileOperationsHandler(LocalFileOperationsHandler handler);
  public abstract void unregisterAuxiliaryFileOperationsHandler(LocalFileOperationsHandler handler);

  public abstract boolean processCachedFilesInSubtree(final VirtualFile file, Processor<VirtualFile> processor);
}
