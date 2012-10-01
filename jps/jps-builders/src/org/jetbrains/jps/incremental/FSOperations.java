package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileSystemUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.JpsPathUtil;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.fs.RootDescriptor;
import org.jetbrains.jps.incremental.storage.Timestamps;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleType;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 7/8/12
 */
public class FSOperations {
  public static void markDirty(CompileContext context, final File file) throws IOException {
    final RootDescriptor rd = context.getProjectDescriptor().getBuildRootIndex().getModuleAndRoot(context, file);
    if (rd != null) {
      final ProjectDescriptor pd = context.getProjectDescriptor();
      pd.fsState.markDirty(context, file, rd, pd.timestamps.getStorage());
    }
  }

  public static void markDirtyIfNotDeleted(CompileContext context, final File file) throws IOException {
    final RootDescriptor rd = context.getProjectDescriptor().getBuildRootIndex().getModuleAndRoot(context, file);
    if (rd != null) {
      final ProjectDescriptor pd = context.getProjectDescriptor();
      pd.fsState.markDirtyIfNotDeleted(context, file, rd, pd.timestamps.getStorage());
    }
  }

  public static void markDeleted(CompileContext context, File file) throws IOException {
    final RootDescriptor rd = context.getProjectDescriptor().getBuildRootIndex().getModuleAndRoot(context, file);
    if (rd != null) {
      final ProjectDescriptor pd = context.getProjectDescriptor();
      pd.fsState.registerDeleted(rd.target, file, pd.timestamps.getStorage());
    }
  }

  public static void markDirty(CompileContext context, final ModuleChunk chunk) throws IOException {
    final ProjectDescriptor pd = context.getProjectDescriptor();
    pd.fsState.clearContextRoundData(context);
    for (ModuleBuildTarget target : chunk.getTargets()) {
      markDirtyFiles(context, target, pd.timestamps.getStorage(), true, null);
    }
  }

  public static void markDirtyRecursively(CompileContext context, ModuleChunk chunk) throws IOException {
    Set<JpsModule> modules = chunk.getModules();
    Set<ModuleBuildTarget> targets = chunk.getTargets();
    final Set<ModuleBuildTarget> dirtyTargets = new HashSet<ModuleBuildTarget>(targets);

    // now mark all modules that depend on dirty modules
    final JpsJavaClasspathKind classpathKind = JpsJavaClasspathKind.compile(chunk.containsTests());
    boolean found = false;
    for (BuildTargetChunk targetChunk : context.getProjectDescriptor().getBuildTargetIndex().getSortedTargetChunks()) {
      if (!found) {
        if (targetChunk.getTargets().equals(chunk.getTargets())) {
          found = true;
        }
      }
      else {
        for (final BuildTarget<?> target : targetChunk.getTargets()) {
          if (target instanceof ModuleBuildTarget) {
            final Set<JpsModule> deps = getDependentModulesRecursively(((ModuleBuildTarget)target).getModule(), classpathKind);
            if (Utils.intersects(deps, modules)) {
              for (BuildTarget<?> buildTarget : targetChunk.getTargets()) {
                if (buildTarget instanceof ModuleBuildTarget) {
                  dirtyTargets.add((ModuleBuildTarget)buildTarget);
                }
              }
              break;
            }
          }
        }
      }
    }

    final Timestamps timestamps = context.getProjectDescriptor().timestamps.getStorage();
    for (ModuleBuildTarget target : dirtyTargets) {
      markDirtyFiles(context, target, timestamps, true, null);
    }

    if (context.isMake()) {
      // mark as non-incremental only the module that triggered non-incremental change
      for (ModuleBuildTarget target : targets) {
        context.markNonIncremental(target);
      }
    }
  }

  private static Set<JpsModule> getDependentModulesRecursively(final JpsModule module, final JpsJavaClasspathKind kind) {
    return JpsJavaExtensionService.dependencies(module).includedIn(kind).recursively().exportedOnly().getModules();
  }

  public static void processFilesToRecompile(CompileContext context, ModuleChunk chunk, FileProcessor processor) throws IOException {
    //noinspection unchecked
    processFilesToRecompile(context, chunk, Condition.TRUE, processor);
  }

  public static void processFilesToRecompile(final CompileContext context,
                                             final ModuleChunk chunk,
                                             final JpsModuleType moduleType,
                                             final FileProcessor processor) throws IOException {
    final Condition<JpsModule> moduleFilter = new Condition<JpsModule>() {
      public boolean value(final JpsModule module) {
        return module.getModuleType() == moduleType;
      }
    };

    processFilesToRecompile(context, chunk, moduleFilter, processor);
  }

  public static void processFilesToRecompile(final CompileContext context,
                                             final ModuleChunk chunk,
                                             final Condition<JpsModule> moduleFilter,
                                             final FileProcessor processor) throws IOException {
    final BuildFSState fsState = context.getProjectDescriptor().fsState;
    for (ModuleBuildTarget target : chunk.getTargets()) {
      if (moduleFilter.value(target.getModule())) {
        fsState.processFilesToRecompile(context, target, processor);
      }
    }
  }

  static void markDirtyFiles(CompileContext context,
                             ModuleBuildTarget target,
                             Timestamps timestamps, boolean forceMarkDirty,
                             @Nullable THashSet<File> currentFiles) throws IOException {
    final ModuleExcludeIndex rootsIndex = context.getProjectDescriptor().moduleExcludeIndex;
    final Set<File> excludes = new HashSet<File>(rootsIndex.getModuleExcludes(target.getModule()));
    for (RootDescriptor rd : context.getProjectDescriptor().getBuildRootIndex().getTargetRoots(target, context)) {
      if (!rd.root.exists()) {
        continue;
      }
      context.getProjectDescriptor().fsState.clearRecompile(rd);
      traverseRecursively(context, rd, rd.root, excludes, timestamps, forceMarkDirty, currentFiles);
    }
  }

  private static void traverseRecursively(CompileContext context, final RootDescriptor rd, final File file, Set<File> excludes, @NotNull final Timestamps tsStorage, final boolean forceDirty, @Nullable Set<File> currentFiles) throws IOException {
    final File[] children = file.listFiles();
    if (children != null) { // is directory
      if (children.length > 0 && !JpsPathUtil.isUnder(excludes, file)) {
        for (File child : children) {
          traverseRecursively(context, rd, child, excludes, tsStorage, forceDirty, currentFiles);
        }
      }
    }
    else { // is file
      boolean markDirty = forceDirty;
      if (!markDirty) {
        markDirty = tsStorage.getStamp(file, rd.target) != FileSystemUtil.lastModified(file);
      }
      if (markDirty) {
        // if it is full project rebuild, all storages are already completely cleared;
        // so passing null because there is no need to access the storage to clear non-existing data
        final Timestamps marker = context.isProjectRebuild() ? null : tsStorage;
        context.getProjectDescriptor().fsState.markDirty(context, file, rd, marker);
      }
      if (currentFiles != null) {
        currentFiles.add(file);
      }
    }
  }
}
