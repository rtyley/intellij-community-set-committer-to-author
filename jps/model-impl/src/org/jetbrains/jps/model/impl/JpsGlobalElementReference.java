package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

/**
 * @author nik
 */
public class JpsGlobalElementReference extends JpsElementReferenceBase<JpsGlobalElementReference, JpsGlobal> {
  @Override
  public JpsGlobal resolve() {
    final JpsModel model = getModel();
    return model != null ? model.getGlobal() : null;
  }

  @NotNull
  @Override
  public JpsGlobalElementReference createCopy() {
    return new JpsGlobalElementReference();
  }

  @Override
  public void applyChanges(@NotNull JpsGlobalElementReference modified) {
  }
}
