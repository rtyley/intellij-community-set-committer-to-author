package org.jetbrains.jps.incremental.fs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileSystemUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.CompileScope;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactRootDescriptor;
import org.jetbrains.jps.incremental.storage.Timestamps;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 12/16/11
 */
public class BuildFSState extends FSState {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.fs.BuildFSState");
  private static final Key<Set<? extends BuildTarget<?>>> CONTEXT_TARGETS_KEY = Key.create("_fssfate_context_targets_");
  private static final Key<FilesDelta> CURRENT_ROUND_DELTA_KEY = Key.create("_current_round_delta_");
  private static final Key<FilesDelta> LAST_ROUND_DELTA_KEY = Key.create("_last_round_delta_");

  // when true, will always determine dirty files by scanning FS and comparing timestamps
  // alternatively, when false, after first scan will rely on external notifications about changes
  private final boolean myAlwaysScanFS;

  public BuildFSState(boolean alwaysScanFS) {
    myAlwaysScanFS = alwaysScanFS;
  }

  @Override
  public boolean markInitialScanPerformed(BuildTarget<?> target) {
    return myAlwaysScanFS || super.markInitialScanPerformed(target);
  }

  @Override
  public Map<BuildRootDescriptor, Set<File>> getSourcesToRecompile(@NotNull CompileContext context, BuildTarget<?> target) {
    final FilesDelta lastRoundDelta = getRoundDelta(LAST_ROUND_DELTA_KEY, context);
    if (lastRoundDelta != null) {
      return lastRoundDelta.getSourcesToRecompile();
    }
    return super.getSourcesToRecompile(context, target);
  }

  @Override
  public boolean markDirty(@Nullable CompileContext context, File file, final BuildRootDescriptor rd, @Nullable Timestamps tsStorage) throws IOException {
    final FilesDelta roundDelta = getRoundDelta(CURRENT_ROUND_DELTA_KEY, context);
    if (roundDelta != null && isInCurrentContextTargets(context, rd)) {
      roundDelta.markRecompile(rd, file);
    }
    return super.markDirty(context, file, rd, tsStorage);
  }

  private static boolean isInCurrentContextTargets(CompileContext context, BuildRootDescriptor rd) {
    if (context == null) {
      return false;
    }
    Set<? extends BuildTarget<?>> targets = CONTEXT_TARGETS_KEY.get(context, Collections.<BuildTarget<?>>emptySet());
    return targets.contains(rd.getTarget());
  }

  @Override
  public boolean markDirtyIfNotDeleted(@Nullable CompileContext context, File file, final BuildRootDescriptor rd, @Nullable Timestamps tsStorage) throws IOException {
    final boolean marked = super.markDirtyIfNotDeleted(context, file, rd, tsStorage);
    if (marked) {
      final FilesDelta roundDelta = getRoundDelta(CURRENT_ROUND_DELTA_KEY, context);
      if (roundDelta != null) {
        if (isInCurrentContextTargets(context, rd)) {
          roundDelta.markRecompile(rd, file);
        }
      }
    }
    return marked;
  }

  public void clearAll() {
    clearContextRoundData(null);
    clearContextChunk(null);
    myInitialScanPerformed.clear();
    super.clearAll();
  }

  public void clearContextRoundData(@Nullable CompileContext context) {
    setRoundDelta(CURRENT_ROUND_DELTA_KEY, context, null);
    setRoundDelta(LAST_ROUND_DELTA_KEY, context, null);
  }

  public void clearContextChunk(@Nullable CompileContext context) {
    setContextTargets(context, null);
  }

  public void beforeChunkBuildStart(@NotNull CompileContext context, ModuleChunk chunk) {
    setContextTargets(context, chunk.getTargets());
  }

  public void beforeNextRoundStart(@NotNull CompileContext context, ModuleChunk chunk) {
    setRoundDelta(LAST_ROUND_DELTA_KEY, context, getRoundDelta(CURRENT_ROUND_DELTA_KEY, context));
    setRoundDelta(CURRENT_ROUND_DELTA_KEY, context, new FilesDelta());
  }

  public <R extends BuildRootDescriptor, T extends BuildTarget<R>> boolean processFilesToRecompile(CompileContext context, final T target, final FileProcessor<R, T> processor) throws IOException {
    final Map<BuildRootDescriptor, Set<File>> data = getSourcesToRecompile(context, target);
    BuildRootIndex rootIndex = context.getProjectDescriptor().getBuildRootIndex();
    final CompileScope scope = context.getScope();
    synchronized (data) {
      for (Map.Entry<BuildRootDescriptor, Set<File>> entry : data.entrySet()) {
        //noinspection unchecked
        R root = (R)entry.getKey();
        FileFilter filter = rootIndex.getRootFilter(root);
        for (File file : entry.getValue()) {
          if (!scope.isAffected(target, file) || !filter.accept(file)) {
            continue;
          }
          if (!processor.apply(target, file, root)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  /**
   * @return true if marked something, false otherwise
   */
  public boolean markAllUpToDate(CompileContext context, final JavaSourceRootDescriptor rd, final Timestamps stamps) throws IOException {
    boolean marked = false;
    final FilesDelta delta = getDelta(rd.target);
    final Set<File> files = delta.clearRecompile(rd);
    if (files != null) {
      FileFilter filter = context.getProjectDescriptor().getBuildRootIndex().getRootFilter(rd);
      CompileScope scope = context.getScope();
      final long compilationStartStamp = context.getCompilationStartStamp();
      for (File file : files) {
        if (filter.accept(file)) {
          if (scope.isAffected(rd.target, file)) {
            final long stamp = FileSystemUtil.lastModified(file);
            if (!rd.isGeneratedSources && stamp > compilationStartStamp) {
              // if the file was modified after the compilation had started,
              // do not save the stamp considering file dirty
              delta.markRecompile(rd, file);
              if (Utils.IS_TEST_MODE) {
                LOG.info("Timestamp after compilation started; marking dirty again: " + file.getPath());
              }
            }
            else {
              marked = true;
              stamps.saveStamp(file, rd.target, stamp);
            }
          }
          else {
            if (Utils.IS_TEST_MODE) {
              LOG.info("Not affected by compile scope; marking dirty again: " + file.getPath());
            }
            delta.markRecompile(rd, file);
          }
        }
        else {
          stamps.removeStamp(file, rd.target);
        }
      }
    }
    return marked;
  }

  public boolean markAllUpToDate(ArtifactRootDescriptor descriptor, Timestamps storage, long compilationStartStamp)
    throws IOException {
    boolean marked = false;
    FilesDelta delta = getDelta(descriptor.getTarget());
    Set<File> files = delta.clearRecompile(descriptor);
    if (files != null) {
      for (File file : files) {
        long stamp = FileSystemUtil.lastModified(file);
        marked = true;
        storage.saveStamp(file, descriptor.getTarget(), stamp);
      }
    }
    return marked;
  }

  private static void setContextTargets(@Nullable CompileContext context, @Nullable Set<? extends BuildTarget<?>> targets) {
    if (context != null) {
      CONTEXT_TARGETS_KEY.set(context, targets);
    }
  }

  @Nullable
  private static FilesDelta getRoundDelta(@NotNull Key<FilesDelta> key, @Nullable CompileContext context) {
    return context != null? key.get(context) : null;
  }

  private static void setRoundDelta(@NotNull Key<FilesDelta> key, @Nullable CompileContext context, @Nullable FilesDelta delta) {
    if (context != null) {
      key.set(context, delta);
    }
  }

}
