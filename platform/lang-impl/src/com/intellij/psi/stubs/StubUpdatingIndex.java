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
package com.intellij.psi.stubs;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;

public class StubUpdatingIndex extends CustomImplementationFileBasedIndexExtension<Integer, SerializedStubTree, FileContent> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.StubUpdatingIndex");

  public static final ID<Integer, SerializedStubTree> INDEX_ID = ID.create("Stubs");
  private static final int VERSION = 17;
  private static final DataExternalizer<SerializedStubTree> KEY_EXTERNALIZER = new DataExternalizer<SerializedStubTree>() {
    public void save(final DataOutput out, final SerializedStubTree v) throws IOException {
      byte[] value = v.getBytes();
      out.writeInt(value.length);
      out.write(value);
    }

    public SerializedStubTree read(final DataInput in) throws IOException {
      int len = in.readInt();
      byte[] result = new byte[len];
      in.readFully(result);
      return new SerializedStubTree(result);
    }
  };

  private static final FileBasedIndex.InputFilter INPUT_FILTER = new FileBasedIndex.InputFilter() {
    public boolean acceptInput(final VirtualFile file) {
      return canHaveStub(file);
    }
  };

  public static boolean canHaveStub(VirtualFile file) {
    final FileType fileType = file.getFileType();
    if (fileType instanceof LanguageFileType) {
      Language l = ((LanguageFileType)fileType).getLanguage();
      ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(l);
      if (parserDefinition == null) return false;

      final IFileElementType filetype = parserDefinition.getFileNodeType();
      return filetype instanceof IStubFileElementType &&
             (((IStubFileElementType)filetype).shouldBuildStubFor(file) ||
              IndexingStamp.isFileIndexed(file, INDEX_ID, IndexInfrastructure.getIndexCreationStamp(INDEX_ID)));
    }
    else if (fileType.isBinary()) {
      final BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
      return builder != null && builder.acceptsFile(file);
    }

    return false;
  }

  private static final KeyDescriptor<Integer> DATA_DESCRIPTOR = new KeyDescriptor<Integer>() {
    public int getHashCode(final Integer value) {
      return value.hashCode();
    }

    public boolean isEqual(final Integer val1, final Integer val2) {
      return val1.equals(val2);
    }

    public void save(final DataOutput out, final Integer value) throws IOException {
      out.writeInt(value.intValue());
    }

    public Integer read(final DataInput in) throws IOException {
      return in.readInt();
    }
  };

  public ID<Integer, SerializedStubTree> getName() {
    return INDEX_ID;
  }

  public int getCacheSize() {
    return 5; // no need to cache many serialized trees
  }

  public DataIndexer<Integer, SerializedStubTree, FileContent> getIndexer() {
    return new DataIndexer<Integer, SerializedStubTree, FileContent>() {
      @NotNull
      public Map<Integer, SerializedStubTree> map(final FileContent inputData) {
        final Map<Integer, SerializedStubTree> result = new HashMap<Integer, SerializedStubTree>();

        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            final StubElement rootStub = buildStubTree(inputData);
            if (rootStub == null) return;

            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            SerializationManager.getInstance().serialize(rootStub, bytes);

            final int key = Math.abs(FileBasedIndex.getFileId(inputData.getFile()));
            result.put(key, new SerializedStubTree(bytes.toByteArray()));
          }
        });

        return result;
      }
    };
  }

  @Nullable
  static StubElement buildStubTree(final FileContent inputData) {
    final FileType fileType = inputData.getFileType();

    if (fileType.isBinary()) {
      final BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
      assert builder != null;

      return builder.buildStubTree(inputData.getFile(), inputData.getContent(), inputData.getProject());
    }

    final LanguageFileType filetype = (LanguageFileType)fileType;
    Language l = filetype.getLanguage();
    final IFileElementType type = LanguageParserDefinitions.INSTANCE.forLanguage(l).getFileNodeType();

    PsiFile psi = inputData.getPsiFile();

    return ((IStubFileElementType)type).getBuilder().buildStubTree(psi);
  }

  public KeyDescriptor<Integer> getKeyDescriptor() {
    return DATA_DESCRIPTOR;
  }

  public DataExternalizer<SerializedStubTree> getValueExternalizer() {
    return KEY_EXTERNALIZER;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return INPUT_FILTER;
  }

  public boolean dependsOnFileContent() {
    return true;
  }

  public int getVersion() {
    return getCumulativeVersion();
  }

  private static int getCumulativeVersion() {
    int version = VERSION;
    for (final FileType fileType : FileTypeManager.getInstance().getRegisteredFileTypes()) {
      if (fileType instanceof LanguageFileType) {
        Language l = ((LanguageFileType)fileType).getLanguage();
        ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(l);
        if (parserDefinition != null) {
          final IFileElementType type = parserDefinition.getFileNodeType();
          if (type instanceof IStubFileElementType) {
            version += ((IStubFileElementType)type).getStubVersion();
          }
        }
      }
      else if (fileType.isBinary()) {
        final BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
        if (builder != null ) {
          version += builder.getStubVersion();
        }
      }
    }
    return version;
  }

  public UpdatableIndex<Integer, SerializedStubTree, FileContent> createIndexImplementation(final ID<Integer, SerializedStubTree> indexId,
                                                                                            final FileBasedIndex owner, IndexStorage<Integer, SerializedStubTree> storage) {
    if (storage instanceof MemoryIndexStorage) {
      final MemoryIndexStorage<Integer, SerializedStubTree> memStorage = (MemoryIndexStorage<Integer, SerializedStubTree>)storage;
      memStorage.addBufferingStateListsner(new MemoryIndexStorage.BufferingStateListener() {
        public void bufferingStateChanged(final boolean newState) {
          ((StubIndexImpl)StubIndexImpl.getInstance()).setDataBufferingEnabled(newState);
        }

        public void memoryStorageCleared() {
          ((StubIndexImpl)StubIndexImpl.getInstance()).cleanupMemoryStorage();
        }
      });
    }
    return new MyIndex(indexId, owner, storage, getIndexer());
  }

  /**
   * Schedules asynchronous rebuild
   * @param finishCallback
   */
  public static void scheduleStubIndicesRebuild(@Nullable final Runnable finishCallback, @NotNull final Project project) {
    final Runnable rebuildRunnable = new Runnable() {
      public void run() {
        final StubIndexImpl stubIndex = (StubIndexImpl)StubIndexImpl.getInstance();
        final Collection<StubIndexKey> allIndexKeys = stubIndex.getAllStubIndexKeys();
        try {
          for (StubIndexKey key : allIndexKeys) {
            stubIndex.getWriteLock(key).lock();
          }
          stubIndex.clearAllIndices();
          final Map<StubIndexKey, Map<Object, TIntArrayList>> empty = Collections.emptyMap();
          FileBasedIndex.getInstance().processAllValues(INDEX_ID, new FileBasedIndex.AllValuesProcessor<SerializedStubTree>() {
            public void process(final int inputId, final SerializedStubTree value) {
              final Map<StubIndexKey, Map<Object, TIntArrayList>> stubTree = new StubTree((PsiFileStub)value.getStub()).indexStubTree();
              updateStubIndices(getAffectedIndices(empty, stubTree), inputId, empty, stubTree);
            }
          }, project);
        }
        finally {
          for (StubIndexKey key : allIndexKeys) {
            stubIndex.getWriteLock(key).unlock();
          }
          if (finishCallback != null) {
            finishCallback.run();
          }
        }
      }
    };

    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      rebuildRunnable.run();
    }
    else {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          new Task.Modal(null, "Updating index", false) {
            public void run(@NotNull final ProgressIndicator indicator) {
              rebuildRunnable.run();
            }
          }.queue();
        }
      });
    }
  }

  private static void updateStubIndices(final Collection<StubIndexKey> indexKeys, final int inputId, final Map<StubIndexKey, Map<Object, TIntArrayList>> oldStubTree, final Map<StubIndexKey, Map<Object, TIntArrayList>> newStubTree) {
    final StubIndexImpl stubIndex = (StubIndexImpl)StubIndex.getInstance();
    for (StubIndexKey key : indexKeys) {
      final Map<Object, TIntArrayList> oldMap = oldStubTree.get(key);
      final Map<Object, TIntArrayList> newMap = newStubTree.get(key);

      final Map<Object, TIntArrayList> _oldMap = oldMap != null ? oldMap : Collections.<Object, TIntArrayList>emptyMap();
      final Map<Object, TIntArrayList> _newMap = newMap != null ? newMap : Collections.<Object, TIntArrayList>emptyMap();

      stubIndex.updateIndex(key, inputId, _oldMap, _newMap);
    }
  }

  private static Collection<StubIndexKey> getAffectedIndices(final Map<StubIndexKey, Map<Object, TIntArrayList>> oldStubTree, final Map<StubIndexKey, Map<Object, TIntArrayList>> newStubTree) {
    Set<StubIndexKey> allIndices = new HashSet<StubIndexKey>();
    allIndices.addAll(oldStubTree.keySet());
    allIndices.addAll(newStubTree.keySet());
    return allIndices;
  }

  private static class MyIndex extends MapReduceIndex<Integer, SerializedStubTree, FileContent> {
    private final StubIndexImpl myStubIndex;

    public MyIndex(final ID<Integer, SerializedStubTree> indexId, final FileBasedIndex owner, final IndexStorage<Integer, SerializedStubTree> storage,
                   final DataIndexer<Integer, SerializedStubTree, FileContent> indexer) {
      super(indexId, indexer, storage);
      myStubIndex = (StubIndexImpl)StubIndex.getInstance();
      try {
        checkNameStorage();
      }
      catch (StorageException e) {
        LOG.info(e);
        owner.requestRebuild(INDEX_ID);
      }
    }

    protected void updateWithMap(final int inputId, final Map<Integer, SerializedStubTree> newData, Callable<Collection<Integer>> oldKeysGetter)
        throws StorageException {

      checkNameStorage();
      final Map<StubIndexKey, Map<Object, TIntArrayList>> newStubTree = getStubTree(newData);

      final StubIndexImpl stubIndex = myStubIndex;
      final Collection<StubIndexKey> allStubIndices = stubIndex.getAllStubIndexKeys();
      try {
        // first write-lock affected stub indices to avoid deadlocks
        for (StubIndexKey key : allStubIndices) {
          stubIndex.getWriteLock(key).lock();
        }

        try {
          getWriteLock().lock();

          final Map<Integer, SerializedStubTree> oldData = readOldData(inputId);
          final Map<StubIndexKey, Map<Object, TIntArrayList>> oldStubTree = getStubTree(oldData);

          super.updateWithMap(inputId, newData, oldKeysGetter);

          updateStubIndices(getAffectedIndices(oldStubTree, newStubTree), inputId, oldStubTree, newStubTree);
        }
        finally {
          getWriteLock().unlock();
        }

      }
      finally {
        for (StubIndexKey key : allStubIndices) {
          stubIndex.getWriteLock(key).unlock();
        }
      }
    }

    private static void checkNameStorage() throws StorageException {
      final SerializationManager serializationManager = SerializationManager.getInstance();
      if (serializationManager.isNameStorageCorrupted()) {
        serializationManager.repairNameStorage();
        //noinspection ThrowFromFinallyBlock
        throw new StorageException("NameStorage for stubs serialization has been corrupted");
      }
    }

    private static Map<StubIndexKey, Map<Object, TIntArrayList>> getStubTree(final Map<Integer, SerializedStubTree> data) {
      final Map<StubIndexKey, Map<Object, TIntArrayList>> stubTree;
      if (!data.isEmpty()) {
        final SerializedStubTree stub = data.values().iterator().next();
        stubTree = new StubTree((PsiFileStub)stub.getStub()).indexStubTree();
      }
      else {
        stubTree = Collections.emptyMap();
      }
      return stubTree;
    }

    /*MUST be called from under the WriteLock*/
    private Map<Integer, SerializedStubTree> readOldData(final int key) throws StorageException {
      final Map<Integer, SerializedStubTree> result = new HashMap<Integer, SerializedStubTree>();

      final ValueContainer<SerializedStubTree> valueContainer = myStorage.read(key);
      if (valueContainer.size() != 1) {
        LOG.assertTrue(valueContainer.size() == 0);
        return result;
      }

      result.put(key, valueContainer.getValueIterator().next());
      return result;
    }

    public void clear() throws StorageException {
      final StubIndexImpl stubIndex = myStubIndex;
      try {
        for (StubIndexKey key : stubIndex.getAllStubIndexKeys()) {
          stubIndex.getWriteLock(key).lock();
        }
        getWriteLock().lock();
        stubIndex.clearAllIndices();
        super.clear();
      }
      finally {
        getWriteLock().unlock();
        for (StubIndexKey key : stubIndex.getAllStubIndexKeys()) {
          stubIndex.getWriteLock(key).unlock();
        }
      }
    }

    public void dispose() {
      try {
        super.dispose();
      }
      finally {
        try {
          myStubIndex.dispose();
        }
        finally {
          ((SerializationManagerImpl)SerializationManager.getInstance()).disposeComponent();
        }
      }
    }
  }
}
