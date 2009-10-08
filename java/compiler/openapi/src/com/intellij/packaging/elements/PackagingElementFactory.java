package com.intellij.packaging.elements;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public abstract class PackagingElementFactory {

  public static PackagingElementFactory getInstance() {
    return ServiceManager.getService(PackagingElementFactory.class);
  }

  @NotNull
  public abstract ArtifactRootElement<?> createArtifactRootElement();

  @NotNull
  public abstract CompositePackagingElement<?> createDirectory(@NotNull @NonNls String directoryName);

  @NotNull
  public abstract CompositePackagingElement<?> createArchive(@NotNull @NonNls String archiveFileName);

  @NotNull
  public abstract PackagingElement<?> createModuleOutput(@NotNull String moduleName, Project project);

  @NotNull
  public abstract PackagingElement<?> createModuleOutput(@NotNull Module module);

  @NotNull
  public abstract List<? extends PackagingElement<?>> createLibraryElements(@NotNull Library library);

  @NotNull
  public abstract PackagingElement<?> createArtifactElement(@NotNull Artifact artifact, @NotNull Project project);

  @NotNull
  public abstract PackagingElement<?> createLibraryFiles(@NotNull String libraryName, @NotNull String level, String moduleName);


  @NotNull
  public abstract PackagingElement<?> createDirectoryCopyWithParentDirectories(@NotNull String filePath, @NotNull String relativeOutputPath);

  @NotNull
  public abstract PackagingElement<?> createFileCopyWithParentDirectories(@NotNull String filePath, @NotNull String relativeOutputPath,
                                                                          @Nullable String outputFileName);
  
  @NotNull
  public abstract PackagingElement<?> createFileCopyWithParentDirectories(@NotNull String filePath, @NotNull String relativeOutputPath);
  

  @NotNull
  public abstract CompositePackagingElement<?> getOrCreateDirectory(@NotNull CompositePackagingElement<?> parent, @NotNull String relativePath);

  @NotNull
  public abstract CompositePackagingElement<?> getOrCreateArchive(@NotNull CompositePackagingElement<?> parent, @NotNull String relativePath);

  public abstract void addFileCopy(@NotNull CompositePackagingElement<?> root, @NotNull String outputDirectoryPath, @NotNull String sourceFilePath);

  @NotNull
  public abstract PackagingElement<?> createParentDirectories(@NotNull String relativeOutputPath, @NotNull PackagingElement<?> element);

  @NotNull
  public abstract List<? extends PackagingElement<?>> createParentDirectories(@NotNull String relativeOutputPath, @NotNull List<? extends PackagingElement<?>> elements);


  @NotNull
  public abstract CompositePackagingElementType<?>[] getCompositeElementTypes();

  @Nullable
  public abstract PackagingElementType<?> findElementType(String id);

  @NotNull
  public abstract PackagingElementType<?>[] getNonCompositeElementTypes();

  @NotNull
  public abstract PackagingElementType[] getAllElementTypes();

  @NotNull
  public abstract ComplexPackagingElementType<?>[] getComplexElementTypes();
}
