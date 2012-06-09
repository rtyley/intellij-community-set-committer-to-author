package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.impl.JpsElementCollectionKind;
import org.jetbrains.jps.model.module.JpsModuleSourceRootListener;

/**
 * @author nik
 */
public class JpsModuleSourceRootKind extends JpsElementKindBase<JpsModuleSourceRootImpl> {
  public static final JpsModuleSourceRootKind INSTANCE = new JpsModuleSourceRootKind();
  public static final JpsElementCollectionKind<JpsModuleSourceRootImpl> ROOT_COLLECTION_KIND = new JpsElementCollectionKind<JpsModuleSourceRootImpl>(INSTANCE);

  public JpsModuleSourceRootKind() {
    super("module source root");
  }

  @Override
  public void fireElementAdded(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsModuleSourceRootImpl element) {
    dispatcher.getPublisher(JpsModuleSourceRootListener.class).sourceRootAdded(element);
  }

  @Override
  public void fireElementRemoved(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsModuleSourceRootImpl element) {
    dispatcher.getPublisher(JpsModuleSourceRootListener.class).sourceRootRemoved(element);
  }
}
