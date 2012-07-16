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

package com.intellij.history.integration;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.Paths;
import com.intellij.history.core.StoredContent;
import com.intellij.history.core.tree.DirectoryEntry;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.FileEntry;
import com.intellij.history.core.tree.RootEntry;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;

public class IdeaGateway {
  private static final Key<ContentAndTimestamps> SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY
    = Key.create("LocalHistory.SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY");

  public boolean isVersioned(@NotNull VirtualFile f) {
    if (!f.isInLocalFileSystem()) return false;

    String fileName = f.getName();
    if (!f.isDirectory() && fileName.endsWith(".class")) return false;

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project each : openProjects) {
      if (each.isDefault()) continue;
      if (!each.isInitialized()) continue;
      if (Comparing.equal(each.getWorkspaceFile(), f)) return false;
      if (ProjectRootManager.getInstance(each).getFileIndex().isIgnored(f)) return false;
    }

    // optimisation: FileTypeManager.isFileIgnored(f) already checked inside ProjectFileIndex.isIgnored()
    return openProjects.length != 0 || !FileTypeManager.getInstance().isFileIgnored(f);
  }

  public boolean areContentChangesVersioned(@NotNull VirtualFile f) {
    return isVersioned(f) && !f.isDirectory() && areContentChangesVersioned(f.getName());
  }

  public boolean areContentChangesVersioned(@NotNull String fileName) {
    return !FileTypeManager.getInstance().getFileTypeByFileName(fileName).isBinary();
  }

  public boolean ensureFilesAreWritable(@NotNull Project p, @NotNull List<VirtualFile> ff) {
    ReadonlyStatusHandler h = ReadonlyStatusHandler.getInstance(p);
    return !h.ensureFilesWritable(VfsUtil.toVirtualFileArray(ff)).hasReadonlyFiles();
  }

  @Nullable
  public VirtualFile findVirtualFile(@NotNull String path) {
    return LocalFileSystem.getInstance().findFileByPath(path);
  }

  @NotNull
  public VirtualFile findOrCreateFileSafely(@NotNull VirtualFile parent, @NotNull String name, boolean isDirectory) throws IOException {
    VirtualFile f = parent.findChild(name);
    if (f != null && f.isDirectory() != isDirectory) {
      f.delete(this);
      f = null;
    }
    if (f == null) {
      f = isDirectory
          ? parent.createChildDirectory(this, name)
          : parent.createChildData(this, name);
    }
    return f;
  }

  @NotNull
  public VirtualFile findOrCreateFileSafely(@NotNull String path, boolean isDirectory) throws IOException {
    VirtualFile f = findVirtualFile(path);
    if (f != null && f.isDirectory() != isDirectory) {
      f.delete(this);
      f = null;
    }
    if (f == null) {
      VirtualFile parent = findOrCreateFileSafely(Paths.getParentOf(path), true);
      String name = Paths.getNameOf(path);
      f = isDirectory
          ? parent.createChildDirectory(this, name)
          : parent.createChildData(this, name);
    }
    return f;
  }

  public List<VirtualFile> getAllFilesFrom(@NotNull String path) {
    VirtualFile f = findVirtualFile(path);
    if (f == null) return Collections.emptyList();
    return collectFiles(f, new ArrayList<VirtualFile>());
  }

  @NotNull
  private static List<VirtualFile> collectFiles(@NotNull VirtualFile f, @NotNull List<VirtualFile> result) {
    if (f.isDirectory()) {
      for (VirtualFile child : iterateDBChildren(f)) {
        collectFiles(child, result);
      }
    }
    else {
      result.add(f);
    }
    return result;
  }

  @NotNull
  public static Collection<VirtualFile> iterateDBChildren(VirtualFile f) {
    if (!(f instanceof NewVirtualFile)) return Collections.emptyList();
    NewVirtualFile nf = (NewVirtualFile)f;
    return nf.getCachedChildren();
  }

  @NotNull
  public RootEntry createTransientRootEntry() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    RootEntry root = new RootEntry();
    doCreateChildren(root, Arrays.asList(ManagingFS.getInstance().getLocalRoots()), false);
    return root;
  }

  @Nullable
  public Entry createTransientEntry(@NotNull VirtualFile file) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return doCreateEntry(file, false);
  }

  @Nullable
  public Entry createEntryForDeletion(@NotNull VirtualFile file) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return doCreateEntry(file, true);
  }

  @Nullable
  private Entry doCreateEntry(@NotNull VirtualFile file, boolean forDeletion) {
    if (!file.isDirectory()) {
      if (!isVersioned(file)) return null;

      Pair<StoredContent, Long> contentAndStamps;
      if (forDeletion) {
        FileDocumentManager m = FileDocumentManager.getInstance();
        Document d = m.isFileModified(file) ? m.getCachedDocument(file) : null; // should not try to load document
        contentAndStamps = acquireAndClearCurrentContent(file, d);
      }
      else {
        contentAndStamps = getActualContentNoAcquire(file);
      }
      return new FileEntry(file.getName(), contentAndStamps.first, contentAndStamps.second, !file.isWritable());
    }
    DirectoryEntry newDir = new DirectoryEntry(file.getName());
    doCreateChildren(newDir, iterateDBChildren(file), forDeletion);
    if (!isVersioned(file) && newDir.getChildren().isEmpty()) return null;
    return newDir;
  }

  private void doCreateChildren(@NotNull DirectoryEntry parent, Collection<VirtualFile> children, final boolean forDeletion) {
    List<Entry> entries = ContainerUtil.mapNotNull(children, new NullableFunction<VirtualFile, Entry>() {
      @Override
      public Entry fun(@NotNull VirtualFile each) {
        return doCreateEntry(each, forDeletion);
      }
    });
    parent.addChildren(entries);
  }

  public void registerUnsavedDocuments(@NotNull final LocalHistoryFacade vcs) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        vcs.beginChangeSet();
        for (Document d : FileDocumentManager.getInstance().getUnsavedDocuments()) {
          VirtualFile f = getFile(d);
          if (!shouldRegisterDocument(f)) continue;
          registerDocumentContents(vcs, f, d);
        }
        vcs.endChangeSet(null);
      }
    });
  }

  private boolean shouldRegisterDocument(@Nullable VirtualFile f) {
    return f != null && f.isValid() && areContentChangesVersioned(f);
  }

  private void registerDocumentContents(@NotNull LocalHistoryFacade vcs, @NotNull VirtualFile f, Document d) {
    Pair<StoredContent, Long> contentAndStamp = acquireAndUpdateActualContent(f, d);
    if (contentAndStamp != null) {
      vcs.contentChanged(f.getPath(), contentAndStamp.first, contentAndStamp.second);
    }
  }

  // returns null is content has not been changes since last time
  @Nullable
  public Pair<StoredContent, Long> acquireAndUpdateActualContent(@NotNull VirtualFile f, @Nullable Document d) {
    ContentAndTimestamps contentAndStamp = f.getUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY);
    if (contentAndStamp == null) {
      if (d != null) saveDocumentContent(f, d);
      return Pair.create(StoredContent.acquireContent(f), f.getTimeStamp());
    }

    // if no need to save current document content when simply return and clear stored one
    if (d == null) {
      f.putUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY, null);
      return Pair.create(contentAndStamp.content, contentAndStamp.registeredTimestamp);
    }

    // if the stored content equals the current one, do not store it and return null
    if (d.getModificationStamp() == contentAndStamp.documentModificationStamp) return null;

    // is current content has been changed, store it and return the previous one
    saveDocumentContent(f, d);
    return Pair.create(contentAndStamp.content, contentAndStamp.registeredTimestamp);
  }

  private static void saveDocumentContent(@NotNull VirtualFile f, @NotNull Document d) {
    f.putUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY,
                  new ContentAndTimestamps(Clock.getTime(),
                                           StoredContent.acquireContent(bytesFromDocument(d)),
                                           d.getModificationStamp()));
  }

  @NotNull
  public Pair<StoredContent, Long> acquireAndClearCurrentContent(@NotNull VirtualFile f, @Nullable Document d) {
    ContentAndTimestamps contentAndStamp = f.getUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY);
    f.putUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY, null);

    if (d != null && contentAndStamp != null) {
      // if previously stored content was not changed, return it
      if (d.getModificationStamp() == contentAndStamp.documentModificationStamp) {
        return Pair.create(contentAndStamp.content, contentAndStamp.registeredTimestamp);
      }
    }

    // release previously stored
    if (contentAndStamp != null) {
      contentAndStamp.content.release();
    }

    // take document's content if any
    if (d != null) {
      return Pair.create(StoredContent.acquireContent(bytesFromDocument(d)), Clock.getTime());
    }

    return Pair.create(StoredContent.acquireContent(f), f.getTimeStamp());
  }

  @NotNull
  private static Pair<StoredContent, Long> getActualContentNoAcquire(@NotNull VirtualFile f) {
    ContentAndTimestamps result = f.getUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY);
    if (result == null) {
      return Pair.create(StoredContent.transientContent(f), f.getTimeStamp());
    }
    return Pair.create(result.content, result.registeredTimestamp);
  }

  private static byte[] bytesFromDocument(@NotNull Document d) {
    try {
      return d.getText().getBytes(getFile(d).getCharset().name());
    }
    catch (UnsupportedEncodingException e) {
      return d.getText().getBytes();
    }
  }

  public String stringFromBytes(@NotNull byte[] bytes, @NotNull String path) {
    try {
      VirtualFile file = findVirtualFile(path);
      if (file == null) {
        return CharsetToolkit.bytesToString(bytes);
      }
      return new String(bytes, file.getCharset().name());
    }
    catch (UnsupportedEncodingException e1) {
      return new String(bytes);
    }
  }

  public void saveAllUnsavedDocuments() {
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  @Nullable
  private static VirtualFile getFile(@NotNull Document d) {
    return FileDocumentManager.getInstance().getFile(d);
  }

  @Nullable
  public Document getDocument(@NotNull String path) {
    return FileDocumentManager.getInstance().getDocument(findVirtualFile(path));
  }

  @NotNull
  public FileType getFileType(@NotNull String fileName) {
    return FileTypeManager.getInstance().getFileTypeByFileName(fileName);
  }

  private static class ContentAndTimestamps {
    long registeredTimestamp;
    StoredContent content;
    long documentModificationStamp;

    private ContentAndTimestamps(long registeredTimestamp, StoredContent content, long documentModificationStamp) {
      this.registeredTimestamp = registeredTimestamp;
      this.content = content;
      this.documentModificationStamp = documentModificationStamp;
    }
  }
}
