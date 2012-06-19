package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.impl.JpsLibraryCollectionImpl;
import org.jetbrains.jps.model.library.impl.JpsLibraryKind;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleType;
import org.jetbrains.jps.model.module.impl.JpsModuleImpl;
import org.jetbrains.jps.model.module.impl.JpsModuleKind;

import java.util.List;

/**
 * @author nik
 */
public class JpsProjectImpl extends JpsRootElementBase<JpsProjectImpl> implements JpsProject {
  private static final JpsElementCollectionKind<JpsElementReference<?>> EXTERNAL_REFERENCES_COLLECTION_KIND =
    new JpsElementCollectionKind<JpsElementReference<?>>(new JpsElementKindBase<JpsElementReference<?>>("external reference"));
  private final JpsLibraryCollection myLibraryCollection;

  public JpsProjectImpl(JpsModel model, JpsEventDispatcher eventDispatcher) {
    super(model, eventDispatcher);
    myContainer.setChild(JpsModuleKind.MODULE_COLLECTION_KIND);
    myContainer.setChild(EXTERNAL_REFERENCES_COLLECTION_KIND);
    myLibraryCollection = new JpsLibraryCollectionImpl(myContainer.setChild(JpsLibraryKind.LIBRARIES_COLLECTION_KIND));
  }

  public JpsProjectImpl(JpsProjectImpl original, JpsModel model, JpsEventDispatcher eventDispatcher) {
    super(original, model, eventDispatcher);
    myLibraryCollection = new JpsLibraryCollectionImpl(myContainer.getChild(JpsLibraryKind.LIBRARIES_COLLECTION_KIND));
  }

  public void addExternalReference(@NotNull JpsElementReference<?> reference) {
    myContainer.getChild(EXTERNAL_REFERENCES_COLLECTION_KIND).addChild(reference);
  }

  @NotNull
  @Override
  public JpsModule addModule(@NotNull JpsModuleType<?> moduleType, @NotNull final String name) {
    final JpsElementCollectionImpl<JpsModule> collection = myContainer.getChild(JpsModuleKind.MODULE_COLLECTION_KIND);
    return collection.addChild(new JpsModuleImpl(moduleType, name));
  }

  @NotNull
  @Override
  public JpsLibrary addLibrary(@NotNull JpsLibraryType<?> libraryType, @NotNull final String name) {
    return myLibraryCollection.addLibrary(libraryType, name);
  }

  @NotNull
  @Override
  public List<JpsModule> getModules() {
    return myContainer.getChild(JpsModuleKind.MODULE_COLLECTION_KIND).getElements();
  }

  @Override
  public void addModule(@NotNull JpsModule module) {
    myContainer.getChild(JpsModuleKind.MODULE_COLLECTION_KIND).addChild(module);
  }

  @NotNull
  @Override
  public JpsLibraryCollection getLibraryCollection() {
    return myLibraryCollection;
  }

  @NotNull
  @Override
  public JpsElementReference<JpsProject> createReference() {
    return new JpsProjectElementReference();
  }
}
