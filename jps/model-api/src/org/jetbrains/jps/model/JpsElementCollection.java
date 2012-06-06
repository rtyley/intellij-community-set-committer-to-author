package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public interface JpsElementCollection<E extends JpsElement> extends JpsParentElement {
  List<E> getElements();

  @NotNull
  <P> E addChild(@NotNull JpsElementFactoryWithParameter<E,P> factory, @NotNull P param);

  @NotNull
  E addChild(@NotNull JpsElementFactory<E> factory);

  E addChild(E element);

  void removeChild(@NotNull E element);
}
