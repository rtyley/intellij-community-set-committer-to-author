package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsLibraryType;

import java.util.List;

/**
 * @author nik
 */
public interface JpsModule extends JpsNamedElement, JpsReferenceableElement<JpsModule> {
  @NotNull
  JpsUrlList getContentRootsList();

  @NotNull
  JpsUrlList getExcludeRootsList();


  @NotNull
  List<JpsModuleSourceRoot> getSourceRoots();

  @NotNull
  <P extends JpsElementProperties, Type extends JpsModuleSourceRootType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsModuleSourceRoot addSourceRoot(@NotNull String url, @NotNull Type rootType);

  @NotNull
  <P extends JpsElementProperties>
  JpsModuleSourceRoot addSourceRoot(@NotNull String url, @NotNull JpsModuleSourceRootType<P> rootType, @NotNull P properties);

  void removeSourceRoot(@NotNull String url, @NotNull JpsModuleSourceRootType rootType);

  JpsDependenciesList getDependenciesList();

  @NotNull
  JpsElementContainer getContainer();

  @NotNull
  JpsModuleReference createReference();

  @NotNull
  <P extends JpsElementProperties, Type extends JpsLibraryType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsLibrary addModuleLibrary(@NotNull String name, @NotNull Type type);

  void addModuleLibrary(@NotNull JpsLibrary library);

  @NotNull
  JpsLibraryCollection getLibraryCollection();

  @NotNull
  JpsSdkReferencesTable getSdkReferencesTable();

  void delete();
}
