package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

import java.util.List;

/**
 * @author nik
 */
public abstract class JpsNamedElementReferenceBase<T extends JpsNamedElement, Self extends JpsNamedElementReferenceBase<T, Self>> extends JpsCompositeElementBase<Self> implements JpsElementReference<T> {
  private static final JpsElementKind<JpsElementReference<? extends JpsCompositeElement>> PARENT_REFERENCE_KIND = new JpsElementKind<JpsElementReference<? extends JpsCompositeElement>>();
  private final JpsElementCollectionKind myCollectionKind;
  protected final String myElementName;

  protected JpsNamedElementReferenceBase(@NotNull JpsModel model, @NotNull JpsEventDispatcher eventDispatcher, @NotNull JpsElementCollectionKind kind, @NotNull String elementName,
                                         @NotNull JpsElementReference<? extends JpsCompositeElement> parentReference, JpsParentElement parent) {
    super(model, eventDispatcher, parent);
    myCollectionKind = kind;
    myElementName = elementName;
    myContainer.setChild(PARENT_REFERENCE_KIND, parentReference);
  }

  protected JpsNamedElementReferenceBase(JpsNamedElementReferenceBase<T, Self> original,
                                         JpsModel model, JpsEventDispatcher eventDispatcher,
                                         JpsParentElement parent) {
    super(original, model, eventDispatcher, parent);
    myCollectionKind = original.myCollectionKind;
    myElementName = original.myElementName;
  }

  @Override
  public T resolve() {
    final JpsCompositeElement parent = myContainer.getChild(PARENT_REFERENCE_KIND).resolve();
    if (parent == null) return null;

    JpsElementCollectionImpl child = (JpsElementCollectionImpl)parent.getContainer().getChild(myCollectionKind);
    List elements = child.getElements();
    for (Object element : elements) {
      if (((T)element).getName().equals(myElementName)) {
        return (T)element;
      }
    }
    return null;
  }
}
