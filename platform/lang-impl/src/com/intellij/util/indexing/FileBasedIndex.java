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

package com.intellij.util.indexing;

import com.intellij.AppTopics;
import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.caches.CacheUpdater;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.*;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.NullVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLock;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.PsiDocumentTransactionListener;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.JBLock;
import com.intellij.util.concurrency.JBReentrantReadWriteLock;
import com.intellij.util.concurrency.LockFactory;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.*;
import com.intellij.util.io.*;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 20, 2007
 */

public class FileBasedIndex implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.FileBasedIndex");
  @NonNls
  private static final String CORRUPTION_MARKER_NAME = "corruption.marker";
  private final Map<ID<?, ?>, Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>> myIndices = new HashMap<ID<?, ?>, Pair<UpdatableIndex<?, ?, FileContent>, InputFilter>>();
  private final Map<ID<?, ?>, Semaphore> myUnsavedDataIndexingSemaphores = new HashMap<ID<?,?>, Semaphore>();
  private final TObjectIntHashMap<ID<?, ?>> myIndexIdToVersionMap = new TObjectIntHashMap<ID<?, ?>>();
  private final Set<ID<?, ?>> myNotRequiringContentIndices = new HashSet<ID<?, ?>>();
  private final Set<FileType> myNoLimitCheckTypes = new HashSet<FileType>();

  private final PerIndexDocumentMap<Long> myLastIndexedDocStamps = new PerIndexDocumentMap<Long>() {
    @Override
    protected Long createDefault(Document document) {
      return 0L;
    }
  };

  private final ChangedFilesCollector myChangedFilesCollector;

  private final List<IndexableFileSet> myIndexableSets = ContainerUtil.createEmptyCOWList();

  public static final int OK = 1;
  public static final int REQUIRES_REBUILD = 2;
  public static final int REBUILD_IN_PROGRESS = 3;
  private final Map<ID<?, ?>, AtomicInteger> myRebuildStatus = new HashMap<ID<?,?>, AtomicInteger>();

  private final VirtualFileManagerEx myVfManager;
  private final FileDocumentManager myFileDocumentManager;
  private final ConcurrentHashSet<ID<?, ?>> myUpToDateIndices = new ConcurrentHashSet<ID<?, ?>>();
  private final Map<Document, PsiFile> myTransactionMap = new HashMap<Document, PsiFile>();

  private static final int ALREADY_PROCESSED = 0x02;
  private static final String USE_MULTITHREADED_INDEXING = "fileIndex.multithreaded";
  private @Nullable String myConfigPath;
  private @Nullable String mySystemPath;
  private final boolean myIsUnitTestMode;

  public void requestReindex(final VirtualFile file) {
    myChangedFilesCollector.invalidateIndices(file, true);
  }

  public interface InputFilter {
    boolean acceptInput(VirtualFile file);
  }

  public FileBasedIndex(final VirtualFileManagerEx vfManager, FileDocumentManager fdm, MessageBus bus) throws IOException {
    myVfManager = vfManager;
    myFileDocumentManager = fdm;
    myIsUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    myConfigPath = calcConfigPath(PathManager.getConfigPath());
    mySystemPath = calcConfigPath(PathManager.getSystemPath());

    final MessageBusConnection connection = bus.connect();
    connection.subscribe(PsiDocumentTransactionListener.TOPIC, new PsiDocumentTransactionListener() {
      public void transactionStarted(final Document doc, final PsiFile file) {
        if (file != null) {
          myTransactionMap.put(doc, file);
          myUpToDateIndices.clear();
        }
      }

      public void transactionCompleted(final Document doc, final PsiFile file) {
        myTransactionMap.remove(doc);
      }
    });

    connection.subscribe(AppTopics.FILE_TYPES, new FileTypeListener() {
      private Map<FileType, Set<String>> myTypeToExtensionMap;
      public void beforeFileTypesChanged(final FileTypeEvent event) {
        cleanupProcessedFlag();
        myTypeToExtensionMap = new HashMap<FileType, Set<String>>();
        final FileTypeManager manager = event.getManager();
        for (FileType type : manager.getRegisteredFileTypes()) {
          myTypeToExtensionMap.put(type, getExtensions(manager, type));
        }
      }

      public void fileTypesChanged(final FileTypeEvent event) {
        final Map<FileType, Set<String>> oldExtensions = myTypeToExtensionMap;
        myTypeToExtensionMap = null;
        if (oldExtensions != null) {
          final FileTypeManager manager = event.getManager();
          final Map<FileType, Set<String>> newExtensions = new HashMap<FileType, Set<String>>();
          for (FileType type : manager.getRegisteredFileTypes()) {
            newExtensions.put(type, getExtensions(manager, type));
          }
          // we are interested only in extension changes or removals.
          // addition of an extension is handled separately by RootsChanged event
          if (!newExtensions.keySet().containsAll(oldExtensions.keySet())) {
            rebuildAllndices();
            return;
          }
          for (FileType type : oldExtensions.keySet()) {
            if (!newExtensions.get(type).containsAll(oldExtensions.get(type))) {
              rebuildAllndices();
              return;
            }
          }
        }
      }

      private Set<String> getExtensions(FileTypeManager manager, FileType type) {
        final Set<String> set = new HashSet<String>();
        for (FileNameMatcher matcher : manager.getAssociations(type)) {
          set.add(matcher.getPresentableString());
        }
        return set;
      }

      private void rebuildAllndices() {
        for (ID<?, ?> indexId : myIndices.keySet()) {
          requestRebuild(indexId);
        }
      }
    });

    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      public void before(List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (event.getRequestor() instanceof FileDocumentManager) {
            cleanupMemoryStorage();
            break;
          }
        }
      }

      public void after(List<? extends VFileEvent> events) {
      }
    });

    ApplicationManager.getApplication().addApplicationListener(new ApplicationAdapter() {
      public void writeActionStarted(Object action) {
        myUpToDateIndices.clear();
      }
    });

    final File workInProgressFile = getMarkerFile();
    if (workInProgressFile.exists()) {
      // previous IDEA session was closed incorrectly, so drop all indices
      FileUtil.delete(PathManager.getIndexRoot());
    }

    try {
      final FileBasedIndexExtension[] extensions = Extensions.getExtensions(FileBasedIndexExtension.EXTENSION_POINT_NAME);
      for (FileBasedIndexExtension<?, ?> extension : extensions) {
        myRebuildStatus.put(extension.getName(), new AtomicInteger(OK));
      }

      final File corruptionMarker = new File(PathManager.getIndexRoot(), CORRUPTION_MARKER_NAME);
      final boolean currentVersionCorrupted = corruptionMarker.exists();
      for (FileBasedIndexExtension<?, ?> extension : extensions) {
        registerIndexer(extension, currentVersionCorrupted);
      }
      FileUtil.delete(corruptionMarker);
      dropUnregisteredIndices();

      // check if rebuild was requested for any index during registration
      for (ID<?, ?> indexId : myIndices.keySet()) {
        if (myRebuildStatus.get(indexId).compareAndSet(REQUIRES_REBUILD, OK)) {
          try {
            clearIndex(indexId);
          }
          catch (StorageException e) {
            requestRebuild(indexId);
            LOG.error(e);
          }
        }
      }

      myChangedFilesCollector = new ChangedFilesCollector();
      vfManager.addVirtualFileListener(myChangedFilesCollector);

      registerIndexableSet(new AdditionalIndexableFileSet());
    }
    finally {
      ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
        public void run() {
          performShutdown();
        }
      });
      FileUtil.createIfDoesntExist(workInProgressFile);
      saveRegisteredIndices(myIndices.keySet());
    }
  }

  private static String calcConfigPath(final String path) {
    try {
      final String _path = FileUtil.toSystemIndependentName(new File(path).getCanonicalPath());
      return _path.endsWith("/")? _path : _path + "/" ;
    }
    catch (IOException e) {
      LOG.info(e);
      return null;
    }
  }

  private static FileBasedIndex ourInstance = CachedSingletonsRegistry.markCachedField(FileBasedIndex.class);
  public static FileBasedIndex getInstance() {
    if (ourInstance == null) {
      ourInstance = ApplicationManager.getApplication().getComponent(FileBasedIndex.class);
    }

    return ourInstance;
  }

  /**
   * @return true if registered index requires full rebuild for some reason, e.g. is just created or corrupted @param extension
   * @param isCurrentVersionCorrupted
   */
  private <K, V> void registerIndexer(final FileBasedIndexExtension<K, V> extension, final boolean isCurrentVersionCorrupted) throws IOException {
    final ID<K, V> name = extension.getName();
    final int version = extension.getVersion();
    if (!extension.dependsOnFileContent()) {
      myNotRequiringContentIndices.add(name);
    }
    myIndexIdToVersionMap.put(name, version);
    final File versionFile = IndexInfrastructure.getVersionFile(name);
    if (isCurrentVersionCorrupted || IndexInfrastructure.versionDiffers(versionFile, version)) {
      if (!isCurrentVersionCorrupted) {
        LOG.info("Version has changed for index " + extension.getName() + ". The index will be rebuilt.");
      }
      FileUtil.delete(IndexInfrastructure.getIndexRootDir(name));
      IndexInfrastructure.rewriteVersion(versionFile, version);
    }

    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        final MapIndexStorage<K, V> storage = new MapIndexStorage<K, V>(IndexInfrastructure.getStorageFile(name), extension.getKeyDescriptor(), extension.getValueExternalizer(), extension.getCacheSize());
        final IndexStorage<K, V> memStorage = new MemoryIndexStorage<K, V>(storage);
        final UpdatableIndex<K, V, FileContent> index = createIndex(name, extension, memStorage);
        myIndices.put(name, new Pair<UpdatableIndex<?,?, FileContent>, InputFilter>(index, new IndexableFilesFilter(extension.getInputFilter())));
        myUnsavedDataIndexingSemaphores.put(name, new Semaphore());
        myNoLimitCheckTypes.addAll(extension.getFileTypesWithSizeLimitNotApplicable());
        break;
      }
      catch (IOException e) {
        LOG.info(e);
        FileUtil.delete(IndexInfrastructure.getIndexRootDir(name));
        IndexInfrastructure.rewriteVersion(versionFile, version);
      }
    }
  }

  private static void saveRegisteredIndices(Collection<ID<?, ?>> ids) {
    final File file = getRegisteredIndicesFile();
    try {
      FileUtil.createIfDoesntExist(file);
      final DataOutputStream os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
      try {
        os.writeInt(ids.size());
        for (ID<?, ?> id : ids) {
          IOUtil.writeString(id.toString(), os);
        }
      }
      finally {
        os.close();
      }
    }
    catch (IOException ignored) {
    }
  }

  private static Set<String> readRegistsredIndexNames() {
    final Set<String> result = new HashSet<String>();
    try {
      final DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(getRegisteredIndicesFile())));
      try {
        final int size = in.readInt();
        for (int idx = 0; idx < size; idx++) {
          result.add(IOUtil.readString(in));
        }
      }
      finally {
        in.close();
      }
    }
    catch (IOException ignored) {
    }
    return result;
  }

  private static File getRegisteredIndicesFile() {
    return new File(PathManager.getIndexRoot(), "registered");
  }

  private static File getMarkerFile() {
    return new File(PathManager.getIndexRoot(), "work_in_progress");
  }

  private <K, V> UpdatableIndex<K, V, FileContent> createIndex(final ID<K, V> indexId, final FileBasedIndexExtension<K, V> extension, final IndexStorage<K, V> storage) throws IOException {
    final MapReduceIndex<K, V, FileContent> index;
    if (extension instanceof CustomImplementationFileBasedIndexExtension) {
      final UpdatableIndex<K, V, FileContent> custom =
        ((CustomImplementationFileBasedIndexExtension<K, V, FileContent>)extension).createIndexImplementation(indexId, this, storage);
      if (!(custom instanceof MapReduceIndex)) {
        return custom;
      }
      index = (MapReduceIndex<K,V, FileContent>)custom;
    }
    else {
      index = new MapReduceIndex<K, V, FileContent>(indexId, extension.getIndexer(), storage);
    }

    final KeyDescriptor<K> keyDescriptor = extension.getKeyDescriptor();
    index.setInputIdToDataKeysIndex(new Factory<PersistentHashMap<Integer, Collection<K>>>() {
      public PersistentHashMap<Integer, Collection<K>> create() {
        try {
          return createIdToDataKeysIndex(indexId, keyDescriptor);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });

    return index;
  }

  private static <K> PersistentHashMap<Integer, Collection<K>> createIdToDataKeysIndex(ID<K, ?> indexId, final KeyDescriptor<K> keyDescriptor) throws IOException {
    final File indexStorageFile = IndexInfrastructure.getInputIndexStorageFile(indexId);
    return new PersistentHashMap<Integer, Collection<K>>(indexStorageFile, new EnumeratorIntegerDescriptor(), new DataExternalizer<Collection<K>>() {
      public void save(DataOutput out, Collection<K> value) throws IOException {
        DataInputOutputUtil.writeINT(out, value.size());
        for (K key : value) {
          keyDescriptor.save(out, key);
        }
      }

      public Collection<K> read(DataInput in) throws IOException {
        final int size = DataInputOutputUtil.readINT(in);
        final List<K> list = new ArrayList<K>();
        for (int idx = 0; idx < size; idx++) {
          list.add(keyDescriptor.read(in));
        }
        return list;
      }
    });
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "FileBasedIndex";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    performShutdown();
  }

  private AtomicBoolean myShutdownPerformed = new AtomicBoolean(false);

  private void performShutdown() {
    if (!myShutdownPerformed.compareAndSet(false, true)) {
      return; // already shut down
    }
    myFileDocumentManager.saveAllDocuments();
    
    LOG.info("START INDEX SHUTDOWN");
    try {
      myChangedFilesCollector.forceUpdate(null, null, true);

      for (ID<?, ?> indexId : myIndices.keySet()) {
        final UpdatableIndex<?, ?, FileContent> index = getIndex(indexId);
        assert index != null;
        checkRebuild(indexId, true); // if the index was scheduled for rebuild, only clean it
        //LOG.info("DISPOSING " + indexId);
        index.dispose();
      }

      myVfManager.removeVirtualFileListener(myChangedFilesCollector);

      FileUtil.delete(getMarkerFile());
    }
    catch (Throwable e) {
      LOG.info("Problems during index shutdown", e);
      throw new RuntimeException(e);
    }
    LOG.info("END INDEX SHUTDOWN");
  }

  public void flushCaches() {
    IndexingStamp.flushCache();
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        for (ID<?, ?> indexId : myIndices.keySet()) {
          //noinspection ConstantConditions
          try {
            getIndex(indexId).flush();
          }
          catch (StorageException e) {
            LOG.info(e);
            requestRebuild(indexId);
          }
        }
      }
    });
  }

  /**
   * @param project it is guaranteeed to return data which is up-to-date withing the project
   * Keys obtained from the files which do not belong to the project specified may not be up-to-date or even exist
   */
  @NotNull
  public <K> Collection<K> getAllKeys(final ID<K, ?> indexId, @NotNull Project project) {
    Set<K> allKeys = new HashSet<K>();
    processAllKeys(indexId, new CommonProcessors.CollectProcessor<K>(allKeys), project);
    return allKeys;
  }

  /**
   * @param project it is guaranteeed to return data which is up-to-date withing the project
   * Keys obtained from the files which do not belong to the project specified may not be up-to-date or even exist
   */
  public <K> boolean processAllKeys(final ID<K, ?> indexId, Processor<K> processor, @NotNull Project project) {
    try {
      ensureUpToDate(indexId, project, GlobalSearchScope.allScope(project));
      final UpdatableIndex<K, ?, FileContent> index = getIndex(indexId);
      if (index == null) return true;
      return index.processAllKeys(processor);
    }
    catch (StorageException e) {
      scheduleRebuild(indexId, e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof StorageException || cause instanceof IOException) {
        scheduleRebuild(indexId, cause);
      }
      else {
        throw e;
      }
    }

    return false;
  }

  private static final ThreadLocal<Integer> myUpToDateCheckState = new ThreadLocal<Integer>();

  public static void disableUpToDateCheckForCurrentThread() {
    final Integer currentValue = myUpToDateCheckState.get();
    myUpToDateCheckState.set(currentValue == null? 1 : currentValue.intValue() + 1);
  }

  public static void enableUpToDateCheckForCurrentThread() {
    final Integer currentValue = myUpToDateCheckState.get();
    if (currentValue != null) {
      final int newValue = currentValue.intValue() - 1;
      if (newValue != 0) {
        myUpToDateCheckState.set(newValue);
      }
      else {
        myUpToDateCheckState.remove();
      }
    }
  }

  private boolean isUpToDateCheckEnabled() {
    final Integer value = myUpToDateCheckState.get();
    return value == null || value.intValue() == 0;
  }


  private final ThreadLocal<Boolean> myReentrancyGuard = new ThreadLocal<Boolean>() {
    protected Boolean initialValue() {
      return Boolean.FALSE;
    }
  };

  /**
   * DO NOT CALL DIRECTLY IN CLIENT CODE
   * The method is internal to indexing engine end is called internally. The method is public due to implementation details
   */
  public <K> void ensureUpToDate(final ID<K, ?> indexId, @NotNull Project project, @Nullable GlobalSearchScope filter) {
    if (isDumb(project)) {
      handleDumbMode(indexId, project);
    }

    if (myReentrancyGuard.get().booleanValue()) {
      //assert false : "ensureUpToDate() is not reentrant!";
      return;
    }
    myReentrancyGuard.set(Boolean.TRUE);

    try {
      myChangedFilesCollector.ensureAllInvalidateTasksCompleted();
      if (isUpToDateCheckEnabled()) {
        try {
          checkRebuild(indexId, false);
          myChangedFilesCollector.forceUpdate(project, filter, false);
          indexUnsavedDocuments(indexId, project, filter);
        }
        catch (StorageException e) {
          scheduleRebuild(indexId, e);
        }
        catch (RuntimeException e) {
          final Throwable cause = e.getCause();
          if (cause instanceof StorageException || cause instanceof IOException) {
            scheduleRebuild(indexId, e);
          }
          else {
            throw e;
          }
        }
      }
    }
    finally {
      myReentrancyGuard.set(Boolean.FALSE);
    }
  }

  private void handleDumbMode(final ID<?, ?> indexId, Project project) {
    if (myNotRequiringContentIndices.contains(indexId)) {
      return; //indexed eagerly in foreground while building unindexed file list
    }

    ProgressManager.checkCanceled(); // DumbModeAction.CANCEL

    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator instanceof BackgroundableProcessIndicator) {
      final BackgroundableProcessIndicator indicator = (BackgroundableProcessIndicator)progressIndicator;
      if (indicator.getDumbModeAction() == DumbModeAction.WAIT) {
        assert !ApplicationManager.getApplication().isDispatchThread();
        DumbService.getInstance(project).waitForSmartMode();
        return;
      }
    }

    throw new IndexNotReadyException();
  }

  private static boolean isDumb(Project project) {
    return DumbServiceImpl.getInstance(project).isDumb();
  }

  @NotNull
  public <K, V> List<V> getValues(final ID<K, V> indexId, @NotNull K dataKey, @NotNull final GlobalSearchScope filter) {
    final List<V> values = new ArrayList<V>();
    processValuesImpl(indexId, dataKey, true, null, new ValueProcessor<V>() {
      public boolean process(final VirtualFile file, final V value) {
        values.add(value);
        return true;
      }
    }, filter);
    return values;
  }

  @NotNull
  public <K, V> Collection<VirtualFile> getContainingFiles(final ID<K, V> indexId, @NotNull K dataKey, @NotNull final GlobalSearchScope filter) {
    final Set<VirtualFile> files = new HashSet<VirtualFile>();
    processValuesImpl(indexId, dataKey, false, null, new ValueProcessor<V>() {
      public boolean process(final VirtualFile file, final V value) {
        files.add(file);
        return true;
      }
    }, filter);
    return files;
  }


  public interface ValueProcessor<V> {
    /**
     * @param value a value to process
     * @param file the file the value came from
     * @return false if no further processing is needed, true otherwise
     */
    boolean process(VirtualFile file, V value);
  }

  /**
   * @return false if ValueProcessor.process() returned false; true otherwise or if ValueProcessor was not called at all 
   */
  public <K, V> boolean processValues(final ID<K, V> indexId, final @NotNull K dataKey, @Nullable final VirtualFile inFile,
                                   ValueProcessor<V> processor, @NotNull final GlobalSearchScope filter) {
    return processValuesImpl(indexId, dataKey, false, inFile, processor, filter);
  }



  private <K, V> boolean processValuesImpl(final ID<K, V> indexId, final K dataKey, boolean ensureValueProcessedOnce,
                                        @Nullable final VirtualFile restrictToFile, ValueProcessor<V> processor,
                                        final GlobalSearchScope filter) {
    try {
      final Project project = filter.getProject();
      assert project != null : "GlobalSearchScope#getProject() should be not-null for all index queries";
      ensureUpToDate(indexId, project, filter);
      final UpdatableIndex<K, V, FileContent> index = getIndex(indexId);
      if (index == null) {
        return true;
      }

      final Lock readLock = index.getReadLock();
      try {
        readLock.lock();
        final ValueContainer<V> container = index.getData(dataKey);

        boolean shouldContinue = true;

        if (restrictToFile != null) {
          if (restrictToFile instanceof VirtualFileWithId) {
            final int restrictedFileId = getFileId(restrictToFile);
            for (final Iterator<V> valueIt = container.getValueIterator(); valueIt.hasNext();) {
              final V value = valueIt.next();
              if (container.isAssociated(value, restrictedFileId)) {
                shouldContinue = processor.process(restrictToFile, value);
                if (!shouldContinue) {
                  break;
                }
              }
            }
          }
        }
        else {
          final PersistentFS fs = (PersistentFS)ManagingFS.getInstance();
          VALUES_LOOP: for (final Iterator<V> valueIt = container.getValueIterator(); valueIt.hasNext();) {
            final V value = valueIt.next();
            for (final ValueContainer.IntIterator inputIdsIterator = container.getInputIdsIterator(value); inputIdsIterator.hasNext();) {
              final int id = inputIdsIterator.next();
              VirtualFile file = IndexInfrastructure.findFileById(fs, id);
              if (file != null && filter.accept(file)) {
                shouldContinue = processor.process(file, value);
                if (!shouldContinue) {
                  break VALUES_LOOP;
                }
                if (ensureValueProcessedOnce) {
                  break; // continue with the next value
                }
              }
            }
          }
        }
        return shouldContinue;
      }
      finally {
        index.getReadLock().unlock();
      }
    }
    catch (StorageException e) {
      scheduleRebuild(indexId, e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof StorageException || cause instanceof IOException) {
        scheduleRebuild(indexId, cause);
      }
      else {
        throw e;
      }
    }
    return true;
  }

  public <K, V> boolean getFilesWithKey(final ID<K, V> indexId, final Set<K> dataKeys,
                                         Processor<VirtualFile> processor,
                                         GlobalSearchScope filter) {
    try {
      final Project project = filter.getProject();
      assert project != null : "GlobalSearchScope#getProject() should be not-null for all index queries";
      ensureUpToDate(indexId, project, filter);
      final UpdatableIndex<K, V, FileContent> index = getIndex(indexId);
      if (index == null) {
        return true;
      }

      final Lock readLock = index.getReadLock();
      try {
        readLock.lock();
        List<TIntHashSet> locals = new ArrayList<TIntHashSet>();
        for (K dataKey : dataKeys) {
          TIntHashSet local = new TIntHashSet();
          locals.add(local);
          final ValueContainer<V> container = index.getData(dataKey);

          for (final Iterator<V> valueIt = container.getValueIterator(); valueIt.hasNext();) {
            final V value = valueIt.next();
            for (final ValueContainer.IntIterator inputIdsIterator = container.getInputIdsIterator(value); inputIdsIterator.hasNext();) {
              final int id = inputIdsIterator.next();
              local.add(id);
            }
          }
        }

        if (locals.size() == 0) return true;

        Collections.sort(locals, new Comparator<TIntHashSet>() {
          public int compare(TIntHashSet o1, TIntHashSet o2) {
            return o1.size() - o2.size();
          }
        });

        final PersistentFS fs = (PersistentFS)ManagingFS.getInstance();
        TIntIterator ids = join(locals).iterator();
        while (ids.hasNext()) {
          int id = ids.next();
          VirtualFile file = IndexInfrastructure.findFileById(fs, id);
          if (file != null && filter.accept(file)) {
            if (!processor.process(file)) return false;
          }
        }
      }
      finally {
        index.getReadLock().unlock();
      }
    }
    catch (StorageException e) {
      scheduleRebuild(indexId, e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof StorageException || cause instanceof IOException) {
        scheduleRebuild(indexId, cause);
      }
      else {
        throw e;
      }
    }
    return true;
  }

  private static TIntHashSet join(List<TIntHashSet> locals) {
    TIntHashSet result = locals.get(0);
    if (locals.size() > 1) {
      TIntIterator it = result.iterator();

      while (it.hasNext()) {
        int id = it.next();
        for (int i = 1; i < locals.size(); i++) {
          if (!locals.get(i).contains(id)) {
            it.remove();
            break;
          }
        }
      }
    }
    return result;
  }

  public interface AllValuesProcessor<V> {
    void process(final int inputId, V value);
  }

  public <K, V> void processAllValues(final ID<K, V> indexId, final AllValuesProcessor<V> processor, @NotNull Project project) {
    try {
      ensureUpToDate(indexId, project, null);
      final UpdatableIndex<K, V, FileContent> index = getIndex(indexId);
      if (index == null) {
        return;
      }
      final Ref<StorageException> storageEx = new Ref<StorageException>(null);
      index.processAllKeys(new Processor<K>() {
        public boolean process(K dataKey) {
          try {
            final ValueContainer<V> container = index.getData(dataKey);
            for (final Iterator<V> it = container.getValueIterator(); it.hasNext();) {
              final V value = it.next();
              for (final ValueContainer.IntIterator inputsIt = container.getInputIdsIterator(value); inputsIt.hasNext();) {
                processor.process(inputsIt.next(), value);
              }
            }
            return true;
          }
          catch (StorageException e) {
            storageEx.set(e);
            return false;
          }
        }
      });
      final StorageException ex = storageEx.get();
      if (ex != null) {
        throw ex;
      }
    }
    catch (StorageException e) {
      scheduleRebuild(indexId, e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof StorageException || cause instanceof IOException) {
        scheduleRebuild(indexId, e);
      }
      else {
        throw e;
      }
    }
  }

  private <K> void scheduleRebuild(final ID<K, ?> indexId, final Throwable e) {
    requestRebuild(indexId);
    LOG.info(e);
    checkRebuild(indexId, false);
  }

  @TestOnly
  public boolean isIndexReady(final ID<?, ?> indexId) {
    return myRebuildStatus.get(indexId).get() == OK;
  }

  private void checkRebuild(final ID<?, ?> indexId, final boolean cleanupOnly) {
    if (myRebuildStatus.get(indexId).compareAndSet(REQUIRES_REBUILD, REBUILD_IN_PROGRESS)) {
      cleanupProcessedFlag();

      final Runnable rebuildRunnable = new Runnable() {
        public void run() {
          try {
            clearIndex(indexId);
            if (!cleanupOnly) {
              for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                UnindexedFilesUpdater updater =
                  new UnindexedFilesUpdater(project, FileBasedIndex.this);
                DumbServiceImpl.getInstance(project).queueCacheUpdate(Collections.<CacheUpdater>singleton(updater));
              }
            }
          }
          catch (StorageException e) {
            requestRebuild(indexId);
            LOG.info(e);
          }
          finally {
            myRebuildStatus.get(indexId).compareAndSet(REBUILD_IN_PROGRESS, OK);
          }
        }
      };

      final Application application = ApplicationManager.getApplication();
      if (cleanupOnly || application.isUnitTestMode()) {
        rebuildRunnable.run();
      }
      else {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            new Task.Modal(null, "Updating index", false) {
              public void run(@NotNull final ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                rebuildRunnable.run();
              }
            }.queue();
          }
        });
      }
    }

    if (myRebuildStatus.get(indexId).get() == REBUILD_IN_PROGRESS) {
      throw new ProcessCanceledException();
    }
  }

  private void clearIndex(final ID<?, ?> indexId) throws StorageException {
    final UpdatableIndex<?, ?, FileContent> index = getIndex(indexId);
    assert index != null;
    index.clear();
    try {
      IndexInfrastructure.rewriteVersion(IndexInfrastructure.getVersionFile(indexId), myIndexIdToVersionMap.get(indexId));
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private Set<Document> getUnsavedOrTransactedDocuments() {
    Set<Document> docs = new HashSet<Document>(Arrays.asList(myFileDocumentManager.getUnsavedDocuments()));
    docs.addAll(myTransactionMap.keySet());
    return docs;
  }

  private void indexUnsavedDocuments(ID<?, ?> indexId, Project project, GlobalSearchScope filter) throws StorageException {

    if (myUpToDateIndices.contains(indexId)) {
      return; // no need to index unsaved docs
    }

    final Set<Document> documents = getUnsavedOrTransactedDocuments();
    if (!documents.isEmpty()) {
      // now index unsaved data
      final StorageGuard.Holder guard = setDataBufferingEnabled(true);
      try {
        boolean allDocsProcessed = true;
        final Semaphore semaphore = myUnsavedDataIndexingSemaphores.get(indexId);
        semaphore.down();
        try {
          for (Document document : documents) {
            allDocsProcessed &= indexUnsavedDocument(document, indexId, project, filter);
          }
        }
        finally {
          semaphore.up();

          while (!semaphore.waitFor(500)) { // may need to wait until another thread is done with indexing
            if (Thread.holdsLock(PsiLock.LOCK)) {
              break; // hack. Most probably that other indexing threads is waiting for PsiLock, which we're are holding.
            }
          }
          if (allDocsProcessed) {
            myUpToDateIndices.add(indexId); // safe to set the flag here, becase it will be cleared under the WriteAction
          }
        }
      }
      finally {
        guard.leave();
      }
    }
  }

  private interface DocumentContent {
    String getText();
    long getModificationStamp();
  }

  private static class AuthenticContent implements DocumentContent {
    private final Document myDocument;

    private AuthenticContent(final Document document) {
      myDocument = document;
    }

    public String getText() {
      return myDocument.getText();
    }

    public long getModificationStamp() {
      return myDocument.getModificationStamp();
    }
  }

  private static class PsiContent implements DocumentContent {
    private final Document myDocument;
    private final PsiFile myFile;

    private PsiContent(final Document document, final PsiFile file) {
      myDocument = document;
      myFile = file;
    }

    public String getText() {
      if (myFile.getModificationStamp() != myDocument.getModificationStamp()) {
        final ASTNode node = myFile.getNode();
        assert node != null;
        return node.getText();
      }
      return myDocument.getText();
    }

    public long getModificationStamp() {
      return myFile.getModificationStamp();
    }
  }

// returns false if doc was not indexed because the file does not fit in scope
private boolean indexUnsavedDocument(final Document document, final ID<?, ?> requestedIndexId, final Project project,
                                     GlobalSearchScope filter) throws StorageException {
  final VirtualFile vFile = myFileDocumentManager.getFile(document);
  if (!(vFile instanceof VirtualFileWithId) || !vFile.isValid()) {
    return true;
  }
  if (filter != null && !filter.accept(vFile)) {
    return false;
  }
  final PsiFile dominantContentFile = findDominantPsiForDocument(document, project);

  final DocumentContent content;
  if (dominantContentFile != null && dominantContentFile.getModificationStamp() != document.getModificationStamp()) {
    content = new PsiContent(document, dominantContentFile);
  }
  else {
    content = new AuthenticContent(document);
  }

  final long currentDocStamp = content.getModificationStamp();
  if (currentDocStamp != myLastIndexedDocStamps.getAndSet(document, requestedIndexId, currentDocStamp).longValue()) {
    final Ref<StorageException> exRef = new Ref<StorageException>(null);
    ProgressManager.getInstance().executeNonCancelableSection(new Runnable() {
      public void run() {
        try {
          final FileContent newFc = new FileContent(vFile, content.getText(), vFile.getCharset());

          if (dominantContentFile != null) {
                dominantContentFile.putUserData(PsiFileImpl.BUILDING_STUB, true);
                newFc.putUserData(PSI_FILE, dominantContentFile);
              }

          if (content instanceof AuthenticContent) {
                newFc.putUserData(EDITOR_HIGHLIGHTER, document instanceof DocumentImpl
                                                      ? ((DocumentImpl)document).getEditorHighlighterForCachesBuilding() : null);
              }

          if (getInputFilter(requestedIndexId).acceptInput(vFile)) {
                newFc.putUserData(PROJECT, project);
                final int inputId = Math.abs(getFileId(vFile));
                getIndex(requestedIndexId).update(inputId, newFc);
              }

          if (dominantContentFile != null) {
                dominantContentFile.putUserData(PsiFileImpl.BUILDING_STUB, null);
              }
        }
        catch (StorageException e) {
          exRef.set(e);
        }
      }
    });
    final StorageException storageException = exRef.get();
    if (storageException != null) {
      throw storageException;
    }
  }
  return true;
}

  public static final Key<PsiFile> PSI_FILE = new Key<PsiFile>("PSI for stubs");
  public static final Key<EditorHighlighter> EDITOR_HIGHLIGHTER = new Key<EditorHighlighter>("Editor");
  public static final Key<Project> PROJECT = new Key<Project>("Context project");
  public static final Key<VirtualFile> VIRTUAL_FILE = new Key<VirtualFile>("Context virtual file");

  @Nullable
  private PsiFile findDominantPsiForDocument(final Document document, Project project) {
    if (myTransactionMap.containsKey(document)) {
      return myTransactionMap.get(document);
    }

    return findLatestKnownPsiForUncomittedDocument(document, project);
  }

  private final StorageGuard myStorageLock = new StorageGuard();

  private StorageGuard.Holder setDataBufferingEnabled(final boolean enabled) {
    final StorageGuard.Holder holder = myStorageLock.enter(enabled);
    for (ID<?, ?> indexId : myIndices.keySet()) {
      final MapReduceIndex index = (MapReduceIndex)getIndex(indexId);
      assert index != null;
      final IndexStorage indexStorage = index.getStorage();
      ((MemoryIndexStorage)indexStorage).setBufferingEnabled(enabled);
    }
    return holder;
  }

  private void cleanupMemoryStorage() {
    synchronized (myLastIndexedDocStamps) {
      myLastIndexedDocStamps.clear();
    }
    for (ID<?, ?> indexId : myIndices.keySet()) {
      final MapReduceIndex index = (MapReduceIndex)getIndex(indexId);
      assert index != null;
      final MemoryIndexStorage memStorage = (MemoryIndexStorage)index.getStorage();
      index.getWriteLock().lock();
      try {
        memStorage.clearMemoryMap();
      }
      finally {
        index.getWriteLock().unlock();
      }
      memStorage.fireMemoryStorageCleared();
    }
  }

  private void dropUnregisteredIndices() {
    final Set<String> indicesToDrop = readRegistsredIndexNames();
    for (ID<?, ?> key : myIndices.keySet()) {
      indicesToDrop.remove(key.toString());
    }
    for (String s : indicesToDrop) {
      FileUtil.delete(IndexInfrastructure.getIndexRootDir(ID.create(s)));
    }
  }

  public void requestRebuild(ID<?, ?> indexId) {
    requestRebuild(indexId, new Throwable());
  }

  public void requestRebuild(ID<?, ?> indexId, Throwable throwable) {
    cleanupProcessedFlag();
    LOG.info("Rebuild requested for index " + indexId, throwable);
    myRebuildStatus.get(indexId).set(REQUIRES_REBUILD);
  }

  private <K, V> UpdatableIndex<K, V, FileContent> getIndex(ID<K, V> indexId) {
    final Pair<UpdatableIndex<?, ?, FileContent>, InputFilter> pair = myIndices.get(indexId);
    //noinspection unchecked
    return pair != null? (UpdatableIndex<K,V, FileContent>)pair.getFirst() : null;
  }

  private InputFilter getInputFilter(ID<?, ?> indexId) {
    final Pair<UpdatableIndex<?, ?, FileContent>, InputFilter> pair = myIndices.get(indexId);
    return pair != null? pair.getSecond() : null;
  }

  public Collection<VirtualFile> getFilesToUpdate(final Project project) {
    final ProjectFileIndex projectIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return ContainerUtil.findAll(myChangedFilesCollector.getAllFilesToUpdate(), new Condition<VirtualFile>() {
      public boolean value(VirtualFile virtualFile) {
        return projectIndex.isInContent(virtualFile);
      }
    });
  }

  public void processRefreshedFile(@NotNull Project project, final com.intellij.ide.caches.FileContent fileContent) {
    myChangedFilesCollector.ensureAllInvalidateTasksCompleted();
    myChangedFilesCollector.processFileImpl(project, fileContent, false);
  }

  public void indexFileContent(@Nullable Project project, com.intellij.ide.caches.FileContent content) {
    myChangedFilesCollector.ensureAllInvalidateTasksCompleted();
    final VirtualFile file = content.getVirtualFile();
    FileContent fc = null;

    PsiFile psiFile = null;

    final Ref<ProcessCanceledException> pce = Ref.create(null);

    final List<Runnable> tasks = new ArrayList<Runnable>();
    for (final ID<?, ?> indexId : myIndices.keySet()) {
      if (shouldIndexFile(file, indexId)) {
        if (fc == null) {
          byte[] currentBytes;
          try {
            currentBytes = content.getBytes();
          }
          catch (IOException e) {
            currentBytes = ArrayUtil.EMPTY_BYTE_ARRAY;
          }
          fc = new FileContent(file, currentBytes);

          psiFile = content.getUserData(PSI_FILE);
          if (psiFile != null) {
            psiFile.putUserData(PsiFileImpl.BUILDING_STUB, true);
            fc.putUserData(PSI_FILE, psiFile);
          }
          if (project == null) {
            project = ProjectUtil.guessProjectForFile(file);
          }
          fc.putUserData(PROJECT, project);
        }

        final FileContent _fc = fc;
        tasks.add(new Runnable() {
          public void run() {
            try {
              updateSingleIndex(indexId, file, _fc);
            }
            catch (ProcessCanceledException e) {
              pce.set(e);
            }
            catch (StorageException e) {
              requestRebuild(indexId);
              LOG.info(e);
            }
          }
        });
      }
    }

    if (tasks.size() > 0) {
      if (Registry.get(USE_MULTITHREADED_INDEXING).asBoolean() && !myIsUnitTestMode) {
        final Job<Object> job = JobScheduler.getInstance().createJob("IndexJob", Job.DEFAULT_PRIORITY / 2);
        try {
          for (Runnable task : tasks) {
            job.addTask(task);
          }
          job.scheduleAndWaitForResults();
        }
        catch (Throwable throwable) {
          LOG.info(throwable);
        }
      }
      else {
        for (Runnable task : tasks) {
          task.run();
        }
      }
    }

    if (!pce.isNull()) {
      myChangedFilesCollector.scheduleForUpdate(file);
      throw pce.get();
    }

    if (psiFile != null) {
      psiFile.putUserData(PsiFileImpl.BUILDING_STUB, null);
    }
  }

  private void updateSingleIndex(final ID<?, ?> indexId, final VirtualFile file, final FileContent currentFC)
    throws StorageException {
    if (myRebuildStatus.get(indexId).get() == REQUIRES_REBUILD) {
      return; // the index is scheduled for rebuild, no need to update
    }

    final StorageGuard.Holder lock = setDataBufferingEnabled(false);

    try {
      final int inputId = Math.abs(getFileId(file));

      final UpdatableIndex<?, ?, FileContent> index = getIndex(indexId);
      assert index != null;

      final Ref<StorageException> exRef = new Ref<StorageException>(null);
      ProgressManager.getInstance().executeNonCancelableSection(new Runnable() {
        public void run() {
          try {
            index.update(inputId, currentFC);
          }
          catch (StorageException e) {
            exRef.set(e);
          }
        }
      });
      final StorageException storageException = exRef.get();
      if (storageException != null) {
        throw storageException;
      }
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          if (file.isValid()) {
            if (currentFC != null) {
              IndexingStamp.update(file, indexId, IndexInfrastructure.getIndexCreationStamp(indexId));
            }
            else {
              // mark the file as unindexed
              IndexingStamp.update(file, indexId, -1L);
            }
          }
        }
      });
    }
    finally {
      lock.leave();
    }
  }

  public static int getFileId(final VirtualFile file) {
    if (file instanceof VirtualFileWithId) {
      return ((VirtualFileWithId)file).getId();
    }

    throw new IllegalArgumentException("Virtual file doesn't support id: " + file + ", implementation class: " + file.getClass().getName());
  }

  private boolean needsFileContentLoading(ID<?, ?> indexId) {
    return !myNotRequiringContentIndices.contains(indexId);
  }

  private abstract static class InvalidationTask implements Runnable {
    private final VirtualFile mySubj;

    protected InvalidationTask(final VirtualFile subj) {
      mySubj = subj;
    }

    public VirtualFile getSubj() {
      return mySubj;
    }
  }

  private final class ChangedFilesCollector extends VirtualFileAdapter {
    private final Set<VirtualFile> myFilesToUpdate = new LinkedHashSet<VirtualFile>();
    private final Queue<InvalidationTask> myFutureInvalidations = new LinkedList<InvalidationTask>();
    private final JBReentrantReadWriteLock myLock = LockFactory.createReadWriteLock();
    private final JBLock r = myLock.readLock();
    private final JBLock w = myLock.writeLock();

    private final ManagingFS myManagingFS = ManagingFS.getInstance();
    // No need to react on movement events since files stay valid, their ids don't change and all associated attributes remain intact.

    public void fileCreated(final VirtualFileEvent event) {
      markDirty(event);
    }

    public void fileDeleted(final VirtualFileEvent event) {
      w.lock();
      try {
        myFilesToUpdate.remove(event.getFile()); // no need to update it anymore
      }
      finally {
        w.unlock();
      }
    }

    public void fileCopied(final VirtualFileCopyEvent event) {
      markDirty(event);
    }

    public void beforeFileDeletion(final VirtualFileEvent event) {
      invalidateIndices(event.getFile(), false);
    }

    public void beforeContentsChange(final VirtualFileEvent event) {
      invalidateIndices(event.getFile(), true);
    }

    public void contentsChanged(final VirtualFileEvent event) {
      markDirty(event);
    }

    public void beforePropertyChange(final VirtualFilePropertyEvent event) {
      if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
        // indexes may depend on file name
        final VirtualFile file = event.getFile();
        if (!file.isDirectory()) {
          // name change may lead to filetype change so the file might become not indexable
          // in general case have to 'unindex' the file and index it again if needed after the name has been changed
          invalidateIndices(file, false);
        }
      }
    }

    public void propertyChanged(final VirtualFilePropertyEvent event) {
      if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
        // indexes may depend on file name
        if (!event.getFile().isDirectory()) {
          markDirty(event);
        }
      }
    }

    private void markDirty(final VirtualFileEvent event) {
      cleanProcessedFlag(event.getFile());
      iterateIndexableFiles(event.getFile(), new Processor<VirtualFile>() {
        public boolean process(final VirtualFile file) {
          FileContent fileContent = null;
          // handle 'content-less' indices separately
          for (ID<?, ?> indexId : myNotRequiringContentIndices) {
            if (getInputFilter(indexId).acceptInput(file)) {
              try {
                if (fileContent == null) {
                  fileContent = new FileContent(file);
                }
                updateSingleIndex(indexId, file, fileContent);
              }
              catch (StorageException e) {
                LOG.info(e);
                requestRebuild(indexId);
              }
            }
          }
          // For 'normal indices' schedule the file for update and stop iteration if at least one index accepts it 
          if (!isTooLarge(file)) {
            for (ID<?, ?> indexId : myIndices.keySet()) {
              if (needsFileContentLoading(indexId) && getInputFilter(indexId).acceptInput(file)) {
                scheduleForUpdate(file);
                break; // no need to iterate further, as the file is already marked
              }
            }
          }

          return true;
        }
      });
      IndexingStamp.flushCache();
    }

    public void scheduleForUpdate(VirtualFile file) {
      w.lock();
      try {
        myFilesToUpdate.add(file);
      }
      finally {
        w.unlock();
      }
    }

    void invalidateIndices(final VirtualFile file, final boolean markForReindex) {
      if (isUnderConfigOrSystem(file)) {
        return;
      }
      if (file.isDirectory()) {
        if (isMock(file) || myManagingFS.wereChildrenAccessed(file)) {
          final Collection<VirtualFile> children = (file instanceof NewVirtualFile)? ((NewVirtualFile)file).getInDbChildren() : Arrays.asList(file.getChildren());
          for (VirtualFile child : children) {
            if (NullVirtualFile.INSTANCE != child) {
              invalidateIndices(child, markForReindex);
            }
          }
        }
      }
      else {
        cleanProcessedFlag(file);
        IndexingStamp.flushCache();
        final List<ID<?, ?>> affectedIndices = new ArrayList<ID<?, ?>>(myIndices.size());

        final boolean isTooLarge = isTooLarge(file);
        for (final ID<?, ?> indexId : myIndices.keySet()) {
          try {
            if (myNotRequiringContentIndices.contains(indexId)) {
              if (shouldUpdateIndex(file, indexId)) {
                updateSingleIndex(indexId, file, null);
              }
            }
            else { // the index requires file content
              if (!isTooLarge && shouldUpdateIndex(file, indexId)) {
                affectedIndices.add(indexId);
              }
            }
          }
          catch (StorageException e) {
            LOG.info(e);
            requestRebuild(indexId);
          }
        }

        if (affectedIndices.size() > 0) {
          if (markForReindex) {
            // only mark the file as unindexed, reindex will be done lazily
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                for (ID<?, ?> indexId : affectedIndices) {
                  IndexingStamp.update(file, indexId, -1L);
                }
              }
            });
            iterateIndexableFiles(file, new Processor<VirtualFile>() {
              public boolean process(final VirtualFile file) {
                scheduleForUpdate(file);
                return true;
              }
            });
          }
          else {
            final InvalidationTask invalidator = new InvalidationTask(file) {
              public void run() {
                removeFileDataFromIndices(affectedIndices, file);
              }
            };
            w.lock();
            try {
              myFutureInvalidations.offer(invalidator);
            }
            finally {
              w.unlock();
            }
          }
        }
        IndexingStamp.flushCache();
      }
    }

    private void removeFileDataFromIndices(List<ID<?, ?>> affectedIndices, VirtualFile file) {
      Throwable unexpectedError = null;
      for (ID<?, ?> indexId : affectedIndices) {
        try {
          updateSingleIndex(indexId, file, null);
        }
        catch (StorageException e) {
          LOG.info(e);
          requestRebuild(indexId);
        }
        catch (ProcessCanceledException ignored) {
        }
        catch (Throwable e) {
          LOG.info(e);
          if (unexpectedError == null) {
            unexpectedError = e;
          }
        }
      }
      IndexingStamp.flushCache();
      if (unexpectedError != null) {
        LOG.error(unexpectedError);
      }
    }

    public void ensureAllInvalidateTasksCompleted() {
      final int size;
      r.lock();
      try {
        size = myFutureInvalidations.size();
        if (size == 0) return;
      }
      finally {
        r.unlock();
      }
      final ProgressIndicator current = ProgressManager.getInstance().getProgressIndicator();
      final ProgressIndicator indicator = current != null ? current : new EmptyProgressIndicator();
      indicator.setText("");
      int count = 0;
      while (true) {
        InvalidationTask task;
        w.lock();
        try {
          task = myFutureInvalidations.poll();
        }
        finally {
          w.unlock();
        }

        if (task == null) {
          break;
        }
        indicator.setFraction(((double)count++)/size);
        indicator.setText2(task.getSubj().getPresentableUrl());
        task.run();
      }
    }

    private void iterateIndexableFiles(final VirtualFile file, final Processor<VirtualFile> processor) {
      if (file.isDirectory()) {
        final ContentIterator iterator = new ContentIterator() {
          public boolean processFile(final VirtualFile fileOrDir) {
            if (!fileOrDir.isDirectory()) {
              processor.process(fileOrDir);
            }
            return true;
          }
        };

        for (IndexableFileSet set : myIndexableSets) {
          set.iterateIndexableFilesIn(file, iterator);
        }
      }
      else {
        for (IndexableFileSet set : myIndexableSets) {
          if (set.isInSet(file)) {
            processor.process(file);
            break;
          }
        }
      }
    }

    public Collection<VirtualFile> getAllFilesToUpdate() {
      r.lock();
      try {
        if (myFilesToUpdate.isEmpty())  return Collections.EMPTY_LIST;
        return new ArrayList<VirtualFile>(myFilesToUpdate);
      }
      finally {
        r.unlock();
      }
    }

    private final Semaphore myForceUpdateSemaphore = new Semaphore();

    public void forceUpdate(@Nullable Project project, @Nullable GlobalSearchScope filter, boolean onlyRemoveOutdatedData) {
      myChangedFilesCollector.ensureAllInvalidateTasksCompleted();
      for (VirtualFile file: getAllFilesToUpdate()) {
        if (filter == null || filter.accept(file)) {
          try {
            myForceUpdateSemaphore.down();
            // process only files that can affect result
            processFileImpl(project, new com.intellij.ide.caches.FileContent(file), onlyRemoveOutdatedData);
          }
          finally {
            myForceUpdateSemaphore.up();
          }
        }
      }

      // If several threads entered the method at the same time and there were files to update,
      // all the threads should leave the method synchronously after all the files scheduled for update are reindexed,
      // no matter which thread will do reindexing job.
      // Thus we ensure that all the threads that entered the method will get the most recent data

      while (!myForceUpdateSemaphore.waitFor(500)) { // may need to wait until another thread is done with indexing
        if (Thread.holdsLock(PsiLock.LOCK)) {
          break; // hack. Most probably that other indexing threads is waiting for PsiLock, which we're are holding.
        }
      }
    }

    private void processFileImpl(Project project, final com.intellij.ide.caches.FileContent fileContent, boolean onlyRemoveOutdatedData) {
      final VirtualFile file = fileContent.getVirtualFile();
      final boolean reallyRemoved;
      w.lock();
      try {
        reallyRemoved = myFilesToUpdate.remove(file);
      }
      finally {
        w.unlock();
      }
      if (reallyRemoved && file.isValid()) {
        if (onlyRemoveOutdatedData) {
          // on shutdown there is no need to re-index the file, just remove outdated data from indices
          final List<ID<?, ?>> affected = new ArrayList<ID<?,?>>();
          for (final ID<?, ?> indexId : myIndices.keySet()) {
            if (getInputFilter(indexId).acceptInput(file)) {
              affected.add(indexId);
            }
          }
          removeFileDataFromIndices(affected, file);
        }
        else {
          indexFileContent(project, fileContent);
        }
        IndexingStamp.flushCache();
      }
    }
  }

  private class UnindexedFilesFinder implements CollectingContentIterator {
    private final List<VirtualFile> myFiles = new ArrayList<VirtualFile>();
    private final ProgressIndicator myProgressIndicator;

    private UnindexedFilesFinder() {
      myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
    }

    public List<VirtualFile> getFiles() {
      return myFiles;
    }

    public boolean processFile(final VirtualFile file) {
      if (!file.isDirectory()) {
        if (file instanceof NewVirtualFile && ((NewVirtualFile)file).getFlag(ALREADY_PROCESSED)) {
          return true;
        }

        if (file instanceof VirtualFileWithId) {
          boolean oldStuff = true;
          if (!isTooLarge(file)) {
            for (ID<?, ?> indexId : myIndices.keySet()) {
              try {
                if (shouldIndexFile(file, indexId)) {
                  myFiles.add(file);
                  oldStuff = false;
                  break;
                }
              }
              catch (RuntimeException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof IOException || cause instanceof StorageException) {
                  LOG.info(e);
                  requestRebuild(indexId);
                }
                else {
                  throw e;
                }
              }
            }
          }
          FileContent fileContent = null;
          for (ID<?, ?> indexId : myNotRequiringContentIndices) {
            if (shouldIndexFile(file, indexId)) {
              oldStuff = false;
              try {
                if (fileContent == null) {
                  fileContent = new FileContent(file);
                }
                updateSingleIndex(indexId, file, fileContent);
              }
              catch (StorageException e) {
                LOG.info(e);
                requestRebuild(indexId);
              }
            }
          }
          IndexingStamp.flushCache();

          if (oldStuff && file instanceof NewVirtualFile) {
            ((NewVirtualFile)file).setFlag(ALREADY_PROCESSED, true);
          }
        }
      }
      else {
        if (myProgressIndicator != null) {
          myProgressIndicator.setText("Scanning files to index");
          myProgressIndicator.setText2(file.getPresentableUrl());
        }
      }
      return true;
    }
  }

  private boolean shouldUpdateIndex(final VirtualFile file, final ID<?, ?> indexId) {
    return getInputFilter(indexId).acceptInput(file) &&
           (isMock(file) || IndexingStamp.isFileIndexed(file, indexId, IndexInfrastructure.getIndexCreationStamp(indexId)));
  }

  private boolean shouldIndexFile(final VirtualFile file, final ID<?, ?> indexId) {
    return getInputFilter(indexId).acceptInput(file) &&
           (isMock(file) || !IndexingStamp.isFileIndexed(file, indexId, IndexInfrastructure.getIndexCreationStamp(indexId)));
  }

  private boolean isUnderConfigOrSystem(VirtualFile file) {
    final String filePath = file.getPath();
    if (myConfigPath != null && FileUtil.startsWith(filePath, myConfigPath)) {
      return true;
    }
    if (mySystemPath != null && FileUtil.startsWith(filePath, mySystemPath)) {
      return true;
    }
    return false;
  }

  private static boolean isMock(final VirtualFile file) {
    return !(file instanceof NewVirtualFile);
  }

  private boolean isTooLarge(VirtualFile file) {
    if (SingleRootFileViewProvider.isTooLarge(file)) {
      final FileType type = FileTypeManager.getInstance().getFileTypeByFile(file);
      return !myNoLimitCheckTypes.contains(type);
    }
    return false;
  }

  public CollectingContentIterator createContentIterator() {
    return new UnindexedFilesFinder();
  }

  public void registerIndexableSet(IndexableFileSet set) {
    myIndexableSets.add(set);
  }

  public void removeIndexableSet(IndexableFileSet set) {
    myChangedFilesCollector.forceUpdate(null, null, true);
    myIndexableSets.remove(set);
  }

  @Nullable
  private static PsiFile findLatestKnownPsiForUncomittedDocument(Document doc, Project project) {
    return PsiDocumentManager.getInstance(project).getCachedPsiFile(doc);
  }
  
  private static class IndexableFilesFilter implements InputFilter {
    private final InputFilter myDelegate;

    private IndexableFilesFilter(InputFilter delegate) {
      myDelegate = delegate;
    }

    public boolean acceptInput(final VirtualFile file) {
      return file instanceof VirtualFileWithId && myDelegate.acceptInput(file);
    }
  }

  private static void cleanupProcessedFlag() {
    final VirtualFile[] roots = ManagingFS.getInstance().getRoots();
    for (VirtualFile root : roots) {
      cleanProcessedFlag(root);
    }
  }

  private static void cleanProcessedFlag(final VirtualFile file) {
    if (!(file instanceof NewVirtualFile)) return;
    
    final NewVirtualFile nvf = (NewVirtualFile)file;
    if (file.isDirectory()) {
      for (VirtualFile child : nvf.getCachedChildren()) {
        cleanProcessedFlag(child);
      }
    }
    else {
/*      nvf.clearCachedFileType(); */
      nvf.setFlag(ALREADY_PROCESSED, false);
    }
  }

  public static void iterateIndexableFiles(final ContentIterator processor, Project project) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    // iterate associated libraries
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    // iterate project content
    projectFileIndex.iterateContent(processor);

    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

    Set<VirtualFile> visitedRoots = new com.intellij.util.containers.HashSet<VirtualFile>();
    for (IndexedRootsProvider provider : Extensions.getExtensions(IndexedRootsProvider.EP_NAME)) {
      //important not to depend on project here, to support per-project background reindex
      // each client gives a project to FileBasedIndex
      final Set<String> rootsToIndex = provider.getRootsToIndex();
      for (String url : rootsToIndex) {
        final VirtualFile root = VirtualFileManager.getInstance().findFileByUrl(url);
        if (visitedRoots.add(root)) {
          iterateRecursively(root, processor, indicator);
        }
      }
    }
    for (Module module : modules) {
      OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry) {
          final VirtualFile[] libSources = orderEntry.getFiles(OrderRootType.SOURCES);
          final VirtualFile[] libClasses = orderEntry.getFiles(OrderRootType.CLASSES);
          for (VirtualFile[] roots : new VirtualFile[][]{libSources, libClasses}) {
            for (VirtualFile root : roots) {
              if (visitedRoots.add(root)) {
                iterateRecursively(root, processor, indicator);
              }
            }
          }
        }
      }
    }
  }

  private static void iterateRecursively(@Nullable final VirtualFile root, final ContentIterator processor, ProgressIndicator indicator) {
    if (root != null) {
      if (indicator != null) {
        indicator.setText2(root.getPresentableUrl());
      }

      if (root.isDirectory()) {
        for (VirtualFile file : root.getChildren()) {
          if (file.isDirectory()) {
            iterateRecursively(file, processor, indicator);
          }
          else {
            processor.processFile(file);
          }
        }
      } else {
        processor.processFile(root);
      }
    }
  }

  private static class StorageGuard {
    private int myHolds = 0;

    public interface Holder {
      void leave();
    }

    private final Holder myTrueHolder = new Holder() {
      public void leave() {
        StorageGuard.this.leave(true);
      }
    };
    private final Holder myFalseHolder = new Holder() {
      public void leave() {
        StorageGuard.this.leave(false);
      }
    };

    public synchronized Holder enter(boolean mode) {
      if (mode) {
        while (myHolds < 0) {
          try {
            wait();
          }
          catch (InterruptedException ignored) {
          }
        }
        myHolds++;
        return myTrueHolder;
      }
      else {
        while (myHolds > 0) {
          try {
            wait();
          }
          catch (InterruptedException ignored) {
          }
        }
        myHolds--;
        return myFalseHolder;
      }
    }

    private synchronized void leave(boolean mode) {
      myHolds += (mode? -1 : 1);
      if (myHolds == 0) {
        notifyAll();
      }
    }

  }
}
