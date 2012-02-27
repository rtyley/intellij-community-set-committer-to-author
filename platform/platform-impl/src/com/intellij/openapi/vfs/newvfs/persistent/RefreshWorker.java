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
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.SymlinkDirectory;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.containers.Queue;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem.*;

/**
 * @author max
 */
public class RefreshWorker {
  private final VirtualFile myRefreshRoot;
  private final boolean myIsRecursive;
  private final Queue<VirtualFile> myRefreshQueue = new Queue<VirtualFile>(100);

  private final List<VFileEvent> myEvents = new ArrayList<VFileEvent>();

  public RefreshWorker(final VirtualFile refreshRoot, final boolean isRecursive) {
    myRefreshRoot = refreshRoot;
    myIsRecursive = isRecursive;
    myRefreshQueue.addLast(refreshRoot);
  }

  public void scan() {
    final NewVirtualFile root = (NewVirtualFile)myRefreshRoot;
    NewVirtualFileSystem delegate = root.getFileSystem();
    final int rootAttributes = delegate.getBooleanAttributes(root, BA_EXISTS | BA_DIRECTORY);

    if (root.isDirty() && (rootAttributes & BA_EXISTS) == 0) {
      scheduleDeletion(root);
      root.markClean();
    }
    else {
      if ((rootAttributes & BA_DIRECTORY) != 0) {
        delegate = PersistentFS.replaceWithNativeFS(delegate);
      }

      final PersistentFS persistence = (PersistentFS)ManagingFS.getInstance();

      while (!myRefreshQueue.isEmpty()) {
        final VirtualFileSystemEntry file = (VirtualFileSystemEntry)myRefreshQueue.pullFirst();
        if (!file.isDirty()) continue;

        if (file instanceof SymlinkDirectory) {
          final int attributes = delegate.getBooleanAttributes(file, -1);
          scheduleChildRefresh(file.getParent(), file, delegate, attributes);
        }
        else if (file.isDirectory()) {
          final VirtualDirectoryImpl dir = (VirtualDirectoryImpl)file;
          final boolean fullSync = dir.allChildrenLoaded();
          if (fullSync) {
            final Set<String> currentNames = new HashSet<String>(Arrays.asList(persistence.list(file)));
            final Set<String> upToDateNames = new HashSet<String>(Arrays.asList(VfsUtil.filterNames(delegate.list(file))));

            final Set<String> newNames = new HashSet<String>(upToDateNames);
            newNames.removeAll(currentNames);

            final Set<String> deletedNames = new HashSet<String>(currentNames);
            deletedNames.removeAll(upToDateNames);

            for (String name : deletedNames) {
              scheduleDeletion(file.findChild(name));
            }

            for (String name : newNames) {
              boolean isDirectory = delegate.isDirectory(new FakeVirtualFile(file, name));
              scheduleCreation(file, name, isDirectory);
            }

            for (VirtualFile child : file.getChildren()) {
              if (!deletedNames.contains(child.getName())) {
                final int childAttributes = delegate.getBooleanAttributes(child, -1);
                scheduleChildRefresh(file, child, delegate, childAttributes);
              }
            }
          }
          else {
            for (VirtualFile child : file.getCachedChildren()) {
              final int childAttributes = delegate.getBooleanAttributes(child, -1);
              if ((childAttributes & BA_EXISTS) != 0) {
                scheduleChildRefresh(file, child, delegate, childAttributes);
              }
              else {
                scheduleDeletion(child);
              }
            }

            final List<String> names = dir.getSuspiciousNames();
            for (String name : names) {
              if (name.isEmpty()) continue;

              final VirtualFile fake = new FakeVirtualFile(file, name);
              final int attributes = delegate.getBooleanAttributes(fake, BA_EXISTS | BA_DIRECTORY);
              if ((attributes & BA_EXISTS) != 0) {
                final boolean isDir = (attributes & BA_DIRECTORY) != 0;
                scheduleCreation(file, name, isDir);
              }
            }
          }
        }
        else {
          long currentTimestamp = persistence.getTimeStamp(file);
          long upToDateTimestamp = delegate.getTimeStamp(file);
          long currentLength = SystemInfo.isUnix ? persistence.getLength(file) : -1;
          long upToDateLength = SystemInfo.isUnix ? delegate.getLength(file) : -1;

          if (currentTimestamp != upToDateTimestamp || currentLength != upToDateLength) {
            scheduleUpdateContent(file);
          }
        }

        boolean currentWritable = persistence.isWritable(file);
        boolean upToDateWritable = delegate.isWritable(file);

        if (currentWritable != upToDateWritable) {
          scheduleWritableAttributeChange(file, currentWritable, upToDateWritable);
        }

        file.markClean();
      }
    }
  }

  private static final int SPECIAL_MASK = BA_REGULAR | BA_DIRECTORY | BA_EXISTS;

  // todo[r.sh] compare link targets for files too
  private void scheduleChildRefresh(@NotNull VirtualFileSystemEntry parent,
                                    @NotNull VirtualFile child,
                                    @NotNull NewVirtualFileSystem delegate,
                                    @FileBooleanAttributes int childAttributes) {
    final boolean currentIsDirectory = child.isDirectory();
    final boolean currentIsSymlink = child.isSymLink();
    final boolean currentIsSpecial = child.isSpecialFile();
    final String currentLinkTarget = child instanceof SymlinkDirectory ? ((SymlinkDirectory)child).getTargetPath() : null;
    final boolean upToDateIsDirectory = (childAttributes & BA_DIRECTORY) != 0;
    final boolean upToDateIsSymlink = delegate.isSymLink(child);
    final boolean upToDateIsSpecial = (childAttributes & SPECIAL_MASK) == BA_EXISTS;
    final String upToDateLinkTarget = currentLinkTarget != null ? delegate.resolveSymLink(child) : null;

    if (currentIsDirectory != upToDateIsDirectory ||
        currentIsSymlink != upToDateIsSymlink ||
        currentIsSpecial != upToDateIsSpecial ||
        !Comparing.equal(currentLinkTarget, upToDateLinkTarget)) {
      scheduleDeletion(child);
      scheduleReCreation(parent, child.getName(), upToDateIsDirectory);
    }
    else if (myIsRecursive || !upToDateIsDirectory) {
      myRefreshQueue.addLast(child);
    }
  }

  private void scheduleWritableAttributeChange(final VirtualFileSystemEntry file,
                                               final boolean currentWritable,
                                               final boolean upToDateWritable) {
    myEvents.add(new VFilePropertyChangeEvent(null, file, VirtualFile.PROP_WRITABLE, currentWritable, upToDateWritable, true));
  }

  private void scheduleUpdateContent(final VirtualFileSystemEntry file) {
    myEvents.add(new VFileContentChangeEvent(null, file, file.getModificationStamp(), -1, true));
  }

  private void scheduleCreation(final VirtualFileSystemEntry parent, final String childName, final boolean isDirectory) {
    myEvents.add(new VFileCreateEvent(null, parent, childName, isDirectory, true, false));
  }

  private void scheduleReCreation(final VirtualFileSystemEntry parent, final String childName, final boolean isDirectory) {
    myEvents.add(new VFileCreateEvent(null, parent, childName, isDirectory, true, true));
  }

  private void scheduleDeletion(final VirtualFile file) {
    if (file == null) return;
    myEvents.add(new VFileDeleteEvent(null, file, true));
  }

  public List<VFileEvent> getEvents() {
    return myEvents;
  }
}
