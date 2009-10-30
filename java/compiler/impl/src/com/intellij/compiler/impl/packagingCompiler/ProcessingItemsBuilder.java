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

package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.compiler.make.*;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.deployment.DeploymentUtilImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.text.FilePathHashingStrategy;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.jar.JarFile;

/**
 * @author nik
*/
public class ProcessingItemsBuilder extends BuildInstructionVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.packagingCompiler.ProcessingItemsBuilder");
  private final Stack<String> myOutputPaths;
  private Stack<NestedJarInfo> myNestedJars;
  private final BuildConfiguration myBuildConfiguration;
  private final OldProcessingItemsBuilderContext myContext;
  private final BuildParticipant myBuildParticipant;
  private final LocalFileSystem myLocalFileSystem;

  public ProcessingItemsBuilder(final BuildParticipant buildParticipant, OldProcessingItemsBuilderContext context) {
    myContext = context;
    myBuildParticipant = buildParticipant;
    myBuildConfiguration = myBuildParticipant.getBuildConfiguration();
    myOutputPaths = new Stack<String>();
    myOutputPaths.push("");
    myLocalFileSystem = LocalFileSystem.getInstance();

    final String jarPath = myBuildConfiguration.getJarPath();
    if (myBuildConfiguration.isJarEnabled() && jarPath != null) {
      myNestedJars = new Stack<NestedJarInfo>();
      final DestinationInfo destinationInfo = createExplodedDestination(jarPath);
      myNestedJars.push(myContext.createNestedJarInfo(destinationInfo, myBuildConfiguration, myBuildParticipant.getBuildInstructions(
        myContext.getCompileContext())));
    }
  }

  private ExplodedDestinationInfo createExplodedDestination(final String path) {
    VirtualFile file = myLocalFileSystem.findFileByPath(path);
    return createExplodedDestination(path, file);
  }

  private ExplodedDestinationInfo createExplodedDestination(final String path, final VirtualFile file) {
    ExplodedDestinationInfo destinationInfo = new ExplodedDestinationInfo(path, file);
    myContext.registerDestination(myBuildParticipant, destinationInfo);
    return destinationInfo;
  }

  public void build() {
    buildItems(myBuildParticipant.getBuildInstructions(myContext.getCompileContext()));
  }

  private void buildItems(final BuildRecipe instructions) {
    instructions.visitInstructions(this, false);

    if (myBuildParticipant.willBuildExploded()) {
      List<String> classpath = DeploymentUtilImpl.getExternalDependenciesClasspath(instructions);
      if (!classpath.isEmpty()) {
        String outputRoot = DeploymentUtilImpl.getOrCreateExplodedDir(myBuildParticipant);
        String fullOutputPath = getCanonicalConcat(outputRoot, myOutputPaths.peek(), JarFile.MANIFEST_NAME);
        myContext.addManifestFile(new ManifestFileInfo(fullOutputPath, classpath));
      }
    }
  }

  private static String getCanonicalConcat(final String... paths) {
    final String fullOutputPath = DeploymentUtil.concatPaths(paths);
    String path = PathUtil.getCanonicalPath(fullOutputPath);
    if (path == null) {
      LOG.error("invalid path: " + Arrays.toString(paths));
    }
    return path;
  }

  public boolean visitFileCopyInstruction(final FileCopyInstruction instruction) throws Exception {
    final String output = myOutputPaths.peek();
    final VirtualFile sourceFile = myLocalFileSystem.findFileByIoFile(instruction.getFile());
    if (sourceFile == null) return true;

    PackagingFileFilter fileFilter = instruction.getFileFilter();

    String outputRelativePath = instruction.getOutputRelativePath();
    if (myBuildParticipant.willBuildExploded()) {
      String outputRoot = DeploymentUtilImpl.getOrCreateExplodedDir(myBuildParticipant);
      String fullOutputPath = getCanonicalConcat(outputRoot, output, outputRelativePath);

      checkRecursiveCopying(sourceFile, fullOutputPath);
      addItemsToExplodedRecursively(sourceFile, fullOutputPath, fileFilter);
    }

    if (myNestedJars != null) {
      NestedJarInfo nestedJar = getNestedJar(myNestedJars, instruction.isExternalDependencyInstruction());
      if (nestedJar != null) {
        checkRecursiveCopying(sourceFile, nestedJar.myDestination.getOutputFilePath());
        addItemsToJarRecursively(sourceFile, DeploymentUtil.trimForwardSlashes(trimParentPrefix(outputRelativePath)),
                                 nestedJar.myDestination, nestedJar.myJarInfo, nestedJar.myAddJarContent, fileFilter);
      }
      else {
        String fullOutputPath = getCanonicalConcat(myBuildConfiguration.getJarPath(), outputRelativePath);
        checkRecursiveCopying(sourceFile, fullOutputPath);
        addItemsToExplodedRecursively(sourceFile, fullOutputPath, fileFilter);
      }
    }

    return true;
  }

  private void checkRecursiveCopying(@NotNull final VirtualFile sourceFile, @NotNull final String outputPath) {
    File fromFile = VfsUtil.virtualToIoFile(sourceFile);
    File toFile = new File(FileUtil.toSystemDependentName(outputPath));
    try {
      if (FileUtil.isAncestor(fromFile, toFile, true)) {
        DeploymentUtil.reportRecursiveCopying(myContext.getCompileContext(), fromFile.getAbsolutePath(), toFile.getAbsolutePath(), "", "");
      }
    }
    catch (IOException ignored) {
    }
  }

  private static String trimParentPrefix(String outputRelativePath) {
    if (outputRelativePath.startsWith("..")) {
      return outputRelativePath.substring(2);
    }
    return outputRelativePath;
  }

  @Nullable
  private static NestedJarInfo getNestedJar(@NotNull final Stack<NestedJarInfo> nestedJarInfos,
                                                           final boolean externalDependencyInstruction) {
    if (!externalDependencyInstruction) {
      return nestedJarInfos.peek();
    }
    if (nestedJarInfos.size() > 1) {
      return nestedJarInfos.get(nestedJarInfos.size() - 2);
    }
    return null;
  }

  private void addItemsToExplodedRecursively(final VirtualFile sourceFile, final String fullOutputPath, @Nullable PackagingFileFilter fileFilter) {
    VirtualFile outputFile = myLocalFileSystem.findFileByPath(fullOutputPath);
    addItemsToExplodedRecursively(sourceFile, fullOutputPath, outputFile, fileFilter);
  }

  public boolean visitJarAndCopyBuildInstruction(final JarAndCopyBuildInstruction instruction) throws Exception {
    final VirtualFile sourceFile = myLocalFileSystem.findFileByIoFile(instruction.getFile());
    if (sourceFile == null) return true;

    PackagingFileFilter fileFilter = instruction.getFileFilter();

    if (myBuildParticipant.willBuildExploded()) {
      String outputRoot = DeploymentUtilImpl.getOrCreateExplodedDir(myBuildParticipant);
      String jarPath = getCanonicalConcat(outputRoot, myOutputPaths.peek(), instruction.getOutputRelativePath());

      checkRecursiveCopying(sourceFile, jarPath);
      addItemsToJar(sourceFile, createExplodedDestination(jarPath), fileFilter);
    }

    if (myNestedJars != null) {
      final NestedJarInfo nestedJar = getNestedJar(myNestedJars, instruction.isExternalDependencyInstruction());
      if (nestedJar != null) {
        final String outputRelativePath = trimParentPrefix(instruction.getOutputRelativePath());
        JarDestinationInfo destination = new JarDestinationInfo(outputRelativePath, nestedJar.myJarInfo, nestedJar.myDestination);
        checkRecursiveCopying(sourceFile, destination.getOutputFilePath());
        addItemsToJar(sourceFile, destination, fileFilter);
      }
      else {
        String jarPath = getCanonicalConcat(myBuildConfiguration.getJarPath(), instruction.getOutputRelativePath());
        checkRecursiveCopying(sourceFile, jarPath);
        addItemsToJar(sourceFile, createExplodedDestination(jarPath), fileFilter);
      }
    }

    return true;
  }

  private void addItemsToJar(final VirtualFile sourceFile, final DestinationInfo destination, @Nullable final PackagingFileFilter fileFilter) {
    JarInfo jarInfo = myContext.getCachedJar(sourceFile);
    boolean addToJarInfo = jarInfo == null;
    if (jarInfo == null) {
      jarInfo = new JarInfo();
      myContext.putCachedJar(sourceFile, jarInfo);
    }

    jarInfo.addDestination(destination);
    if (destination instanceof ExplodedDestinationInfo) {
      myContext.registerJarFile(jarInfo, destination.getOutputFilePath());
    }

    addItemsToJarRecursively(sourceFile, "", destination, jarInfo, addToJarInfo, fileFilter);
  }

  private void addItemsToJarRecursively(final VirtualFile sourceFile, String pathInJar, DestinationInfo jarDestination,
                                        final JarInfo jarInfo, boolean addToJarContent, @Nullable PackagingFileFilter fileFilter) {
    if (fileFilter != null && !fileFilter.accept(sourceFile, myContext.getCompileContext())) {
      return;
    }

    if (sourceFile.isDirectory()) {
      final VirtualFile[] children = sourceFile.getChildren();
      for (VirtualFile child : children) {
        String childPath = DeploymentUtil.trimForwardSlashes(DeploymentUtil.appendToPath(pathInJar, child.getName()));
        addItemsToJarRecursively(child, childPath, jarDestination, jarInfo, addToJarContent, fileFilter);
      }
    }
    else {
      String fullOutputPath = DeploymentUtil.appendToPath(jarDestination.getOutputPath(), pathInJar);
      if (myContext.checkOutputPath(fullOutputPath, sourceFile)) {
        final PackagingProcessingItem item = myContext.getOrCreateProcessingItem(sourceFile, myBuildParticipant);
        item.addDestination(new JarDestinationInfo(pathInJar, jarInfo, jarDestination));
        if (addToJarContent) {
          jarInfo.addContent(pathInJar, sourceFile);
        }
      }
    }
  }

  private void addItemsToExplodedRecursively(final VirtualFile sourceFile, final String outputPath, @Nullable final VirtualFile outputFile,
                                             @Nullable PackagingFileFilter fileFilter) {
    if (fileFilter != null && !fileFilter.accept(sourceFile, myContext.getCompileContext())) {
      return;
    }

    if (sourceFile.isDirectory()) {
      final VirtualFile[] children = sourceFile.getChildren();
      THashMap<String, VirtualFile> outputChildren = null;
      if (outputFile != null) {
        outputChildren = new THashMap<String, VirtualFile>(FilePathHashingStrategy.create());
        VirtualFile[] files = outputFile.getChildren();
        if (files != null) {
          for (VirtualFile file : files) {
            outputChildren.put(file.getName(), file);
          }
        }
      }
      for (VirtualFile child : children) {
        final VirtualFile outputChild = outputChildren != null ? outputChildren.get(child.getName()) : null;
        addItemsToExplodedRecursively(child, DeploymentUtil.appendToPath(outputPath, child.getName()), outputChild, fileFilter);
      }
    }
    else if (myContext.checkOutputPath(outputPath, sourceFile)) {
      PackagingProcessingItem item = myContext.getOrCreateProcessingItem(sourceFile, myBuildParticipant);
      item.addDestination(createExplodedDestination(outputPath, outputFile));
    }
  }

  public static class NestedJarInfo {
    private final JarInfo myJarInfo;
    private final DestinationInfo myDestination;
    private final boolean myAddJarContent;

    public NestedJarInfo(final JarInfo jarInfo, final DestinationInfo destination, final boolean addJarContent) {
      myJarInfo = jarInfo;
      myDestination = destination;
      myAddJarContent = addJarContent;
    }
  }
}
