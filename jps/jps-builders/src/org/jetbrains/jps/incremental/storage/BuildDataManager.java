package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.artifacts.ArtifactsBuildData;

import java.io.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/7/11
 */
public class BuildDataManager implements StorageOwner {
  private static final int VERSION = 12;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.storage.BuildDataManager");
  private static final String SRC_TO_FORM_STORAGE = "src-form";
  private static final String MAPPINGS_STORAGE = "mappings";

  private final Object mySourceToOutputLock = new Object();
  private final Map<BuildTarget<?>, SourceToOutputMappingImpl> mySourceToOutputs = new HashMap<BuildTarget<?>, SourceToOutputMappingImpl>();

  private final SourceToFormMapping mySrcToFormMap;
  private final ArtifactsBuildData myArtifactsBuildData;
  private final ModuleOutputRootsLayout myOutputRootsLayout;
  private final Mappings myMappings;
  private final BuildDataPaths myDataPaths;
  private final BuildTargetsState myTargetsState;
  private final File myVersionFile;

  public BuildDataManager(final BuildDataPaths dataPaths, BuildTargetsState targetsState, final boolean useMemoryTempCaches) throws IOException {
    myDataPaths = dataPaths;
    myTargetsState = targetsState;
    mySrcToFormMap = new SourceToFormMapping(new File(getSourceToFormsRoot(), "data"));
    myOutputRootsLayout = new ModuleOutputRootsLayout(new File(getOutputsLayoutRoot(), "data"));
    myMappings = new Mappings(getMappingsRoot(), useMemoryTempCaches);
    myArtifactsBuildData = new ArtifactsBuildData(new File(dataPaths.getDataStorageRoot(), "artifacts"));
    myVersionFile = new File(myDataPaths.getDataStorageRoot(), "version.dat");
  }

  private File getOutputsLayoutRoot() {
    return new File(myDataPaths.getDataStorageRoot(), "output-roots");
  }

  public SourceToOutputMappingImpl getSourceToOutputMap(final BuildTarget<?> target) throws IOException {
    SourceToOutputMappingImpl mapping;
    synchronized (mySourceToOutputLock) {
      mapping = mySourceToOutputs.get(target);
      if (mapping == null) {
        mapping = new SourceToOutputMappingImpl(new File(myDataPaths.getTargetDataRoot(target), "src-out" + File.separator + "data"));
        mySourceToOutputs.put(target, mapping);
      }
    }
    return mapping;
  }

  public ArtifactsBuildData getArtifactsBuildData() {
    return myArtifactsBuildData;
  }

  public SourceToFormMapping getSourceToFormMap() {
    return mySrcToFormMap;
  }

  public ModuleOutputRootsLayout getOutputRootsLayout() {
    return myOutputRootsLayout;
  }

  public Mappings getMappings() {
    return myMappings;
  }

  public void clean() throws IOException {
    try {
      myArtifactsBuildData.clean();
    }
    finally {
      try {
        synchronized (mySourceToOutputLock) {
          closeSourceToOutputStorages();
        }
      }
      finally {
        try {
          wipeStorage(getSourceToFormsRoot(), mySrcToFormMap);
        }
        finally {
          try {
            wipeStorage(getOutputsLayoutRoot(), myOutputRootsLayout);
          }
          finally {
            final Mappings mappings = myMappings;
            if (mappings != null) {
              synchronized (mappings) {
                mappings.clean();
              }
            }
            else {
              FileUtil.delete(getMappingsRoot());
            }
          }
        }
      }
      myTargetsState.clean();
    }
    saveVersion();
  }

  public void flush(boolean memoryCachesOnly) {
    myArtifactsBuildData.flush(memoryCachesOnly);
    synchronized (mySourceToOutputLock) {
      for (SourceToOutputMappingImpl mapping : mySourceToOutputs.values()) {
        mapping.flush(memoryCachesOnly);
      }
    }
    mySrcToFormMap.flush(memoryCachesOnly);
    myOutputRootsLayout.flush(memoryCachesOnly);
    final Mappings mappings = myMappings;
    if (mappings != null) {
      synchronized (mappings) {
        mappings.flush(memoryCachesOnly);
      }
    }
  }

  public void close() throws IOException {
    try {
      myTargetsState.save();
      myArtifactsBuildData.close();
    }
    finally {
      try {
        synchronized (mySourceToOutputLock) {
          closeSourceToOutputStorages();
        }
      }
      finally {
        try {
          closeStorage(mySrcToFormMap);
        }
        finally {
          try {
            closeStorage(myOutputRootsLayout);
          }
          finally {
            final Mappings mappings = myMappings;
            if (mappings != null) {
              try {
                mappings.close();
              }
              catch (RuntimeException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                  throw ((IOException)cause);
                }
                throw e;
              }
            }
          }
        }
      }
    }
  }

  public void closeSourceToOutputStorages(Collection<BuildTargetChunk> chunks) throws IOException {
    synchronized (mySourceToOutputLock) {
      for (BuildTargetChunk chunk : chunks) {
        for (BuildTarget<?> target : chunk.getTargets()) {
          final SourceToOutputMappingImpl mapping = mySourceToOutputs.remove(target);
          if (mapping != null) {
            mapping.close();
          }
        }
      }
    }
  }

  private void closeSourceToOutputStorages() throws IOException {
    IOException ex = null;
    try {
      for (SourceToOutputMappingImpl mapping : mySourceToOutputs.values()) {
        try {
          mapping.close();
        }
        catch (IOException e) {
          if (e != null) {
            ex = e;
          }
        }
      }
    }
    finally {
      mySourceToOutputs.clear();
    }
    if (ex != null) {
      throw ex;
    }
  }

  private File getSourceToFormsRoot() {
    return new File(myDataPaths.getDataStorageRoot(), SRC_TO_FORM_STORAGE);
  }

  private File getMappingsRoot() {
    return new File(myDataPaths.getDataStorageRoot(), MAPPINGS_STORAGE);
  }

  public BuildDataPaths getDataPaths() {
    return myDataPaths;
  }

  private static void wipeStorage(File root, @Nullable AbstractStateStorage<?, ?> storage) {
    if (storage != null) {
      synchronized (storage) {
        storage.wipe();
      }
    }
    else {
      FileUtil.delete(root);
    }
  }

  private static void closeStorage(@Nullable AbstractStateStorage<?, ?> storage) throws IOException {
    if (storage != null) {
      synchronized (storage) {
        storage.close();
      }
    }
  }

  private Boolean myVersionDiffers = null;

  public boolean versionDiffers() {
    final Boolean cached = myVersionDiffers;
    if (cached != null) {
      return cached;
    }
    try {
      final DataInputStream is = new DataInputStream(new FileInputStream(myVersionFile));
      try {
        final boolean diff = is.readInt() != VERSION;
        myVersionDiffers = diff;
        return diff;
      }
      finally {
        is.close();
      }
    }
    catch (FileNotFoundException ignored) {
      return false; // treat it as a new dir
    }
    catch (IOException ex) {
      LOG.info(ex);
    }
    return true;
  }

  public void saveVersion() {
    final Boolean differs = myVersionDiffers;
    if (differs == null || differs) {
      try {
        FileUtil.createIfDoesntExist(myVersionFile);
        final DataOutputStream os = new DataOutputStream(new FileOutputStream(myVersionFile));
        try {
          os.writeInt(VERSION);
          myVersionDiffers = Boolean.FALSE;
        }
        finally {
          os.close();
        }
      }
      catch (IOException ignored) {
      }
    }
  }
}
