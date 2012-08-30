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
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.LocalFileSystem;

import java.util.*;

/**
* @author irengrig
*/
public class FileWatchRequestModifier implements Runnable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.projectlevelman.FileWatchRequestModifier");

  private final Project myProject;
  private final NewMappings myNewMappings;
  private final Map<VcsDirectoryMapping, LocalFileSystem.WatchRequest> myDirectoryMappingWatches;
  private final LocalFileSystem myLfs;

  public FileWatchRequestModifier(final Project project, final NewMappings newMappings, final LocalFileSystem localFileSystem) {
    myLfs = localFileSystem;
    myProject = project;
    myNewMappings = newMappings;
    myDirectoryMappingWatches = new HashMap<VcsDirectoryMapping, LocalFileSystem.WatchRequest>();
  }

  @Override
  public void run() {
    if ((! myProject.isInitialized()) || myProject.isDisposed()) return;
    final List<VcsDirectoryMapping> copy = myNewMappings.getDirectoryMappings();

    final List<VcsDirectoryMapping> added = new LinkedList<VcsDirectoryMapping>(copy);
    added.removeAll(myDirectoryMappingWatches.keySet());

    final List<VcsDirectoryMapping> deleted = new LinkedList<VcsDirectoryMapping>(myDirectoryMappingWatches.keySet());
    deleted.removeAll(copy);

    final Map<String, VcsDirectoryMapping> toAdd = new HashMap<String, VcsDirectoryMapping>();
    for (VcsDirectoryMapping mapping : added) {
      if (!mapping.isDefaultMapping()) {
        toAdd.put(mapping.getDirectory(), mapping);
      }
    }

    final Collection<LocalFileSystem.WatchRequest> toRemove = new ArrayList<LocalFileSystem.WatchRequest>();
    for (VcsDirectoryMapping mapping : deleted) {
      if (mapping.isDefaultMapping()) continue;
      final LocalFileSystem.WatchRequest removed = myDirectoryMappingWatches.remove(mapping);
      if (removed != null) {
        toRemove.add(removed);
      }
    }

    final Set<LocalFileSystem.WatchRequest> requests = myLfs.replaceWatchedRoots(toRemove, toAdd.keySet(), null);
    for (LocalFileSystem.WatchRequest request : requests) {
      final VcsDirectoryMapping mapping = toAdd.get(request.getRootPath());
      if (mapping != null) {
        myDirectoryMappingWatches.put(mapping, request);
      }
      else {
        LOG.error("root=" + request.getRootPath() + " toAdd=" + toAdd.keySet());
      }
    }
  }
}
