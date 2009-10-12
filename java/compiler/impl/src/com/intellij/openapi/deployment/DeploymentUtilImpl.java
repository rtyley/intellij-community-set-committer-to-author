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
package com.intellij.openapi.deployment;

import com.intellij.compiler.impl.packagingCompiler.BuildRecipeImpl;
import com.intellij.compiler.impl.packagingCompiler.JarAndCopyBuildInstructionImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.make.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.PathUtil;
import com.intellij.util.descriptors.ConfigFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * @author Alexey Kudravtsev
 */
public class DeploymentUtilImpl extends DeploymentUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.deployment.MakeUtilImpl");
  @NonNls private static final String JAR_SUFFIX = ".jar";

  public boolean addModuleOutputContents(@NotNull CompileContext context,
                                         @NotNull BuildRecipe items,
                                         @NotNull final Module sourceModule,
                                         Module targetModule,
                                         final String outputRelativePath,
                                         @Nullable String possibleBaseOuputPath,
                                         @Nullable PackagingFileFilter fileFilter) {
    final File outputPath = getModuleOutputPath(sourceModule);

    String[] sourceRoots = getSourceRootUrlsInReadAction(sourceModule);
    boolean ok = true;
    if (outputPath != null && sourceRoots.length != 0) {
      ok = outputPath.exists();
      boolean added = addItemsRecursively(items, outputPath, targetModule, outputRelativePath, fileFilter, possibleBaseOuputPath);
      if (!added) {
        LOG.assertTrue(possibleBaseOuputPath != null);
        String additionalMessage = CompilerBundle.message("message.text.change.module.output.directory.or.module.exploded.directory",
                                                          ModuleUtil.getModuleNameInReadAction(sourceModule),
                                                          ModuleUtil.getModuleNameInReadAction(targetModule));
        reportRecursiveCopying(context, outputPath.getPath(), appendToPath(possibleBaseOuputPath, outputRelativePath),
                               CompilerBundle.message("module.output.directory", ModuleUtil.getModuleNameInReadAction(sourceModule)),
                               additionalMessage);
        ok = false;
      }
    }
    return ok;
  }

  private static String[] getSourceRootUrlsInReadAction(final Module module) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String[]>() {
      public String[] compute() {
        return ModuleRootManager.getInstance(module).getSourceRootUrls();
      }
    });
  }

  private static File getModuleOutputPath(final Module module) {
    return ApplicationManager.getApplication().runReadAction(new Computable<File>() {
      @Nullable
      public File compute() {
        final String url = CompilerModuleExtension.getInstance(module).getCompilerOutputUrl();
        if (url == null) return null;
        return new File(PathUtil.toPresentableUrl(url));
      }
    });
  }

  public void addLibraryLink(@NotNull final CompileContext context,
                             @NotNull final BuildRecipe items,
                             @NotNull final LibraryLink libraryLink,
                             @NotNull final Module module,
                             @Nullable final String possibleBaseOutputPath) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        String outputRelativePath;
        final PackagingMethod packagingMethod = libraryLink.getPackagingMethod();
        if (packagingMethod.equals(PackagingMethod.COPY_FILES_AND_LINK_VIA_MANIFEST)
            || packagingMethod.equals(PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST)) {
          outputRelativePath = getRelativePathForManifestLinking(libraryLink.getURI());
        }
        else {
          outputRelativePath = libraryLink.getURI();
        }

        final List<String> urls = libraryLink.getClassesRootUrls();

        boolean isDestinationDirectory = true;
        if (LibraryLink.MODULE_LEVEL.equals(libraryLink.getLevel()) && urls.size() == 1 && outputRelativePath.endsWith(JAR_SUFFIX)) {
          isDestinationDirectory = false;
        }

        for (String url : urls) {
          final String path = PathUtil.toPresentableUrl(url);
          final File file = new File(path);
          boolean packagingMethodIsCopy = packagingMethod.equals(PackagingMethod.COPY_FILES_AND_LINK_VIA_MANIFEST) ||
                                          packagingMethod.equals(PackagingMethod.COPY_FILES);

          String fileDestination = outputRelativePath;
          if (isDestinationDirectory) {
            if (file.isDirectory()) {
              if (!packagingMethodIsCopy) {
                fileDestination = appendToPath(fileDestination, file.getName() + JAR_SUFFIX);
              }
            }
            else {
              fileDestination = appendToPath(fileDestination, file.getName());
            }
          }

          if (file.isDirectory()) {
            boolean ok;
            if (packagingMethodIsCopy) {
              ok = addItemsRecursively(items, file, module, fileDestination, null, possibleBaseOutputPath);
            }
            else {
              fixPackagingMethod(packagingMethod, libraryLink, context);
              BuildInstruction instruction = new JarAndCopyBuildInstructionImpl(module, file, fileDestination);
              items.addInstruction(instruction);
              ok = true;
            }
            if (!ok) {
              final String name = libraryLink.getPresentableName();
              String additionalMessage = CompilerBundle.message("message.text.adjust.library.path");
              reportRecursiveCopying(context, file.getPath(), fileDestination,
                                     CompilerBundle.message("directory.description.library.directory", name), additionalMessage);
            }
          }
          else {
            items.addFileCopyInstruction(file, false, module, fileDestination, null);
          }
        }
      }
    });
  }

  private static void fixPackagingMethod(final PackagingMethod packagingMethod, final LibraryLink libraryLink, final CompileContext context) {
    if (!packagingMethod.equals(PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST) &&
        !packagingMethod.equals(PackagingMethod.JAR_AND_COPY_FILE)) {
      libraryLink.setPackagingMethod(PackagingMethod.JAR_AND_COPY_FILE);
      String message = CompilerBundle.message("message.text.packaging.method.for.library.reset", libraryLink.getPresentableName(),
                                              PackagingMethod.JAR_AND_COPY_FILE);
      context.addMessage(CompilerMessageCategory.WARNING, message, null, -1, -1);
    }
  }

  public void copyFile(@NotNull final File fromFile,
                       @NotNull final File toFile,
                       @NotNull CompileContext context,
                       @Nullable Set<String> writtenPaths,
                       @Nullable FileFilter fileFilter) throws IOException {
    if (fileFilter != null && !fileFilter.accept(fromFile)) {
      return;
    }
    checkPathDoNotNavigatesUpFromFile(fromFile);
    checkPathDoNotNavigatesUpFromFile(toFile);
    if (fromFile.isDirectory()) {
      final File[] fromFiles = fromFile.listFiles();
      toFile.mkdirs();
      for (File file : fromFiles) {
        copyFile(file, new File(toFile, file.getName()), context, writtenPaths, fileFilter);
      }
      return;
    }
    if (toFile.isDirectory()) {
      context.addMessage(CompilerMessageCategory.ERROR,
                         CompilerBundle.message("message.text.destination.is.directory", createCopyErrorMessage(fromFile, toFile)), null, -1, -1);
      return;
    }
    if (fromFile.equals(toFile)
        || writtenPaths != null && !writtenPaths.add(toFile.getPath())) {
      return;
    }
    if (!FileUtil.isFilePathAcceptable(toFile, fileFilter)) return;
    if (context.getProgressIndicator() != null) {
      context.getProgressIndicator().setText("Copying files");
      context.getProgressIndicator().setText2(fromFile.getPath());
    }
    try {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Copy file '" + fromFile + "' to '"+toFile+"'");
      }
      if (toFile.exists() && !SystemInfo.isFileSystemCaseSensitive) {
        File canonicalFile = toFile.getCanonicalFile();
        if (!canonicalFile.getAbsolutePath().equals(toFile.getAbsolutePath())) {
          FileUtil.delete(toFile);
        }
      }
      FileUtil.copy(fromFile, toFile);
    }
    catch (IOException e) {
      context.addMessage(CompilerMessageCategory.ERROR, createCopyErrorMessage(fromFile, toFile) + ": "+e.getLocalizedMessage(), null, -1, -1);
    }
  }

  // OS X is sensitive for that
  private static void checkPathDoNotNavigatesUpFromFile(File file) {
    String path = file.getPath();
    int i = path.indexOf("..");
    if (i != -1) {
      String filepath = path.substring(0,i-1);
      File filepart = new File(filepath);
      if (filepart.exists() && !filepart.isDirectory()) {
        LOG.error("Incorrect file path: '" + path + '\'');
      }
    }
  }

  private static String createCopyErrorMessage(final File fromFile, final File toFile) {
    return CompilerBundle.message("message.text.error.copying.file.to.file", FileUtil.toSystemDependentName(fromFile.getPath()),
                              FileUtil.toSystemDependentName(toFile.getPath()));
  }

  public final boolean addItemsRecursively(@NotNull BuildRecipe items,
                                           @NotNull File root,
                                           Module module,
                                           String outputRelativePath,
                                           @Nullable PackagingFileFilter fileFilter,
                                           @Nullable String possibleBaseOutputPath) {
    if (outputRelativePath == null) outputRelativePath = "";
    outputRelativePath = trimForwardSlashes(outputRelativePath);

    if (possibleBaseOutputPath != null) {
      File file = new File(possibleBaseOutputPath, outputRelativePath);
      String relativePath = getRelativePath(root, file);
      if (relativePath != null && !relativePath.startsWith("..") && relativePath.length() != 0) {
        return false;
      }
    }

    items.addFileCopyInstruction(root, true, module, outputRelativePath, fileFilter);
    return true;
  }

  public void reportDeploymentDescriptorDoesNotExists(ConfigFile descriptor, CompileContext context, Module module) {
    final String description = module.getModuleType().getName() + " '" + module.getName() + '\'';
    String descriptorPath = VfsUtil.urlToPath(descriptor.getUrl());
    final String message =
      CompilerBundle.message("message.text.compiling.item.deployment.descriptor.could.not.be.found", description, descriptorPath);
    context.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
  }

  public static String getRelativePathForManifestLinking(String relativePath) {
    if (!StringUtil.startsWithChar(relativePath, '/')) relativePath = '/' + relativePath;
    relativePath = ".." + relativePath;
    return relativePath;
  }

  public @Nullable File findUserSuppliedManifestFile(@NotNull BuildRecipe buildRecipe) {
    final Ref<File> ref = Ref.create(null);
    buildRecipe.visitInstructions(new BuildInstructionVisitor() {
      public boolean visitInstruction(BuildInstruction instruction) throws Exception {
        final File file = instruction.findFileByRelativePath(JarFile.MANIFEST_NAME);
        ref.set(file);
        return file == null;
      }
    }, false);
    return ref.get();
  }

  public void checkConfigFile(final ConfigFile descriptor, final CompileContext compileContext, final Module module) {
    if (new File(VfsUtil.urlToPath(descriptor.getUrl())).exists()) {
      String message = getConfigFileErrorMessage(descriptor);
      if (message != null) {
        final String moduleDescription = module.getModuleType().getName() + " '" + module.getName() + '\'';
        compileContext.addMessage(CompilerMessageCategory.ERROR,
                                CompilerBundle.message("message.text.compiling.module.message", moduleDescription, message),
                                  descriptor.getUrl(), -1, -1);
      }
    }
    else {
      DeploymentUtil.getInstance().reportDeploymentDescriptorDoesNotExists(descriptor, compileContext, module);
    }
  }

  public @Nullable Manifest createManifest(@NotNull BuildRecipe buildRecipe) {
    if (findUserSuppliedManifestFile(buildRecipe) != null) {
      return null;
    }

    final List<String> classpathElements = getExternalDependenciesClasspath(buildRecipe);
    final Manifest manifest = new Manifest();
    setManifestAttributes(manifest.getMainAttributes(), classpathElements);
    return manifest;
  }

  public static void setManifestAttributes(final Attributes mainAttributes, final @Nullable List<String> classpathElements) {
    if (classpathElements != null && classpathElements.size() > 0) {
      StringBuilder builder;
      Set<String> existingPaths = new HashSet<String>();
      String oldClassPath = mainAttributes.getValue(Attributes.Name.CLASS_PATH);
      if (oldClassPath != null) {
        StringTokenizer tokenizer = new StringTokenizer(oldClassPath);
        while (tokenizer.hasMoreTokens()) {
          existingPaths.add(tokenizer.nextToken());
        }
        builder = new StringBuilder(oldClassPath);
      }
      else {
        builder = new StringBuilder();
      }

      for (String path : classpathElements) {
        if (!existingPaths.contains(path)) {
          if (builder.length() > 0) {
            builder.append(' ');
          }
          builder.append(path);
        }
      }
      mainAttributes.put(Attributes.Name.CLASS_PATH, builder.toString());
    }
    ManifestBuilder.setGlobalAttributes(mainAttributes);
  }

  public static List<String> getExternalDependenciesClasspath(final BuildRecipe buildRecipe) {
    final List<String> classpath = new ArrayList<String>();

    buildRecipe.visitInstructions(new BuildInstructionVisitor() {
      public boolean visitInstruction(BuildInstruction instruction) throws RuntimeException {
        final String outputRelativePath = instruction.getOutputRelativePath();
        if (instruction.isExternalDependencyInstruction()) {
          final String jarReference = PathUtil.getCanonicalPath("/tmp/" + outputRelativePath).substring(1);
          classpath.add(trimForwardSlashes(jarReference));
        }
        return true;
      }
    }, false);
    return classpath;
  }

  public void addJavaModuleOutputs(@NotNull final Module module,
                                   @NotNull ModuleLink[] containingModules,
                                   @NotNull BuildRecipe instructions,
                                   @NotNull CompileContext context,
                                   String explodedPath) {
    addJavaModuleOutputs(module, containingModules, instructions, context, explodedPath, "");
  }

  public void addJavaModuleOutputs(@NotNull final Module module,
                                   @NotNull ModuleLink[] containingModules,
                                   @NotNull BuildRecipe instructions,
                                   @NotNull CompileContext context,
                                   String explodedPath, final String linkContainerDescription) {
    addJavaModuleOutputs(module, containingModules, instructions, context, explodedPath, linkContainerDescription, null);
  }

  public void addJavaModuleOutputs(@NotNull final Module module,
                                   @NotNull ModuleLink[] containingModules,
                                   @NotNull BuildRecipe instructions,
                                   @NotNull CompileContext context,
                                   @Nullable String possibleExplodedPath, final String linkContainerDescription, @Nullable Map<Module, PackagingFileFilter> fileFilters) {
    for (ModuleLink moduleLink : containingModules) {
      Module childModule = moduleLink.getModule();
      if (childModule != null) {
        final PackagingMethod packagingMethod = moduleLink.getPackagingMethod();
        if (PackagingMethod.DO_NOT_PACKAGE.equals(packagingMethod)) {
          continue;
        }

        PackagingFileFilter fileFilter = fileFilters != null ? fileFilters.get(childModule) : null;

        if (PackagingMethod.JAR_AND_COPY_FILE.equals(packagingMethod)) {
          addJarJavaModuleOutput(instructions, childModule, moduleLink.getURI(), context, linkContainerDescription, fileFilter);
        }
        else if (PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST.equals(packagingMethod)) {
          String relativePath = getRelativePathForManifestLinking(moduleLink.getURI());
          addJarJavaModuleOutput(instructions, childModule, relativePath, context, linkContainerDescription, fileFilter);
        }
        else if (PackagingMethod.COPY_FILES.equals(packagingMethod)) {
          addModuleOutputContents(context, instructions, childModule, module, moduleLink.getURI(), possibleExplodedPath, fileFilter);
        }
        else if (PackagingMethod.COPY_FILES_AND_LINK_VIA_MANIFEST.equals(packagingMethod)) {
          moduleLink.setPackagingMethod(PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST);
          String relativePath = getRelativePathForManifestLinking(moduleLink.getURI());
          addJarJavaModuleOutput(instructions, childModule, relativePath, context, linkContainerDescription, fileFilter);
          context.addMessage(CompilerMessageCategory.WARNING,
                             CompilerBundle.message("message.text.packaging.method.for.module.reset.to.method",
                                                ModuleUtil.getModuleNameInReadAction(childModule),
                                                PackagingMethod.JAR_AND_COPY_FILE_AND_LINK_VIA_MANIFEST),
                             null, -1, -1);
        }
        else {
          LOG.info("invalid packaging method " + packagingMethod + " for module '" + ModuleUtil.getModuleNameInReadAction(childModule)
                   + "' in module '" + ModuleUtil.getModuleNameInReadAction(module) + "'");
        }
      }
    }
  }

  private static void addJarJavaModuleOutput(BuildRecipe instructions,
                                             Module module,
                                             String relativePath,
                                             CompileContext context, final String linkContainerDescription, @Nullable PackagingFileFilter fileFilter) {
    final String[] sourceUrls = getSourceRootUrlsInReadAction(module);
    if (sourceUrls.length > 0) {
      final File outputPath = getModuleOutputPath(module);
      if (outputPath != null) {
        if ("/".equals(relativePath) || "".equals(relativePath) || getRelativePathForManifestLinking("/").equals(relativePath)) {
          context.addMessage(CompilerMessageCategory.ERROR, CompilerBundle.message("message.text.invalid.output.path.for.module.jar", relativePath, module.getName(), linkContainerDescription), null, -1, -1);
        }
        else {
          instructions.addInstruction(new JarAndCopyBuildInstructionImpl(module, outputPath, relativePath, fileFilter));
        }
      }
    }
  }

  public ModuleLink createModuleLink(@NotNull Module dep, @NotNull Module module) {
    return new ModuleLinkImpl(dep, module);
  }

  public LibraryLink createLibraryLink(Library library, @NotNull Module parentModule) {
    return new LibraryLinkImpl(library, parentModule);
  }

  public BuildRecipe createBuildRecipe() {
    return new BuildRecipeImpl();
  }

  @Nullable
  public String getConfigFileErrorMessage(final ConfigFile configFile) {
    if (configFile.getVirtualFile() == null) {
      String path = FileUtil.toSystemDependentName(VfsUtil.urlToPath(configFile.getUrl()));
      return CompilerBundle.message("mesage.text.deployment.descriptor.file.not.exist", path);
    }
    PsiFile psiFile = configFile.getPsiFile();
    if (psiFile == null || !psiFile.isValid()) {
      return CompilerBundle.message("message.text.deployment.description.invalid.file");
    }

    if (psiFile instanceof XmlFile) {
      XmlDocument document = ((XmlFile)psiFile).getDocument();
      if (document == null || document.getRootTag() == null) {
        return CompilerBundle.message("message.text.xml.file.invalid", FileUtil.toSystemDependentName(VfsUtil.urlToPath(configFile.getUrl())));
      }
    }
    return null;
  }

  @Nullable
  public static String getOrCreateExplodedDir(final BuildParticipant buildParticipant) {
    BuildConfiguration buildConfiguration = buildParticipant.getBuildConfiguration();
    if (buildConfiguration.isExplodedEnabled()) {
      return buildConfiguration.getExplodedPath();
    }
    return buildParticipant.getOrCreateTemporaryDirForExploded();
  }

  public static BuildParticipantProvider<?>[] getBuildParticipantProviders() {
    return Extensions.getExtensions(BuildParticipantProvider.EXTENSION_POINT_NAME);
  }
}
