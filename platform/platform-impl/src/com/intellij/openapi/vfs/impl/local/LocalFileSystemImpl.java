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
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.concurrency.JBLock;
import com.intellij.util.concurrency.JBReentrantReadWriteLock;
import com.intellij.util.concurrency.LockFactory;
import com.intellij.util.containers.HashSet;
import com.intellij.util.io.SafeFileOutputStream;
import com.intellij.util.io.fs.IFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.util.*;

public final class LocalFileSystemImpl extends LocalFileSystem implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl");

  private final JBReentrantReadWriteLock LOCK = LockFactory.createReadWriteLock();
  final JBLock READ_LOCK = LOCK.readLock();
  final JBLock WRITE_LOCK = LOCK.writeLock();

  private final List<WatchRequest> myRootsToWatch = new ArrayList<WatchRequest>();
  private WatchRequest[] myCachedNormalizedRequests = null;

  private final List<LocalFileOperationsHandler> myHandlers = new ArrayList<LocalFileOperationsHandler>();
  private final FileWatcher myWatcher;

  private static class WatchRequestImpl implements WatchRequest {
    public final String myRootPath;

    public String myFSRootPath;
    public final boolean myToWatchRecursively;

    public WatchRequestImpl(String rootPath, final boolean toWatchRecursively) {
      myToWatchRecursively = toWatchRecursively;
      final int index = rootPath.indexOf(JarFileSystem.JAR_SEPARATOR);
      if (index >= 0) rootPath = rootPath.substring(0, index);
      final File file = new File(rootPath.replace('/', File.separatorChar));
      if (!file.isDirectory()) {
        final File parentFile = file.getParentFile();
        if (parentFile != null) {
          if (SystemInfo.isFileSystemCaseSensitive) {
            myFSRootPath = parentFile.getAbsolutePath(); // fixes problem with symlinks under Unix (however does not under Windows!)
          }
          else {
            try {
              myFSRootPath = parentFile.getCanonicalPath();
            }
            catch (IOException e) {
              myFSRootPath = rootPath; //need something
            }
          }
        }
        else {
          myFSRootPath = rootPath.replace('/', File.separatorChar);
        }

        myRootPath = myFSRootPath.replace(File.separatorChar, '/');
      }
      else {
        myRootPath = rootPath.replace(File.separatorChar, '/');
        myFSRootPath = rootPath.replace('/', File.separatorChar);
      }
    }

    @NotNull
    public String getRootPath() {
      return myRootPath;
    }

    @NotNull
    public String getFileSystemRootPath() {
      return myFSRootPath;
    }

    public boolean isToWatchRecursively() {
      return myToWatchRecursively;
    }

    public boolean dominates(WatchRequest other) {
      if (myToWatchRecursively) {
        return other.getRootPath().startsWith(myRootPath);
      }

      return !other.isToWatchRecursively() && myRootPath.equals(other.getRootPath());
    }
  }

  public LocalFileSystemImpl() {
    myWatcher = FileWatcher.getInstance();
    if (myWatcher.isOperational()) {
      new StoreRefreshStatusThread().start();
    }
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  @TestOnly
  public void cleanupForNextTest() throws IOException {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
        refresh(false);
      }
    });
    ((PersistentFS)ManagingFS.getInstance()).clearIdCache();
    
    final VirtualFile[] roots = ManagingFS.getInstance().getRoots(this);
    for (VirtualFile root : roots) {
      if (root instanceof VirtualDirectoryImpl) {
        final VirtualDirectoryImpl directory = (VirtualDirectoryImpl)root;
        directory.cleanupCachedChildren();
      }
    }

    myRootsToWatch.clear();

    final File file = new File(FileUtil.getTempDirectory());
    String path = file.getCanonicalPath().replace(File.separatorChar, '/');
    addRootToWatch(path, true);
  }

  @NotNull
  public String getProtocol() {
    return PROTOCOL;
  }

  @Nullable
  public VirtualFile findFileByPath(@NotNull String path) {
    /*
    if (File.separatorChar == '\\') {
      if (path.indexOf('\\') >= 0) return null;
    }
    */

    String canonicalPath = getVfsCanonicalPath(path);
    if (canonicalPath == null) return null;
    return super.findFileByPath(canonicalPath);
  }

  public VirtualFile findFileByPathIfCached(@NotNull String path) {
    String canonicalPath = getVfsCanonicalPath(path);
    if (canonicalPath == null) return null;
    return super.findFileByPathIfCached(canonicalPath);
  }

  @Nullable
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    String canonicalPath = getVfsCanonicalPath(path);
    if (canonicalPath == null) return null;
    return super.refreshAndFindFileByPath(canonicalPath);
  }

  @Nullable
  public String normalize(final String path) {
    return getVfsCanonicalPath(path);
  }

  public VirtualFile findFileByIoFile(File file) {
    String path = file.getAbsolutePath();
    if (path == null) return null;
    return findFileByPath(path.replace(File.separatorChar, '/'));
  }

  @Nullable
  public VirtualFile findFileByIoFile(final IFile file) {
    String path = file.getPath();
    if (path == null) return null;
    return findFileByPath(path.replace(File.separatorChar, '/'));
  }

  public VirtualFile refreshAndFindFileByIoFile(File file) {
    String path = file.getAbsolutePath();
    if (path == null) return null;
    return refreshAndFindFileByPath(path.replace(File.separatorChar, '/'));
  }

  @Nullable
  public VirtualFile refreshAndFindFileByIoFile(final IFile ioFile) {
    String path = ioFile.getPath();
    if (path == null) return null;
    return refreshAndFindFileByPath(path.replace(File.separatorChar, '/'));
  }

  public void refreshIoFiles(Iterable<File> files) {
    final VirtualFileManagerEx manager = (VirtualFileManagerEx)VirtualFileManager.getInstance();

    Application app = ApplicationManager.getApplication();
    boolean fireCommonRefreshSession = app.isDispatchThread() || app.isWriteAccessAllowed();
    if (fireCommonRefreshSession) manager.fireBeforeRefreshStart(false);

    try {
      List<VirtualFile> filesToRefresh = new ArrayList<VirtualFile>();

      for (File file : files) {
        final VirtualFile virtualFile = refreshAndFindFileByIoFile(file);
        if (virtualFile != null) {
          filesToRefresh.add(virtualFile);
        }
      }

      RefreshQueue.getInstance().refresh(false, false, null, filesToRefresh.toArray(new VirtualFile[filesToRefresh.size()]));
    }
    finally {
      if (fireCommonRefreshSession) manager.fireAfterRefreshFinish(false);
    }
  }

  public void refreshFiles(Iterable<VirtualFile> files) {
    refreshFiles(files, false, false);
  }

  private static void refreshFiles(final Iterable<VirtualFile> files, final boolean recursive, final boolean async) {
    List<VirtualFile> list = new ArrayList<VirtualFile>();
    for (VirtualFile file : files) {
      list.add(file);
    }

    RefreshQueue.getInstance().refresh(async, recursive, null, list.toArray(new VirtualFile[list.size()]));
  }

  public byte[] physicalContentsToByteArray(final VirtualFile virtualFile) throws IOException {
    return virtualFile.contentsToByteArray();
  }

  public long physicalLength(final VirtualFile virtualFile) {
    return virtualFile.getLength();
  }

  private WatchRequest[] normalizeRootsForRefresh() {
    if (myCachedNormalizedRequests != null) return myCachedNormalizedRequests;
    List<WatchRequest> result = new ArrayList<WatchRequest>();
    WRITE_LOCK.lock();
    try {
      NextRoot:
      for (WatchRequest request : myRootsToWatch) {
        String rootPath = request.getRootPath();
        boolean recursively = request.isToWatchRecursively();

        for (Iterator<WatchRequest> iterator1 = result.iterator(); iterator1.hasNext();) {
          final WatchRequest otherRequest = iterator1.next();
          final String otherRootPath = otherRequest.getRootPath();
          final boolean otherRecursively = otherRequest.isToWatchRecursively();
          if ((rootPath.equals(otherRootPath) && (!recursively || otherRecursively)) ||
              (FileUtil.startsWith(rootPath, otherRootPath) && otherRecursively)) {
            continue NextRoot;
          }
          else if (FileUtil.startsWith(otherRootPath, rootPath) && (recursively || !otherRecursively)) {
            iterator1.remove();
          }
        }
        result.add(request);
      }
    }
    finally {
      WRITE_LOCK.unlock();
    }

    myCachedNormalizedRequests = result.toArray(new WatchRequest[result.size()]);
    return myCachedNormalizedRequests;
  }

  private void storeRefreshStatusToFiles() {
    if (FileWatcher.getInstance().isOperational()) {
      // TODO: different ways to marky dirty for all these cases
      markPathsDirty(FileWatcher.getInstance().getDirtyPaths());
      markFlatDirsDirty(FileWatcher.getInstance().getDirtyDirs());
      markRecursiveDirsDirty(FileWatcher.getInstance().getDirtyRecursivePaths());
    }
  }

  private void markPathsDirty(final List<String> dirtyFiles) {
    for (String dirtyFile : dirtyFiles) {
      String path = dirtyFile.replace(File.separatorChar, '/');
      VirtualFile file = findFileByPathIfCached(path);
      if (file instanceof NewVirtualFile) {
        ((NewVirtualFile)file).markDirty();
      }
    }
  }

  private void markFlatDirsDirty(final List<String> dirtyFiles) {
    for (String dirtyFile : dirtyFiles) {
      String path = dirtyFile.replace(File.separatorChar, '/');
      VirtualFile file = findFileByPathIfCached(path);
      if (file instanceof NewVirtualFile) {
        final NewVirtualFile nvf = (NewVirtualFile)file;
        nvf.markDirty();
        for (VirtualFile child : nvf.getCachedChildren()) {
          ((NewVirtualFile)child).markDirty();
        }
      }
    }
  }

  private void markRecursiveDirsDirty(final List<String> dirtyFiles) {
    for (String dirtyFile : dirtyFiles) {
      String path = dirtyFile.replace(File.separatorChar, '/');
      VirtualFile file = findFileByPathIfCached(path);
      if (file instanceof NewVirtualFile) {
        ((NewVirtualFile)file).markDirtyRecursively();
      }
    }
  }

  public void markSuspicousFilesDirty(List<VirtualFile> files) {
    storeRefreshStatusToFiles();

    if (myWatcher.isOperational()) {
      for (String root : myWatcher.getManualWatchRoots()) {
        final VirtualFile suspicousRoot = findFileByPathIfCached(root);
        if (suspicousRoot != null) {
          ((NewVirtualFile)suspicousRoot).markDirtyRecursively();
        }
      }
    }
    else {
      for (VirtualFile file : files) {
        if (file.getFileSystem() == this) {
          ((NewVirtualFile)file).markDirtyRecursively();
        }
      }
    }
  }

  @Nullable
  private static String getVfsCanonicalPath(@NotNull String path) {
    if (path.length() == 0) {
      try {
        return new File("").getCanonicalPath();
      }
      catch (IOException e) {
        return path;
      }
    }

    if (SystemInfo.isWindows) {
      if (path.startsWith("//") || path.startsWith("\\\\")) {
        return path;
      }
      
      if (path.charAt(0) == '/') path = path.substring(1); //hack over new File(path).toUrl().getFile()
      if (path.contains("~")) {
        try {
          return new File(path.replace('/', File.separatorChar)).getCanonicalPath().replace(File.separatorChar, '/');
        }
        catch (IOException e) {
          return null;
        }
      }
    }
    else {
      if (!path.startsWith("/")) {
        path = new File(path).getAbsolutePath();
      }
    }

    
    return path.replace(File.separatorChar, '/');
  }

  private void setUpFileWatcher() {
    final Application application = ApplicationManager.getApplication();

    if (application.isDisposeInProgress()) return;

    if (myWatcher.isOperational()) {
      application.runReadAction(new Runnable() {
        public void run() {
          WRITE_LOCK.lock();
          try {
            final WatchRequest[] watchRequests = normalizeRootsForRefresh();
            List<String> myRecursiveRoots = new ArrayList<String>();
            List<String> myFlatRoots = new ArrayList<String>();

            for (WatchRequest root : watchRequests) {
              if (root.isToWatchRecursively()) {
                myRecursiveRoots.add(root.getFileSystemRootPath());
              }
              else {
                myFlatRoots.add(root.getFileSystemRootPath());
              }
            }

            myWatcher.setWatchRoots(myRecursiveRoots, myFlatRoots);
          }
          finally {
            WRITE_LOCK.unlock();
          }
        }
      });
    }
  }

  private class StoreRefreshStatusThread extends Thread {
    private static final long PERIOD = 1000;

    public StoreRefreshStatusThread() {
      //noinspection HardCodedStringLiteral
      super("StoreRefreshStatusThread");
      setPriority(MIN_PRIORITY);
      setDaemon(true);
    }

    public void run() {
      while (true) {
        final Application application = ApplicationManager.getApplication();
        if (application == null || application.isDisposed()) break;
        
        storeRefreshStatusToFiles();
        try {
          sleep(PERIOD);
        }
        catch (InterruptedException e) {
          //normal situation
        }
      }
    }
  }

  @NotNull
  public String getComponentName() {
    return "LocalFileSystem";
  }

  public WatchRequest addRootToWatch(@NotNull String rootPath, boolean toWatchRecursively) {
    if (rootPath.length() == 0) return null;

    WRITE_LOCK.lock();
    try {
      final WatchRequestImpl result = new WatchRequestImpl(rootPath, toWatchRecursively);
      final VirtualFile existingFile = findFileByPathIfCached(rootPath);
      if (existingFile != null) {
        if (!isAlreadyWatched(result)) {
          existingFile.refresh(true, toWatchRecursively);
          if (existingFile.isDirectory() && !toWatchRecursively && existingFile instanceof NewVirtualFile) {
            for (VirtualFile child : ((NewVirtualFile)existingFile).getCachedChildren()) {
              child.refresh(true, false);
            }
          }
        }
      }
      myRootsToWatch.add(result);
      myCachedNormalizedRequests = null;
      setUpFileWatcher();
      return result;
    }
    finally {
      WRITE_LOCK.unlock();
    }
  }

  private boolean isAlreadyWatched(final WatchRequest request) {
    for (final WatchRequest current : normalizeRootsForRefresh()) {
      if (current.dominates(request)) return true;
    }
    return false;
  }

  @NotNull
  public Set<WatchRequest> addRootsToWatch(@NotNull final Collection<String> rootPaths, final boolean toWatchRecursively) {
    Set<WatchRequest> result = new HashSet<WatchRequest>();
    Set<VirtualFile> filesToSynchronize = new HashSet<VirtualFile>();

    WRITE_LOCK.lock();
    try {
      for (String rootPath : rootPaths) {
        LOG.assertTrue(rootPath != null);
        if (rootPath.length() > 0) {
          final WatchRequestImpl request = new WatchRequestImpl(rootPath, toWatchRecursively);
          final VirtualFile existingFile = findFileByPathIfCached(rootPath);
          if (existingFile != null) {
            if (!isAlreadyWatched(request)) {
              filesToSynchronize.add(existingFile);
            }
          }
          result.add(request);
          myRootsToWatch.add(request); //add in any case, safe to add inplace without copying myRootsToWatch before the loop
        }
      }
      myCachedNormalizedRequests = null;
      setUpFileWatcher();
    }
    finally {
      WRITE_LOCK.unlock();
    }

    if (!ApplicationManager.getApplication().isUnitTestMode() && !filesToSynchronize.isEmpty()) {
      refreshFiles(filesToSynchronize, toWatchRecursively, true);
    }

    return result;
  }

  public void removeWatchedRoot(@NotNull final WatchRequest watchRequest) {
    WRITE_LOCK.lock();
    try {
      if (myRootsToWatch.remove(watchRequest)) {
        myCachedNormalizedRequests = null;
        setUpFileWatcher();
      }
    }
    finally {
      WRITE_LOCK.unlock();
    }
  }

  public void registerAuxiliaryFileOperationsHandler(LocalFileOperationsHandler handler) {
    if (myHandlers.contains(handler)) {
      LOG.error("Handler " + handler + " already registered.");
    }
    myHandlers.add(handler);
  }

  public void unregisterAuxiliaryFileOperationsHandler(LocalFileOperationsHandler handler) {
    if (!myHandlers.remove(handler)) {
      LOG.error("Handler" + handler + " haven't been registered or already unregistered.");
    }
  }

  public boolean processCachedFilesInSubtree(final VirtualFile file, Processor<VirtualFile> processor) {
    if (file.getFileSystem() != this) return true;

    return processFile((NewVirtualFile)file, processor);
  }

  private static boolean processFile(NewVirtualFile file, Processor<VirtualFile> processor) {
    if (!processor.process(file)) return false;
    if (file.isDirectory()) {
      for (final VirtualFile child : file.getCachedChildren()) {
        if (!processFile((NewVirtualFile)child, processor)) return false;
      }
    }
    return true;
  }

  private boolean auxDelete(VirtualFile file) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.delete(file)) return true;
    }

    return false;
  }

  private boolean auxMove(VirtualFile file, VirtualFile toDir) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.move(file, toDir)) return true;
    }
    return false;
  }

  private void auxNotifyCompleted(final ThrowableConsumer<LocalFileOperationsHandler, IOException> consumer) {
    for (LocalFileOperationsHandler handler : myHandlers) {
      handler.afterDone(consumer);
    }
  }

  @Nullable
  private File auxCopy(VirtualFile file, VirtualFile toDir, final String copyName) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      final File copy = handler.copy(file, toDir, copyName);
      if (copy != null) return copy;
    }
    return null;
  }

  private boolean auxRename(VirtualFile file, String newName) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.rename(file, newName)) return true;
    }
    return false;
  }

  private boolean auxCreateFile(VirtualFile dir, String name) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.createFile(dir, name)) return true;
    }
    return false;
  }

  private boolean auxCreateDirectory(VirtualFile dir, String name) throws IOException {
    for (LocalFileOperationsHandler handler : myHandlers) {
      if (handler.createDirectory(dir, name)) return true;
    }
    return false;
  }

  public void removeWatchedRoots(@NotNull final Collection<WatchRequest> rootsToWatch) {
    WRITE_LOCK.lock();
    try {
      if (myRootsToWatch.removeAll(rootsToWatch)) {
        myCachedNormalizedRequests = null;
        setUpFileWatcher();
      }
    }
    finally {
      WRITE_LOCK.unlock();
    }
  }

  private static void delete(File physicalFile) throws IOException {
    File[] list = physicalFile.listFiles();
    if (list != null) {
      for (File aList : list) {
        delete(aList);
      }
    }
    if (!physicalFile.delete()) {
      throw new IOException(VfsBundle.message("file.delete.error", physicalFile.getPath()));
    }
  }

  public boolean isReadOnly() {
    return false;
  }

  @SuppressWarnings({"NonConstantStringShouldBeStringBuffer"})
  private static File convertToIOFile(VirtualFile file) {
    String path = file.getPath();
    if (path.endsWith(":") && path.length() == 2 && (SystemInfo.isWindows || SystemInfo.isOS2)) {
      path += "/"; // Make 'c:' resolve to a root directory for drive c:, not the current directory on that drive
    }

    return new File(path);
  }

  public VirtualFile createChildDirectory(final Object requestor, @NotNull final VirtualFile parent, @NotNull final String dir) throws IOException {
    final File ioDir = new File(convertToIOFile(parent), dir);
    final boolean succ = auxCreateDirectory(parent, dir) || ioDir.mkdirs();
    auxNotifyCompleted(new ThrowableConsumer<LocalFileOperationsHandler, IOException>() {
      public void consume(LocalFileOperationsHandler handler) throws IOException {
        handler.createDirectory(parent, dir);
      }
    });
    if (!succ) {
      throw new IOException("Failed to create directory: " + ioDir.getPath());
    }

    return new FakeVirtualFile(parent, dir);
  }

  public VirtualFile createChildFile(final Object requestor, @NotNull final VirtualFile parent, @NotNull final String file) throws IOException {
    final File ioFile = new File(convertToIOFile(parent), file);
    final boolean succ = auxCreateFile(parent, file) || FileUtil.createIfDoesntExist(ioFile);
    auxNotifyCompleted(new ThrowableConsumer<LocalFileOperationsHandler, IOException>() {
      public void consume(LocalFileOperationsHandler handler) throws IOException {
        handler.createFile(parent, file);
      }
    });
    if (!succ) {
      throw new IOException("Failed to create child file at " + ioFile.getPath());
    }

    return new FakeVirtualFile(parent, file);
  }

  public void deleteFile(final Object requestor, @NotNull final VirtualFile file) throws IOException {
    if (!auxDelete(file)) {
      delete(convertToIOFile(file));
    }
    auxNotifyCompleted(new ThrowableConsumer<LocalFileOperationsHandler, IOException>() {
      public void consume(LocalFileOperationsHandler handler) throws IOException {
        handler.delete(file);
      }
    });
  }

  public boolean exists(final VirtualFile fileOrDirectory) {
    if (fileOrDirectory.getParent() == null) return true;
    return convertToIOFile(fileOrDirectory).exists();
  }

  public long getLength(final VirtualFile file) {
    return convertToIOFile(file).length();
  }

  public boolean isCaseSensitive() {
    return SystemInfo.isFileSystemCaseSensitive;
  }

  @NotNull
  public InputStream getInputStream(final VirtualFile file) throws FileNotFoundException {
    return new BufferedInputStream(new FileInputStream(convertToIOFile(file)));
  }

  @NotNull
  public byte[] contentsToByteArray(final VirtualFile file) throws IOException {
    return FileUtil.loadFileBytes(convertToIOFile(file));
  }

  public long getTimeStamp(final VirtualFile file) {
    return convertToIOFile(file).lastModified();
  }

  @NotNull
  public OutputStream getOutputStream(final VirtualFile file, final Object requestor, final long modStamp, final long timeStamp) throws FileNotFoundException {
    final File ioFile = convertToIOFile(file);
    final OutputStream stream = shallUseSafeStream(requestor, ioFile) ? new SafeFileOutputStream(ioFile) : new FileOutputStream(ioFile);
    return new BufferedOutputStream(stream) {
      public void close() throws IOException {
        super.close();
        if (timeStamp > 0) {
          ioFile.setLastModified(timeStamp);
        }
      }
    };
  }

  private static boolean shallUseSafeStream(Object requestor, File file) {
    return requestor instanceof SafeWriteRequestor && FileUtil.canCallCanExecute() && !FileUtil.canExecute(file);
  }

  public boolean isDirectory(final VirtualFile file) {
    return convertToIOFile(file).isDirectory();
  }

  public boolean isWritable(final VirtualFile file) {
    return convertToIOFile(file).canWrite();
  }

  public String[] list(final VirtualFile file) {
    if (file.getParent() == null) {
      final File[] roots = File.listRoots();
      if (roots.length == 1 && roots[0].getName().length() == 0) {
        return roots[0].list();
      }
      if ("".equals(file.getName())) {
        // return drive letter names for the 'fake' root on windows
        final String[] names = new String[roots.length];
        for (int i = 0; i < names.length; i++) {
          String name = roots[i].getPath();
          if (name.endsWith(File.separator)) {
            name = name.substring(0, name.length() - File.separator.length());
          }
          names[i] = name;        
        }
        return names;
      }
    }
    final String[] names = convertToIOFile(file).list();
    return names != null ? names : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public void moveFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final VirtualFile newParent) throws IOException {
    if (!auxMove(file, newParent)) {
      final File ioFrom = convertToIOFile(file);
      final File ioParent = convertToIOFile(newParent);
      ioFrom.renameTo(new File(ioParent, file.getName()));
    }
    auxNotifyCompleted(new ThrowableConsumer<LocalFileOperationsHandler, IOException>() {
      public void consume(LocalFileOperationsHandler handler) throws IOException {
        handler.move(file, newParent);
      }
    });
  }

  public void renameFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final String newName) throws IOException {
    if (!file.exists()) {
      throw new IOException("File to move does not exist: " + file.getPath());
    }

    final VirtualFile parent = file.getParent();
    assert parent != null;

    if (!auxRename(file, newName)) {
      if (!convertToIOFile(file).renameTo(new File(convertToIOFile(parent), newName))) {
        throw new IOException("Destination already exists: " + parent.getPath() + "/" + newName);
      }
    }
    auxNotifyCompleted(new ThrowableConsumer<LocalFileOperationsHandler, IOException>() {
      public void consume(LocalFileOperationsHandler handler) throws IOException {
        handler.rename(file, newName);
      }
    });
  }

  public VirtualFile copyFile(final Object requestor, @NotNull final VirtualFile vFile, @NotNull final VirtualFile newParent, @NotNull final String copyName)
    throws IOException {
    File physicalCopy = auxCopy(vFile, newParent, copyName);

    try {
      if (physicalCopy == null) {
        File physicalFile = convertToIOFile(vFile);

        File newPhysicalParent = convertToIOFile(newParent);
        physicalCopy = new File(newPhysicalParent, copyName);

        try {
          if (physicalFile.isDirectory()) {
            FileUtil.copyDir(physicalFile, physicalCopy);
          }
          else {
            FileUtil.copy(physicalFile, physicalCopy);
          }
        }
        catch (IOException e) {
          FileUtil.delete(physicalCopy);
          throw e;
        }
      }
    } finally {
      auxNotifyCompleted(new ThrowableConsumer<LocalFileOperationsHandler, IOException>() {
        public void consume(LocalFileOperationsHandler handler) throws IOException {
          handler.copy(vFile, newParent, copyName);
        }
      });
    }
    return new FakeVirtualFile(newParent, copyName);
  }

  public void setTimeStamp(final VirtualFile file, final long modstamp) {
    convertToIOFile(file).setLastModified(modstamp);
  }

  public void setWritable(final VirtualFile file, final boolean writableFlag) throws IOException {
    FileUtil.setReadOnlyAttribute(file.getPath(), !writableFlag);
    final File ioFile = convertToIOFile(file);
    if (ioFile.canWrite() != writableFlag) {
      throw new IOException("Failed to change read-only flag for " + ioFile.getPath());
    }
  }

  @NonNls
  public String toString() {
    return "LocalFileSystem";
  }

  protected String extractRootPath(@NotNull final String path) {
    if (path.length() == 0) {
      try {
        return extractRootPath(new File("").getCanonicalPath());
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    if (SystemInfo.isWindows) {
      if (path.length() >= 2 && path.charAt(1) == ':') {
        // Drive letter
        return path.substring(0, 2).toUpperCase(Locale.US);
      }

      if (path.startsWith("//") || path.startsWith("\\\\")) {
        // UNC. Must skip exactly two path elements like [\\ServerName\ShareName]\pathOnShare\file.txt
        // Root path is in square brackets here.

        int slashCount = 0;
        int idx;
        for (idx = 2; idx < path.length() && slashCount < 2; idx++) {
          final char c = path.charAt(idx);
          if (c == '\\' || c == '/') {
            slashCount++;
            idx--;
          }
        }

        return path.substring(0, idx);
      }

      return "";
    }

    return path.startsWith("/") ? "/" : "";
  }

  public int getRank() {
    return 1;
  }


  public void refreshWithoutFileWatcher(final boolean asynchronous) {
    Runnable heavyRefresh = new Runnable() {
      public void run() {
        for (VirtualFile root : ManagingFS.getInstance().getRoots(LocalFileSystemImpl.this)) {
          ((NewVirtualFile)root).markDirtyRecursively();
        }

        refresh(asynchronous);
      }
    };

    if (asynchronous && myWatcher.isOperational()) {
      RefreshQueue.getInstance().refresh(true, true, heavyRefresh, ManagingFS.getInstance().getRoots(this));
    }
    else {
      heavyRefresh.run();
    }
  }

  public boolean markNewFilesAsDirty() {
    return true;
  }

  public String getCanonicallyCasedName(final VirtualFile file) {
    if (isCaseSensitive()) {
      return super.getCanonicallyCasedName(file);
    }

    try {
      return convertToIOFile(file).getCanonicalFile().getName();
    }
    catch (IOException e) {
      return file.getName();
    }
  }
}
