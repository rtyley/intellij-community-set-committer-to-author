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
package com.intellij.compiler.impl;

import com.intellij.ProjectTopics;
import com.intellij.compiler.CompileServerManager;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerIOUtil;
import com.intellij.compiler.make.MakeUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.IndexInfrastructure;
import com.intellij.util.io.*;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 3, 2008
 *
 * A source file is scheduled for recompilation if
 * 1. its timestamp has changed
 * 2. one of its corresponding output files was deleted
 * 3. output root of containing module has changed
 *
 * An output file is scheduled for deletion if:
 * 1. corresponding source file has been scheduled for recompilation (see above)
 * 2. corresponding source file has been deleted
 */

public class TranslatingCompilerFilesMonitor implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.TranslatingCompilerFilesMonitor");
  public static boolean ourDebugMode = false;
  @NonNls
  private static final String PATHS_TO_DELETE_FILENAME = "paths_to_delete.dat";
  private static final String OUTPUT_ROOTS_FILENAME = "output_roots.dat";
  private static final FileAttribute ourSourceFileAttribute = new FileAttribute("_make_source_file_info_", 3);
  private static final FileAttribute ourOutputFileAttribute = new FileAttribute("_make_output_file_info_", 3);
  private static final Key<Map<String, VirtualFile>> SOURCE_FILES_CACHE = Key.create("_source_url_to_vfile_cache_");

  private final Object myDataLock = new Object();
  private final TIntObjectHashMap<TIntHashSet> mySourcesToRecompile = new TIntObjectHashMap<TIntHashSet>(); // ProjectId->set of source file paths
  private PersistentHashMap<Integer, TIntObjectHashMap<Pair<Integer, Integer>>> myOutputRootsStorage; // ProjectId->map[moduleId->Pair(outputDirId, testOutputDirId)]
  private final TIntObjectHashMap<Map<String, SourceUrlClassNamePair>> myOutputsToDelete = new TIntObjectHashMap<Map<String, SourceUrlClassNamePair>>(); // Map: projectId -> Map{output path -> [sourceUrl; classname]}
  private final SLRUCache<Project, File> myGeneratedDataPaths = new SLRUCache<Project, File>(8, 8) {
    @NotNull
    public File createValue(final Project project) {
      Disposer.register(project, new Disposable() {
        public void dispose() {
          myGeneratedDataPaths.remove(project);
        }
      });
      return CompilerPaths.getGeneratedDataDirectory(project);
    }
  };
  private final SLRUCache<Integer, TIntObjectHashMap<Pair<Integer, Integer>>> myProjectOutputRoots = new SLRUCache<Integer, TIntObjectHashMap<Pair<Integer, Integer>>>(2, 2) {
    protected void onDropFromCache(Integer key, TIntObjectHashMap<Pair<Integer, Integer>> value) {
      try {
        myOutputRootsStorage.put(key, value);
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }

    @NotNull
    public TIntObjectHashMap<Pair<Integer, Integer>> createValue(Integer key) {
      TIntObjectHashMap<Pair<Integer, Integer>> map = null;
      try {
        ensureOutputStorageInitialized();
        map = myOutputRootsStorage.get(key);
      }
      catch (IOException e) {
        LOG.info(e);
      }
      return map != null? map : new TIntObjectHashMap<Pair<Integer, Integer>>();
    }
  };
  private final ProjectManager myProjectManager;
  private final TIntIntHashMap myInitInProgress = new TIntIntHashMap(); // projectId fior successfully initialized projects
  private final Object myAsyncScanLock = new Object();

  public TranslatingCompilerFilesMonitor(VirtualFileManager vfsManager, ProjectManager projectManager, Application application) {
    myProjectManager = projectManager;

    projectManager.addProjectManagerListener(new MyProjectManagerListener());
    vfsManager.addVirtualFileListener(new MyVfsListener(), application);
  }

  public static TranslatingCompilerFilesMonitor getInstance() {
    return ApplicationManager.getApplication().getComponent(TranslatingCompilerFilesMonitor.class);
  }

  @Nullable
  public static VirtualFile getSourceFileByOutput(VirtualFile outputFile) {
    final OutputFileInfo outputFileInfo = loadOutputInfo(outputFile);
    if (outputFileInfo != null) {
      final String path = outputFileInfo.getSourceFilePath();
      if (path != null) {
        return LocalFileSystem.getInstance().findFileByPath(path);
      }
    }
    return null;
  }

  public void collectFiles(CompileContext context, final TranslatingCompiler compiler, Iterator<VirtualFile> scopeSrcIterator, boolean forceCompile,
                           final boolean isRebuild,
                           Collection<VirtualFile> toCompile,
                           Collection<Trinity<File, String, Boolean>> toDelete) {
    final Project project = context.getProject();
    final int projectId = getProjectId(project);
    final CompilerConfiguration configuration = CompilerConfiguration.getInstance(project);
    final boolean _forceCompile = forceCompile || isRebuild;
    final Set<VirtualFile> selectedForRecompilation = new HashSet<VirtualFile>();
    synchronized (myDataLock) {
      final TIntHashSet pathsToRecompile = mySourcesToRecompile.get(projectId);
      if (_forceCompile || pathsToRecompile != null && !pathsToRecompile.isEmpty()) {
        if (ourDebugMode) {
          System.out.println("Analysing potentially recompilable files for " + compiler.getDescription());
        }
        while (scopeSrcIterator.hasNext()) {
          final VirtualFile file = scopeSrcIterator.next();
          if (!file.isValid()) {
            if (LOG.isDebugEnabled() || ourDebugMode) {
              LOG.debug("Skipping invalid file " + file.getPresentableUrl());
              if (ourDebugMode) {
                System.out.println("\t SKIPPED(INVALID) " + file.getPresentableUrl());
              }
            }
            continue;
          }
          final int fileId = getFileId(file);
          if (_forceCompile) {
            if (compiler.isCompilableFile(file, context) && !configuration.isExcludedFromCompilation(file)) {
              toCompile.add(file);
              if (ourDebugMode) {
                System.out.println("\t INCLUDED " + file.getPresentableUrl());
              }
              selectedForRecompilation.add(file);
              if (pathsToRecompile == null || !pathsToRecompile.contains(fileId)) {
                loadInfoAndAddSourceForRecompilation(projectId, file);
              }
            }
            else {
              if (ourDebugMode) {
                System.out.println("\t NOT COMPILABLE OR EXCLUDED " + file.getPresentableUrl());
              }
            }
          }
          else if (pathsToRecompile.contains(fileId)) {
            if (compiler.isCompilableFile(file, context) && !configuration.isExcludedFromCompilation(file)) {
              toCompile.add(file);
              if (ourDebugMode) {
                System.out.println("\t INCLUDED " + file.getPresentableUrl());
              }
              selectedForRecompilation.add(file);
            }
            else {
              if (ourDebugMode) {
                System.out.println("\t NOT COMPILABLE OR EXCLUDED " + file.getPresentableUrl());
              }
            }
          }
          else {
            if (ourDebugMode) {
              System.out.println("\t NOT INCLUDED " + file.getPresentableUrl());
            }
          }
        }
      }
      // it is important that files to delete are collected after the files to compile (see what happens if forceCompile == true)
      if (!isRebuild) {
        final Map<String, SourceUrlClassNamePair> outputsToDelete = myOutputsToDelete.get(projectId);
        if (outputsToDelete != null) {
          final VirtualFileManager vfm = VirtualFileManager.getInstance();
          final LocalFileSystem lfs = LocalFileSystem.getInstance();
          final List<String> zombieEntries = new ArrayList<String>();
          final Map<String, VirtualFile> srcFileCache = getFileCache(context);
          for (String outputPath : outputsToDelete.keySet()) {
            final SourceUrlClassNamePair classNamePair = outputsToDelete.get(outputPath);
            final String sourceUrl = classNamePair.getSourceUrl();

            final VirtualFile srcFile;
            if (srcFileCache.containsKey(sourceUrl)) {
              srcFile = srcFileCache.get(sourceUrl);
            }
            else {
              srcFile = vfm.findFileByUrl(sourceUrl);
              srcFileCache.put(sourceUrl, srcFile);
            }

            final boolean sourcePresent = srcFile != null;
            if (sourcePresent) {
              if (!compiler.isCompilableFile(srcFile, context)) {
                continue; // do not collect files that were compiled by another compiler
              }
              if (!selectedForRecompilation.contains(srcFile)) {
                if (!isMarkedForRecompilation(projectId, getFileId(srcFile))) {
                  if (LOG.isDebugEnabled() || ourDebugMode) {
                    final String message = "Found zombie entry (output is marked, but source is present and up-to-date): " + outputPath;
                    LOG.debug(message);
                    if (ourDebugMode) {
                      System.out.println(message);
                    }
                  }
                  zombieEntries.add(outputPath);
                }
                continue;
              }
            }
            if (lfs.findFileByPath(outputPath) != null) {
              //noinspection UnnecessaryBoxing
              final File file = new File(outputPath);
              toDelete.add(new Trinity<File, String, Boolean>(file, classNamePair.getClassName(), Boolean.valueOf(sourcePresent)));
              if (LOG.isDebugEnabled() || ourDebugMode) {
                final String message = "Found file to delete: " + file;
                LOG.debug(message);
                if (ourDebugMode) {
                  System.out.println(message);
                }
              }
            }
            else {
              if (LOG.isDebugEnabled() || ourDebugMode) {
                final String message = "Found zombie entry marked for deletion: " + outputPath;
                LOG.debug(message);
                if (ourDebugMode) {
                  System.out.println(message);
                }
              }
              // must be gagbage entry, should cleanup
              zombieEntries.add(outputPath);
            }
          }
          for (String path : zombieEntries) {
            unmarkOutputPathForDeletion(path);
          }
        }
      }
    }
  }

  private static Map<String, VirtualFile> getFileCache(CompileContext context) {
    Map<String, VirtualFile> cache = context.getUserData(SOURCE_FILES_CACHE);
    if (cache == null) {
      context.putUserData(SOURCE_FILES_CACHE, cache = new HashMap<String, VirtualFile>());
    }
    return cache;
  }

  private static int getFileId(final VirtualFile file) {
    return FileBasedIndex.getFileId(file);
  }

  private static VirtualFile findFileById(int id) {
    return IndexInfrastructure.findFileById((PersistentFS)ManagingFS.getInstance(), id);
  }

  public void update(final CompileContext context, @Nullable final String outputRoot, final Collection<TranslatingCompiler.OutputItem> successfullyCompiled, final VirtualFile[] filesToRecompile)
      throws IOException {
    final Project project = context.getProject();
    final int projectId = getProjectId(project);
    if (!successfullyCompiled.isEmpty()) {
      final LocalFileSystem lfs = LocalFileSystem.getInstance();
      final IOException[] exceptions = {null};
      // need read action here to ensure that no modifications were made to VFS while updating file attributes
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          try {
            final Map<VirtualFile, SourceFileInfo> compiledSources = new HashMap<VirtualFile, SourceFileInfo>();
            final Set<VirtualFile> forceRecompile = new HashSet<VirtualFile>();

            for (TranslatingCompiler.OutputItem item : successfullyCompiled) {
              final VirtualFile sourceFile = item.getSourceFile();
              final boolean isSourceValid = sourceFile.isValid();
              SourceFileInfo srcInfo = compiledSources.get(sourceFile);
              if (isSourceValid && srcInfo == null) {
                srcInfo = loadSourceInfo(sourceFile);
                if (srcInfo != null) {
                  srcInfo.clearPaths(projectId);
                }
                else {
                  srcInfo = new SourceFileInfo();
                }
                compiledSources.put(sourceFile, srcInfo);
              }

              final String outputPath = item.getOutputPath();
              if (outputPath != null) { // can be null for packageinfo
                final VirtualFile outputFile = lfs.findFileByPath(outputPath);

                //assert outputFile != null : "Virtual file was not found for \"" + outputPath + "\"";

                if (outputFile != null) {
                  if (!sourceFile.equals(outputFile)) {
                    final String className = outputRoot == null? null : MakeUtil.relativeClassPathToQName(outputPath.substring(outputRoot.length()), '/');
                    if (isSourceValid) {
                      srcInfo.addOutputPath(projectId, outputPath);
                      saveOutputInfo(outputFile, new OutputFileInfo(sourceFile.getPath(), className));
                    }
                    else {
                      markOutputPathForDeletion(projectId, outputPath, className, sourceFile.getUrl());
                    }
                  }
                }
                else {  // output file was not found
                  LOG.warn("TranslatingCompilerFilesMonitor.update():  Virtual file was not found for \"" + outputPath + "\"");
                  if (isSourceValid) {
                    forceRecompile.add(sourceFile);
                  }
                }
              }
            }
            final long compilationStartStamp = ((CompileContextEx)context).getStartCompilationStamp();
            for (Map.Entry<VirtualFile, SourceFileInfo> entry : compiledSources.entrySet()) {
              final SourceFileInfo info = entry.getValue();
              final VirtualFile file = entry.getKey();

              final long fileStamp = file.getTimeStamp();
              info.updateTimestamp(projectId, fileStamp);
              saveSourceInfo(file, info);
              if (LOG.isDebugEnabled() || ourDebugMode) {
                final String message = "Unschedule recompilation (successfully compiled) " + file.getPresentableUrl();
                LOG.debug(message);
                if (ourDebugMode) {
                  System.out.println(message);
                }
              }
              removeSourceForRecompilation(projectId, Math.abs(getFileId(file)));
              if (fileStamp > compilationStartStamp && !((CompileContextEx)context).isGenerated(file) || forceRecompile.contains(file)) {
                // changes were made during compilation, need to re-schedule compilation
                // it is important to invoke removeSourceForRecompilation() before this call to make sure
                // the corresponding output paths will be scheduled for deletion
                addSourceForRecompilation(projectId, file, info);
              }
            }
          }
          catch (IOException e) {
            exceptions[0] = e;
          }
        }
      });
      if (exceptions[0] != null) {
        throw exceptions[0];
      }
    }
    
    if (filesToRecompile.length > 0) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          for (VirtualFile file : filesToRecompile) {
            if (file.isValid()) {
              loadInfoAndAddSourceForRecompilation(projectId, file);
            }
          }
        }
      });
    }
  }

  public void updateOutputRootsLayout(Project project) {
    final TIntObjectHashMap<Pair<Integer, Integer>> map = buildOutputRootsLayout(new ProjectRef(project));
    synchronized (myProjectOutputRoots) {
      myProjectOutputRoots.put(getProjectId(project), map);
    }
  }

  @NotNull
  public String getComponentName() {
    return "TranslatingCompilerFilesMonitor";
  }

  public void initComponent() {
    final File file = new File(CompilerPaths.getCompilerSystemDirectory(), PATHS_TO_DELETE_FILENAME);
    try {
      final DataInputStream is = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
      try {
        final int projectsCount = is.readInt();
        synchronized (myDataLock) {
          for (int idx = 0; idx < projectsCount; idx++) {
            final int projectId = is.readInt();
            final int size = is.readInt();
            if (size > 0) {
              final Map<String, SourceUrlClassNamePair> map = new HashMap<String, SourceUrlClassNamePair>();
              myOutputsToDelete.put(projectId, map);
              for (int i = 0; i < size; i++) {
                final String outputPath = FileUtil.toSystemIndependentName(CompilerIOUtil.readString(is));
                final String srcUrl = CompilerIOUtil.readString(is);
                final String className = CompilerIOUtil.readString(is);
                if (LOG.isDebugEnabled() || ourDebugMode) {
                  final String message = "INIT path to delete: " + outputPath;
                  LOG.debug(message);
                  if (ourDebugMode) {
                    System.out.println(message);
                  }
                }
                map.put(outputPath, new SourceUrlClassNamePair(srcUrl, className));
              }
            }
          }
        }
      }
      finally {
        is.close();
      }
    }
    catch (FileNotFoundException ignored) {
    }
    catch (IOException e) {
      LOG.info(e);
      synchronized (myDataLock) {
        myOutputsToDelete.clear();
      }
      FileUtil.delete(file);
    }

    ensureOutputStorageInitialized();
  }

  private void ensureOutputStorageInitialized() {
    if (myOutputRootsStorage != null) {
      return;
    }
    final File rootsFile = new File(CompilerPaths.getCompilerSystemDirectory(), OUTPUT_ROOTS_FILENAME);
    try {
      initOutputRootsFile(rootsFile);
    }
    catch (IOException e) {
      LOG.info(e);
      FileUtil.delete(rootsFile);
      try {
        initOutputRootsFile(rootsFile);
      }
      catch (IOException e1) {
        LOG.error(e1);
      }
    }
  }

  private static TIntObjectHashMap<Pair<Integer, Integer>> buildOutputRootsLayout(ProjectRef projRef) {
    final TIntObjectHashMap<Pair<Integer, Integer>> map = new TIntObjectHashMap<Pair<Integer, Integer>>();
    for (Module module : ModuleManager.getInstance(projRef.get()).getModules()) {
      final CompilerModuleExtension manager = CompilerModuleExtension.getInstance(module);
      if (manager != null) {
        final VirtualFile output = manager.getCompilerOutputPath();
        final int first = output != null? Math.abs(getFileId(output)) : -1;
        final VirtualFile testsOutput = manager.getCompilerOutputPathForTests();
        final int second = testsOutput != null? Math.abs(getFileId(testsOutput)) : -1;
        map.put(getModuleId(module), new Pair<Integer, Integer>(first, second));
      }
    }
    return map;
  }

  private void initOutputRootsFile(File rootsFile) throws IOException {
    myOutputRootsStorage = new PersistentHashMap<Integer, TIntObjectHashMap<Pair<Integer, Integer>>>(rootsFile, new EnumeratorIntegerDescriptor(), new DataExternalizer<TIntObjectHashMap<Pair<Integer, Integer>>>() {
      public void save(DataOutput out, TIntObjectHashMap<Pair<Integer, Integer>> value) throws IOException {
        for (final TIntObjectIterator<Pair<Integer, Integer>> it = value.iterator(); it.hasNext();) {
          it.advance();
          DataInputOutputUtil.writeINT(out, it.key());
          final Pair<Integer, Integer> pair = it.value();
          DataInputOutputUtil.writeINT(out, pair.first);
          DataInputOutputUtil.writeINT(out, pair.second);
        }
      }

      public TIntObjectHashMap<Pair<Integer, Integer>> read(DataInput in) throws IOException {
        final DataInputStream _in = (DataInputStream)in;
        final TIntObjectHashMap<Pair<Integer, Integer>> map = new TIntObjectHashMap<Pair<Integer, Integer>>();
        while (_in.available() > 0) {
          final int key = DataInputOutputUtil.readINT(_in);
          final int first = DataInputOutputUtil.readINT(_in);
          final int second = DataInputOutputUtil.readINT(_in);
          map.put(key, new Pair<Integer, Integer>(first, second));
        }
        return map;
      }
    });
  }

  public void disposeComponent() {
    final File file = new File(CompilerPaths.getCompilerSystemDirectory(), PATHS_TO_DELETE_FILENAME);
    try {
      FileUtil.createParentDirs(file);
      final DataOutputStream os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
      try {
        synchronized (myDataLock) {
          final int[] keys = myOutputsToDelete.keys();
          os.writeInt(keys.length);
          for (int projectId : keys) {
            final Map<String, SourceUrlClassNamePair> projectOutputs = myOutputsToDelete.get(projectId);
            os.writeInt(projectId);
            if (projectOutputs != null) {
              os.writeInt(projectOutputs.size());
              for (Map.Entry<String, SourceUrlClassNamePair> entry : projectOutputs.entrySet()) {
                CompilerIOUtil.writeString(entry.getKey(), os);
                final SourceUrlClassNamePair pair = entry.getValue();
                CompilerIOUtil.writeString(pair.getSourceUrl(), os);
                CompilerIOUtil.writeString(pair.getClassName(), os);
              }
            }
            else {
              os.writeInt(0);
            }
          }
        }
      }
      finally {
        os.close();
        synchronized (myProjectOutputRoots) {
          myProjectOutputRoots.clear();
        }
        myOutputRootsStorage.close();
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Nullable
  private static SourceFileInfo loadSourceInfo(final VirtualFile file) {
    try {
      final DataInputStream is = ourSourceFileAttribute.readAttribute(file);
      if (is != null) {
        try {
          return new SourceFileInfo(is);
        }
        finally {
          is.close();
        }
      }
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        LOG.info(e); // ignore IOExceptions
      }
      else {
        throw e;
      }
    }
    catch (IOException ignored) {
      LOG.info(ignored);
    }
    return null;
  }

  public static void removeSourceInfo(VirtualFile file) {
    saveSourceInfo(file, new SourceFileInfo());
  }

  private static void saveSourceInfo(VirtualFile file, SourceFileInfo descriptor) {
    final DataOutputStream out = ourSourceFileAttribute.writeAttribute(file);
    try {
      try {
        descriptor.save(out);
      }
      finally {
        out.close();
      }
    }
    catch (IOException ignored) {
      LOG.info(ignored);
    }
  }

  @Nullable
  private static OutputFileInfo loadOutputInfo(final VirtualFile file) {
    try {
      final DataInputStream is = ourOutputFileAttribute.readAttribute(file);
      if (is != null) {
        try {
          return new OutputFileInfo(is);
        }
        finally {
          is.close();
        }
      }
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        LOG.info(e); // ignore IO exceptions
      }
      else {
        throw e;
      }
    }
    catch (IOException ignored) {
      LOG.info(ignored);
    }
    return null;
  }

  private static void saveOutputInfo(VirtualFile file, OutputFileInfo descriptor) {
    final DataOutputStream out = ourOutputFileAttribute.writeAttribute(file);
    try {
      try {
        descriptor.save(out);
      }
      finally {
        out.close();
      }
    }
    catch (IOException ignored) {
      LOG.info(ignored);
    }
  }

  private static int getProjectId(Project project) {
    try {
      return FSRecords.getNames().enumerate(project.getLocationHash());
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return -1;
  }

  private static int getModuleId(Module module) {
    try {
      return FSRecords.getNames().enumerate(module.getName());
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return -1;
  }

  private static class OutputFileInfo {
    private final int mySourcePath;

    private final int myClassName;

    OutputFileInfo(final String sourcePath, @Nullable String className) throws IOException {
      final PersistentStringEnumerator symtable = FSRecords.getNames();
      mySourcePath = symtable.enumerate(sourcePath);
      myClassName = className != null? symtable.enumerate(className) : -1;
    }

    OutputFileInfo(final DataInput in) throws IOException {
      mySourcePath = in.readInt();
      myClassName = in.readInt();
    }

    String getSourceFilePath() {
      try {
        return FSRecords.getNames().valueOf(mySourcePath);
      }
      catch (IOException e) {
        LOG.info(e);
      }
      return null;
    }

    @Nullable
    public String getClassName() {
      try {
        return myClassName < 0? null : FSRecords.getNames().valueOf(myClassName);
      }
      catch (IOException e) {
        LOG.info(e);
      }
      return null;
    }

    public void save(final DataOutput out) throws IOException {
      out.writeInt(mySourcePath);
      out.writeInt(myClassName);
    }
  }

  private static class SourceFileInfo {
    private TIntLongHashMap myTimestamps; // ProjectId -> last compiled stamp
    private TIntObjectHashMap<Serializable> myProjectToOutputPathMap; // ProjectId -> either a single output path or a set of output paths

    private SourceFileInfo() {
    }

    private SourceFileInfo(@NotNull DataInput in) throws IOException {
      final int projCount = in.readInt();
      for (int idx = 0; idx < projCount; idx++) {
        final int projectId = in.readInt();
        final long stamp = in.readLong();
        updateTimestamp(projectId, stamp);

        final int pathsCount = in.readInt();
        for (int i = 0; i < pathsCount; i++) {
          final int path = in.readInt();
          addOutputPath(projectId, path);
        }
      }
    }

    public void save(@NotNull final DataOutput out) throws IOException {
      final int[] projects = getProjectIds().toArray();
      out.writeInt(projects.length);
      for (int projectId : projects) {
        out.writeInt(projectId);
        out.writeLong(getTimestamp(projectId));
        final Object value = myProjectToOutputPathMap != null? myProjectToOutputPathMap.get(projectId) : null;
        if (value instanceof Integer) {
          out.writeInt(1);
          out.writeInt(((Integer)value).intValue());
        }
        else if (value instanceof TIntHashSet) {
          final TIntHashSet set = (TIntHashSet)value;
          out.writeInt(set.size());
          final IOException[] ex = new IOException[] {null};
          set.forEach(new TIntProcedure() {
            public boolean execute(final int value) {
              try {
                out.writeInt(value);
                return true;
              }
              catch (IOException e) {
                ex[0] = e;
                return false;
              }
            }
          });
          if (ex[0] != null) {
            throw ex[0];
          }
        }
        else {
          out.writeInt(0);
        }
      }
    }

    private void updateTimestamp(final int projectId, final long stamp) {
      if (stamp > 0L) {
        if (myTimestamps == null) {
          myTimestamps = new TIntLongHashMap(1, 0.98f);
        }
        myTimestamps.put(projectId, stamp);
      }
      else {
        if (myTimestamps != null) {
          myTimestamps.remove(projectId);
        }
      }
    }

    TIntHashSet getProjectIds() {
      final TIntHashSet result = new TIntHashSet();
      if (myTimestamps != null) {
        result.addAll(myTimestamps.keys());
      }
      if (myProjectToOutputPathMap != null) {
        result.addAll(myProjectToOutputPathMap.keys());
      }
      return result;
    }

    private void addOutputPath(final int projectId, String outputPath) {
      try {
        addOutputPath(projectId, FSRecords.getNames().enumerate(outputPath));
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }

    private void addOutputPath(final int projectId, final int outputPath) {
      if (myProjectToOutputPathMap == null) {
        myProjectToOutputPathMap = new TIntObjectHashMap<Serializable>(1, 0.98f);
        myProjectToOutputPathMap.put(projectId, outputPath);
      }
      else {
        final Object val = myProjectToOutputPathMap.get(projectId);
        if (val == null)  {
          myProjectToOutputPathMap.put(projectId, outputPath);
        }
        else {
          TIntHashSet set;
          if (val instanceof Integer)  {
            set = new TIntHashSet();
            set.add(((Integer)val).intValue());
            myProjectToOutputPathMap.put(projectId, set);
          }
          else {
            assert val instanceof TIntHashSet;
            set = (TIntHashSet)val;
          }
          set.add(outputPath);
        }
      }
    }

    public void clearPaths(final int projectId){
      if (myProjectToOutputPathMap != null) {
        myProjectToOutputPathMap.remove(projectId);
      }
    }

    long getTimestamp(final int projectId) {
      return myTimestamps == null? -1L : myTimestamps.get(projectId);
    }

    void processOutputPaths(final int projectId, final Proc proc){
      if (myProjectToOutputPathMap != null) {
        try {
          final PersistentStringEnumerator symtable = FSRecords.getNames();
          final Object val = myProjectToOutputPathMap.get(projectId);
          if (val instanceof Integer)  {
            proc.execute(projectId, symtable.valueOf(((Integer)val).intValue()));
          }
          else if (val instanceof TIntHashSet) {
            ((TIntHashSet)val).forEach(new TIntProcedure() {
              public boolean execute(final int value) {
                try {
                  proc.execute(projectId, symtable.valueOf(value));
                  return true;
                }
                catch (IOException e) {
                  LOG.info(e);
                  return false;
                }
              }
            });
          }
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
    }

    boolean isAssociated(int projectId, String outputPath) {
      if (myProjectToOutputPathMap != null) {
        try {
          final Object val = myProjectToOutputPathMap.get(projectId);
          if (val instanceof Integer)  {
            return FileUtil.pathsEqual(outputPath, FSRecords.getNames().valueOf(((Integer)val).intValue()));
          }
          if (val instanceof TIntHashSet) {
            final int _outputPath = FSRecords.getNames().enumerate(outputPath);
            return ((TIntHashSet)val).contains(_outputPath);
          }
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
      return false;
    }
  }

  public static List<String> getCompiledClassNames(VirtualFile srcFile, Project project) {
    SourceFileInfo info = loadSourceInfo(srcFile);
    if (info == null) {
      return Collections.emptyList();
    }

    final ArrayList<String> result = new ArrayList<String>();

    info.processOutputPaths(getProjectId(project), new Proc() {
      @Override
      public boolean execute(int projectId, String outputPath) {
        VirtualFile clsFile = LocalFileSystem.getInstance().findFileByPath(outputPath);
        if (clsFile != null) {
          OutputFileInfo outputInfo = loadOutputInfo(clsFile);
          if (outputInfo != null) {
            ContainerUtil.addIfNotNull(result, outputInfo.getClassName());
          }
        }
        return true;
      }
    });
    return result;
  }


  private interface FileProcessor {
    void execute(VirtualFile file);
  }

  private static void processRecursively(VirtualFile file, boolean dbOnly, final FileProcessor processor) {
    if (file.getFileSystem() instanceof LocalFileSystem) {
      if (file.isDirectory()) {
        if (dbOnly) {
          for (VirtualFile child : ((NewVirtualFile)file).iterInDbChildren()) {
            processRecursively(child, true, processor);
          }
        }
        else {
          for (VirtualFile child : file.getChildren()) {
            processRecursively(child, false, processor);
          }
        }
      }
      else {
        processor.execute(file);
      }
    }
  }

  // made public for tests
  public void scanSourceContent(final ProjectRef projRef, final Collection<VirtualFile> roots, final int totalRootCount, final boolean isNewRoots) {
    if (roots.isEmpty()) {
      return;
    }
    final int projectId = getProjectId(projRef.get());

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(projRef.get()).getFileIndex();
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    int processed = 0;
    for (VirtualFile srcRoot : roots) {
      if (indicator != null) {
        projRef.get();
        indicator.setText2(srcRoot.getPresentableUrl());
        indicator.setFraction(++processed / (double)totalRootCount);
      }
      if (isNewRoots) {
        fileIndex.iterateContentUnderDirectory(srcRoot, new ContentIterator() {
          public boolean processFile(final VirtualFile file) {
            if (!file.isDirectory()) {
              if (!isMarkedForRecompilation(projectId, Math.abs(getFileId(file)))) {
                final SourceFileInfo srcInfo = loadSourceInfo(file);
                if (srcInfo == null || srcInfo.getTimestamp(projectId) != file.getTimeStamp()) {
                  addSourceForRecompilation(projectId, file, srcInfo);
                }
              }
            }
            else {
              projRef.get();
            }
            return true;
          }
        });
      }
      else {
        final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
        new Object() {
          void processFile(VirtualFile file) {
            if (fileTypeManager.isFileIgnored(file)) {
              return;
            }
            final int fileId = getFileId(file);
            if (fileId > 0 /*file is valid*/) {
              if (file.isDirectory()) {
                projRef.get();
                for (VirtualFile child : file.getChildren()) {
                  processFile(child);
                }
              }
              else {
                if (!isMarkedForRecompilation(projectId, fileId)) {
                  final SourceFileInfo srcInfo = loadSourceInfo(file);
                  if (srcInfo != null) {
                    addSourceForRecompilation(projectId, file, srcInfo);
                  }
                }
              }
            }
          }
        }.processFile(srcRoot);
      }
    }
  }

  public void ensureInitializationCompleted(Project project, ProgressIndicator indicator) {
    final int id = getProjectId(project);
    synchronized (myAsyncScanLock) {
      while (myInitInProgress.containsKey(id)) {
        if (!project.isOpen() || project.isDisposed() || (indicator != null && indicator.isCanceled())) {
          // makes no sense to continue waiting
          break;
        }
        try {
          myAsyncScanLock.wait(500);
        }
        catch (InterruptedException ignored) {
          break;
        }
      }
    }
  }

  private void markOldOutputRoots(final ProjectRef projRef, final TIntObjectHashMap<Pair<Integer, Integer>> currentLayout) {
    final int projectId = getProjectId(projRef.get());

    final TIntHashSet rootsToMark = new TIntHashSet();
    synchronized (myProjectOutputRoots) {
      final TIntObjectHashMap<Pair<Integer, Integer>> oldLayout = myProjectOutputRoots.get(projectId);
      for (final TIntObjectIterator<Pair<Integer, Integer>> it = oldLayout.iterator(); it.hasNext();) {
        it.advance();
        final Pair<Integer, Integer> currentRoots = currentLayout.get(it.key());
        final Pair<Integer, Integer> oldRoots = it.value();
        if (shouldMark(oldRoots.first, currentRoots != null? currentRoots.first : -1)) {
          rootsToMark.add(oldRoots.first);
        }
        if (shouldMark(oldRoots.second, currentRoots != null? currentRoots.second : -1)) {
          rootsToMark.add(oldRoots.second);
        }
      }
    }

    for (TIntIterator it = rootsToMark.iterator(); it.hasNext();) {
      final int id = it.next();
      final VirtualFile outputRoot = findFileById(id);
      if (outputRoot != null) {
        processOldOutputRoot(projectId, outputRoot);
      }
    }
  }

  private static boolean shouldMark(Integer oldOutputRoot, Integer currentOutputRoot) {
    return oldOutputRoot != null && oldOutputRoot.intValue() > 0 && !Comparing.equal(oldOutputRoot, currentOutputRoot);
  }

  private void processOldOutputRoot(int projectId, VirtualFile outputRoot) {
    // recursively mark all corresponding sources for recompilation
    if (outputRoot.isDirectory()) {
      for (VirtualFile child : outputRoot.getChildren()) {
        processOldOutputRoot(projectId, child);
      }
    }
    else {
      // todo: possible optimization - process only those outputs that are not marked for deletion yet
      final OutputFileInfo outputInfo = loadOutputInfo(outputRoot);
      if (outputInfo != null) {
        final String srcPath = outputInfo.getSourceFilePath();
        final VirtualFile srcFile = srcPath != null? LocalFileSystem.getInstance().findFileByPath(srcPath) : null;
        if (srcFile != null) {
          loadInfoAndAddSourceForRecompilation(projectId, srcFile);
        }
      }
    }
  }

  public void scanSourcesForCompilableFiles(final Project project) {
    final int projectId = getProjectId(project);
    startAsyncScan(projectId);
    StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
      public void run() {
        new Task.Backgroundable(project, CompilerBundle.message("compiler.initial.scanning.progress.text"), false) {
          public void run(@NotNull final ProgressIndicator indicator) {
            final ProjectRef projRef = new ProjectRef(project);
            try {
              final IntermediateOutputCompiler[] compilers =
                  CompilerManager.getInstance(projRef.get()).getCompilers(IntermediateOutputCompiler.class);

              final Set<VirtualFile> intermediateRoots = new HashSet<VirtualFile>();
              if (compilers.length > 0) {
                final Module[] modules = ModuleManager.getInstance(projRef.get()).getModules();
                for (IntermediateOutputCompiler compiler : compilers) {
                  for (Module module : modules) {
                    if (module.isDisposed()) {
                      continue;
                    }
                    final VirtualFile outputRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(CompilerPaths.getGenerationOutputPath(compiler, module, false));
                    if (outputRoot != null) {
                      intermediateRoots.add(outputRoot);
                    }
                    final VirtualFile testsOutputRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(CompilerPaths.getGenerationOutputPath(compiler, module, true));
                    if (testsOutputRoot != null) {
                      intermediateRoots.add(testsOutputRoot);
                    }
                  }
                }
              }

              final List<VirtualFile> projectRoots = Arrays.asList(ProjectRootManager.getInstance(projRef.get()).getContentSourceRoots());
              final int totalRootsCount = projectRoots.size() + intermediateRoots.size();
              scanSourceContent(projRef, projectRoots, totalRootsCount, true);

              if (!intermediateRoots.isEmpty()) {
                final FileProcessor processor = new FileProcessor() {
                  public void execute(final VirtualFile file) {
                    if (!isMarkedForRecompilation(projectId, Math.abs(getFileId(file)))) {
                      final SourceFileInfo srcInfo = loadSourceInfo(file);
                      if (srcInfo == null || srcInfo.getTimestamp(projectId) != file.getTimeStamp()) {
                        addSourceForRecompilation(projectId, file, srcInfo);
                      }
                    }
                  }
                };
                int processed = projectRoots.size();
                for (VirtualFile root : intermediateRoots) {
                  projRef.get();
                  indicator.setText2(root.getPresentableUrl());
                  indicator.setFraction(++processed / (double)totalRootsCount);
                  processRecursively(root, false, processor);
                }
              }
              
              markOldOutputRoots(projRef, buildOutputRootsLayout(projRef));
            }
            catch (ProjectRef.ProjectClosedException swallowed) {
            }
            finally {
              terminateAsyncScan(projectId, false);
            }
          }
        }.queue();
      }
    });
  }

  private void terminateAsyncScan(int projectId, final boolean clearCounter) {
    synchronized (myAsyncScanLock) {
      int counter = myInitInProgress.remove(projectId);
      if (clearCounter) {
        myAsyncScanLock.notifyAll();
      }
      else {
        if (--counter > 0) {
          myInitInProgress.put(projectId, counter);
        }
        else {
          myAsyncScanLock.notifyAll();
        }
      }
    }
  }

  private void startAsyncScan(final int projectId) {
    synchronized (myAsyncScanLock) {
      int counter = myInitInProgress.get(projectId);
      counter = (counter > 0)? counter + 1 : 1;
      myInitInProgress.put(projectId, counter);
      myAsyncScanLock.notifyAll();
    }
  }

  private class MyProjectManagerListener extends ProjectManagerAdapter {

    final Map<Project, MessageBusConnection> myConnections = new HashMap<Project, MessageBusConnection>();

    public void projectOpened(final Project project) {
      final MessageBusConnection conn = project.getMessageBus().connect();
      myConnections.put(project, conn);
      final ProjectRef projRef = new ProjectRef(project);
      final int projectId = getProjectId(project);

      conn.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
        private VirtualFile[] myRootsBefore;
        private Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);

        public void beforeRootsChange(final ModuleRootEvent event) {
          try {
            myRootsBefore = ProjectRootManager.getInstance(projRef.get()).getContentSourceRoots();
          }
          catch (ProjectRef.ProjectClosedException e) {
            myRootsBefore = null;
          }
        }

        public void rootsChanged(final ModuleRootEvent event) {
          try {
            final VirtualFile[] rootsBefore = myRootsBefore;
            myRootsBefore = null;
            final VirtualFile[] rootsAfter = ProjectRootManager.getInstance(projRef.get()).getContentSourceRoots();
            final Set<VirtualFile> newRoots = new HashSet<VirtualFile>();
            final Set<VirtualFile> oldRoots = new HashSet<VirtualFile>();
            {
              if (rootsAfter.length > 0) {
                ContainerUtil.addAll(newRoots, rootsAfter);
              }
              if (rootsBefore != null) {
                newRoots.removeAll(Arrays.asList(rootsBefore));
              }
            }
            {
              if (rootsBefore != null) {
                ContainerUtil.addAll(oldRoots, rootsBefore);
              }
              if (!oldRoots.isEmpty() && rootsAfter.length > 0) {
                oldRoots.removeAll(Arrays.asList(rootsAfter));
              }
            }

            myAlarm.cancelAllRequests(); // need alarm to deal with multiple rootsChanged events
            myAlarm.addRequest(new Runnable() {
              public void run() {
                startAsyncScan(projectId);
                new Task.Backgroundable(project, CompilerBundle.message("compiler.initial.scanning.progress.text"), false) {
                  public void run(@NotNull final ProgressIndicator indicator) {
                    try {
                      if (newRoots.size() > 0) {
                        scanSourceContent(projRef, newRoots, newRoots.size(), true);
                      }
                      if (oldRoots.size() > 0) {
                        scanSourceContent(projRef, oldRoots, oldRoots.size(), false);
                      }
                      markOldOutputRoots(projRef, buildOutputRootsLayout(projRef));
                    }
                    catch (ProjectRef.ProjectClosedException swallowed) {
                      // ignored
                    }
                    finally {
                      terminateAsyncScan(projectId, false);
                    }
                  }
                }.queue();
              }
            }, 500, ModalityState.NON_MODAL);
          }
          catch (ProjectRef.ProjectClosedException e) {
            LOG.info(e);
          }
        }
      });

      scanSourcesForCompilableFiles(project);
    }

    public void projectClosed(final Project project) {
      final int projectId = getProjectId(project);
      terminateAsyncScan(projectId, true);
      myConnections.remove(project).disconnect();
      synchronized (myDataLock) {
        mySourcesToRecompile.remove(projectId);
      }
    }
  }

  private class MyVfsListener extends VirtualFileAdapter {
    public void propertyChanged(final VirtualFilePropertyEvent event) {
      if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
        final VirtualFile file = event.getFile();
        final VirtualFile parent = event.getParent();
        if (parent != null) {
          final String oldName = (String)event.getOldValue();
          CompileServerManager.getInstance().notifyFilesDeleted(Collections.singleton(parent.getPath() + "/" + oldName));
        }
        markDirtyIfSource(file, false);
      }
    }

    public void contentsChanged(final VirtualFileEvent event) {
      markDirtyIfSource(event.getFile(), false);
    }

    public void fileCreated(final VirtualFileEvent event) {
      processNewFile(event.getFile(), true);
    }

    public void fileCopied(final VirtualFileCopyEvent event) {
      processNewFile(event.getFile(), true);
    }

    public void fileMoved(VirtualFileMoveEvent event) {
      processNewFile(event.getFile(), true);
    }

    public void beforeFileDeletion(final VirtualFileEvent event) {
      final VirtualFile eventFile = event.getFile();
      if ((LOG.isDebugEnabled() && eventFile.isDirectory()) || ourDebugMode) {
        final String message = "Processing file deletion: " + eventFile.getPresentableUrl();
        LOG.debug(message);
        if (ourDebugMode) {
          System.out.println(message);
        }
      }

      final Set<String> pathsToMark = new HashSet<String>();

      processRecursively(eventFile, true, new FileProcessor() {
        public void execute(final VirtualFile file) {
          final String filePath = file.getPath();
          pathsToMark.add(filePath);
          try {
            final OutputFileInfo outputInfo = loadOutputInfo(file);
            if (outputInfo != null) {
              final String srcPath = outputInfo.getSourceFilePath();
              final VirtualFile srcFile = srcPath != null? LocalFileSystem.getInstance().findFileByPath(srcPath) : null;
              if (srcFile != null) {
                final SourceFileInfo srcInfo = loadSourceInfo(srcFile);
                if (srcInfo != null) {
                  final boolean srcWillBeDeleted = VfsUtil.isAncestor(eventFile, srcFile, false);
                  for (int projectId : srcInfo.getProjectIds().toArray()) {
                    if (srcInfo.isAssociated(projectId, filePath)) {
                      if (srcWillBeDeleted) {
                        if (LOG.isDebugEnabled() || ourDebugMode) {
                          final String message = "Unschedule recompilation because of deletion " + srcFile.getPresentableUrl();
                          LOG.debug(message);
                          if (ourDebugMode) {
                            System.out.println(message);
                          }
                        }
                        removeSourceForRecompilation(projectId, Math.abs(getFileId(srcFile)));
                      }
                      else {
                        addSourceForRecompilation(projectId, srcFile, srcInfo);
                      }
                    }
                  }
                }
              }
            }

            final SourceFileInfo srcInfo = loadSourceInfo(file);
            if (srcInfo != null) {
              final TIntHashSet projects = srcInfo.getProjectIds();
              if (!projects.isEmpty()) {
                final ScheduleOutputsForDeletionProc deletionProc = new ScheduleOutputsForDeletionProc(file.getUrl());
                deletionProc.setRootBeingDeleted(eventFile);
                final int sourceFileId = Math.abs(getFileId(file));
                for (int projectId : projects.toArray()) {
                  // mark associated outputs for deletion
                  srcInfo.processOutputPaths(projectId, deletionProc);
                  if (LOG.isDebugEnabled() || ourDebugMode) {
                    final String message = "Unschedule recompilation because of deletion " + file.getPresentableUrl();
                    LOG.debug(message);
                    if (ourDebugMode) {
                      System.out.println(message);
                    }
                  }
                  removeSourceForRecompilation(projectId, sourceFileId);
                }
              }
            }
          }
          finally {
            // it is important that update of myOutputsToDelete is done at the end
            // otherwise the filePath of the file that is about to be deleted may be re-scheduled for deletion in addSourceForRecompilation()
            unmarkOutputPathForDeletion(filePath);
          }
        }
      });

      if (!pathsToMark.isEmpty()) {
        CompileServerManager.getInstance().notifyFilesDeleted(pathsToMark);
      }
    }

    public void beforeFileMovement(final VirtualFileMoveEvent event) {
      markDirtyIfSource(event.getFile(), true);
    }

    private void markDirtyIfSource(final VirtualFile file, boolean fromMove) {
      final Set<String> pathsToMark = new HashSet<String>();
      processRecursively(file, false, new FileProcessor() {
        public void execute(final VirtualFile file) {
          pathsToMark.add(file.getPath());
          final SourceFileInfo srcInfo = file.isValid()? loadSourceInfo(file) : null;
          if (srcInfo != null) {
            for (int projectId : srcInfo.getProjectIds().toArray()) {
              addSourceForRecompilation(projectId, file, srcInfo);
            }
          }
          else {
            processNewFile(file, false);
          }
        }
      });
      if (fromMove) {
        CompileServerManager.getInstance().notifyFilesDeleted(pathsToMark);
      }
      else {
        CompileServerManager.getInstance().notifyFilesChanged(pathsToMark);
      }
    }

    private void processNewFile(final VirtualFile file, final boolean notifyServer) {
      final Set<String> pathsToMark = notifyServer ? new HashSet<String>() : Collections.<String>emptySet();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        // need read action to ensure that the project was not disposed during the iteration over the project list
        public void run() {
          for (final Project project : myProjectManager.getOpenProjects()) {
            if (!project.isInitialized()) {
              continue; // the content of this project will be scanned during its post-startup activities
            }
            final ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
            final int projectId = getProjectId(project);
            if (rootManager.getFileIndex().isInSourceContent(file)) {
              final TranslatingCompiler[] translators = CompilerManager.getInstance(project).getCompilers(TranslatingCompiler.class);
              processRecursively(file, false, new FileProcessor() {
                public void execute(final VirtualFile file) {
                  if (notifyServer) {
                    pathsToMark.add(file.getPath());
                  }
                  if (isCompilable(file)) {
                    loadInfoAndAddSourceForRecompilation(projectId, file);
                  }
                }

                boolean isCompilable(VirtualFile file) {
                  for (TranslatingCompiler translator : translators) {
                    if (translator.isCompilableFile(file, DummyCompileContext.getInstance())) {
                      return true;
                    }
                  }
                  return false;
                }
              });
            }
            else {
              if (belongsToIntermediateSources(file, project)) {
                processRecursively(file, false, new FileProcessor() {
                  public void execute(final VirtualFile file) {
                    loadInfoAndAddSourceForRecompilation(projectId, file);
                  }
                });
              }
            }
          }
        }
      });
      if (notifyServer) {
        CompileServerManager.getInstance().notifyFilesChanged(pathsToMark);
      }
    }
  }

  private boolean belongsToIntermediateSources(VirtualFile file, Project project) {
    return FileUtil.isAncestor(myGeneratedDataPaths.get(project), new File(file.getPath()), true);
  }

  private void loadInfoAndAddSourceForRecompilation(final int projectId, final VirtualFile srcFile) {
    addSourceForRecompilation(projectId, srcFile, loadSourceInfo(srcFile));
  }
  private void addSourceForRecompilation(final int projectId, final VirtualFile srcFile, @Nullable final SourceFileInfo srcInfo) {
    final boolean alreadyMarked;
    synchronized (myDataLock) {
      TIntHashSet set = mySourcesToRecompile.get(projectId);
      if (set == null) {
        set = new TIntHashSet();
        mySourcesToRecompile.put(projectId, set);
      }
      alreadyMarked = !set.add(Math.abs(getFileId(srcFile)));
      if (!alreadyMarked && (LOG.isDebugEnabled() || ourDebugMode)) {
        final String message = "Scheduled recompilation " + srcFile.getPresentableUrl();
        LOG.debug(message);
        if (ourDebugMode) {
          System.out.println(message);
        }
      }
    }

    if (!alreadyMarked && srcInfo != null) {
      srcInfo.updateTimestamp(projectId, -1L);
      srcInfo.processOutputPaths(projectId, new ScheduleOutputsForDeletionProc(srcFile.getUrl()));
      saveSourceInfo(srcFile, srcInfo);
    }
  }

  private void removeSourceForRecompilation(final int projectId, final int srcId) {
    synchronized (myDataLock) {
      TIntHashSet set = mySourcesToRecompile.get(projectId);
      if (set != null) {
        set.remove(srcId);
        if (set.isEmpty()) {
          mySourcesToRecompile.remove(projectId);
        }
      }
    }
  }
  
  public boolean isMarkedForCompilation(Project project, VirtualFile file) {
    return isMarkedForRecompilation(getProjectId(project), getFileId(file));
  }
  
  private boolean isMarkedForRecompilation(int projectId, final int srcId) {
    synchronized (myDataLock) {
      final TIntHashSet set = mySourcesToRecompile.get(projectId);
      return set != null && set.contains(srcId);
    }
  }
  
  private interface Proc {
    boolean execute(final int projectId, String outputPath);
  }
  
  private class ScheduleOutputsForDeletionProc implements Proc {
    private final String mySrcUrl;
    private final LocalFileSystem myFileSystem;
    @Nullable
    private VirtualFile myRootBeingDeleted;

    private ScheduleOutputsForDeletionProc(final String srcUrl) {
      mySrcUrl = srcUrl;
      myFileSystem = LocalFileSystem.getInstance();
    }

    public void setRootBeingDeleted(@Nullable VirtualFile rootBeingDeleted) {
      myRootBeingDeleted = rootBeingDeleted;
    }

    public boolean execute(final int projectId, String outputPath) {
      final VirtualFile outFile = myFileSystem.findFileByPath(outputPath);
      if (outFile != null) { // not deleted yet
        if (myRootBeingDeleted != null && VfsUtil.isAncestor(myRootBeingDeleted, outFile, false)) {
          unmarkOutputPathForDeletion(outputPath);
        }
        else {
          final OutputFileInfo outputInfo = loadOutputInfo(outFile);
          final String classname = outputInfo != null? outputInfo.getClassName() : null;
          markOutputPathForDeletion(projectId, outputPath, classname, mySrcUrl);
        }
      }
      return true;
    }
  }

  private void markOutputPathForDeletion(final int projectId, final String outputPath, final String classname, final String srcUrl) {
    final SourceUrlClassNamePair pair = new SourceUrlClassNamePair(srcUrl, classname);
    synchronized (myDataLock) {
      Map<String, SourceUrlClassNamePair> map = myOutputsToDelete.get(projectId);
      if (map == null) {
        map = new HashMap<String, SourceUrlClassNamePair>();
        myOutputsToDelete.put(projectId, map);
      }
      map.put(outputPath, pair);
      if (LOG.isDebugEnabled() || ourDebugMode) {
        final String message = "ADD path to delete: " + outputPath + "; source: " + srcUrl;
        LOG.debug(message);
        if (ourDebugMode) {
          System.out.println(message);
        }
      }
    }
  }

  private void unmarkOutputPathForDeletion(String outputPath) {
    synchronized (myDataLock) {
      for (int projectId : myOutputsToDelete.keys()) {
        final Map<String, SourceUrlClassNamePair> map = myOutputsToDelete.get(projectId);
        if (map != null) {
          final SourceUrlClassNamePair val = map.remove(outputPath);
          if (val != null) {
            if (LOG.isDebugEnabled() || ourDebugMode) {
              final String message = "REMOVE path to delete: " + outputPath;
              LOG.debug(message);
              if (ourDebugMode) {
                System.out.println(message);
              }
            }
            if (map.isEmpty()) {
              myOutputsToDelete.remove(projectId);
            }
          }
        }
      }
    }
  }

  public static final class ProjectRef extends Ref<Project> {
    static class ProjectClosedException extends RuntimeException {
    }

    public ProjectRef(Project project) {
      super(project);
    }

    public Project get() {
      final Project project = super.get();
      if (project != null && project.isDisposed()) {
        throw new ProjectClosedException();
      }
      return project;
    }
  }
  
}
