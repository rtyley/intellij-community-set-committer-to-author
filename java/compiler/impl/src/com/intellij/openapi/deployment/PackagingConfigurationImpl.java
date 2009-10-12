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

import com.intellij.facet.impl.DefaultFacetsProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import gnu.trove.TObjectHashingStrategy;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Alexey Kudravtsev
 */
public class PackagingConfigurationImpl implements PackagingConfiguration {
  private static final Logger LOG = Logger.getInstance("#com.intellij.javaee.module.J2EEModuleContainerImpl");
  protected ModulesProvider myDefaultModulesProvider;
  protected final Module myParentModule;
  protected final Set<ContainerElement> myContents = new LinkedHashSet<ContainerElement>();

  @NonNls public static final String TYPE_ATTRIBUTE_NAME = "type";
  @NonNls public static final String CONTAINER_ELEMENT_NAME = "containerElement";
  @NonNls public static final String MODULE_TYPE = "module";
  @NonNls public static final String LIBRARY_TYPE = "library";
  public static final TObjectHashingStrategy<ContainerElement> IGNORING_ATTRIBUTES_EQUALITY = new ElementIgnoringAttributesEquality();

  public PackagingConfigurationImpl(@NotNull Module module) {
    myParentModule = module;
    myDefaultModulesProvider = new DefaultModulesProvider(module.getProject());
  }

  public PackagingConfigurationImpl createNewInstance() {
    return new PackagingConfigurationImpl(myParentModule);
  }

  public void removeLibrary(final Library library) {
    for (ContainerElement content : myContents) {
      if (content instanceof LibraryLink && library.equals(((LibraryLink)content).getLibrary())) {
        removeContainerElement(content);
        break;
      }
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    clearContainer();
    LOG.assertTrue(myParentModule != null);

    final List<Element> children = element.getChildren(CONTAINER_ELEMENT_NAME);
    for (Element child : children) {
      final String type = child.getAttributeValue(TYPE_ATTRIBUTE_NAME);
      ContainerElement containerElement;
      containerElement = createElement(child, myParentModule, type);

      containerElement.readExternal(child);

      addElement(containerElement);
    }
  }

  protected ContainerElement createElement(final @NotNull Element child, final @NotNull Module module, final String type) throws InvalidDataException {
    if (MODULE_TYPE.equals(type)) {
      return new ModuleLinkImpl((String)null, module);
    }
    else if (LIBRARY_TYPE.equals(type)) {
      return new LibraryLinkImpl(null, module);
    }
    else {
      throw new InvalidDataException("invalid type: " + type + " " + child);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (final ContainerElement containerElement : myContents) {
      final Element child = new Element(CONTAINER_ELEMENT_NAME);
      final String type = getElementType(containerElement);
      child.setAttribute(TYPE_ATTRIBUTE_NAME, type);
      containerElement.writeExternal(child);
      element.addContent(child);
    }
  }

  protected String getElementType(final ContainerElement containerElement) throws WriteExternalException {
    if (containerElement instanceof ModuleLink) {
      return MODULE_TYPE;
    }
    else if (containerElement instanceof LibraryLink) {
      return LIBRARY_TYPE;
    }
    else {
      throw new WriteExternalException("invalid type: " + containerElement);
    }
  }

  public ModuleLink[] getContainingModules() {

    final List<ModuleLink> moduleLinks = new ArrayList<ModuleLink>();

    ContainerElement[] elements = getElements();
    for (ContainerElement element : elements) {
      if (element instanceof ModuleLink) {
        moduleLinks.add((ModuleLink)element);
      }
    }

    return moduleLinks.toArray(new ModuleLink[moduleLinks.size()]);
  }

  public LibraryLink[] getContainingLibraries() {
    final List<LibraryLink> libraryLinks = new ArrayList<LibraryLink>();
    ContainerElement[] elements = getElements();
    for (ContainerElement element : elements) {
      if (element instanceof LibraryLink) {
        libraryLinks.add((LibraryLink)element);
      }
    }
    return libraryLinks.toArray(new LibraryLink[libraryLinks.size()]);
  }

  public ContainerElement[] getElements() {
    return getElements(myDefaultModulesProvider, DefaultFacetsProvider.INSTANCE, true, false, false);
  }

  public ContainerElement[] getElements(ModulesProvider provider, final FacetsProvider facetsProvider,
                                        final boolean includeResolved, final boolean includeUnresolved, final boolean includeNonPackaged) {
    ArrayList<ContainerElement> result = new ArrayList<ContainerElement>();
    for (final ContainerElement containerElement : myContents) {
      final boolean resolved = containerElement.resolveElement(provider, facetsProvider);
      if ((resolved && includeResolved || !resolved && includeUnresolved)
        && (includeNonPackaged || containerElement.getPackagingMethod() != PackagingMethod.DO_NOT_PACKAGE)) {
        result.add(containerElement);
      }
    }
    return result.toArray(new ContainerElement[result.size()]);
  }

  public void setElements(ContainerElement[] elements) {
    clearContainer();
    myContents.addAll(Arrays.asList(elements));
  }

  protected void removeModuleLinkInternal(Module module) {
    for (final ContainerElement containerElement : myContents) {
      if (containerElement instanceof ModuleLink && ((ModuleLink)containerElement).getModule() == module) {
        removeContainerElement(containerElement);
        break;
      }
    }
  }

  public boolean removeContainerElement(final ContainerElement containerElement) {
    return myContents.remove(containerElement);
  }

  public Module[] getContainingIdeaModules() {
    ModuleLink[] containingModules = getContainingModules();
    List<Module> result = new ArrayList<Module>(containingModules.length);
    for (ModuleLink containingModule : containingModules) {
      final Module module = containingModule.getModule();
      if (module != null) {
        result.add(module);
      }
    }
    return result.toArray(new Module[result.size()]);
  }

  protected void clearContainer() {
    myContents.clear();
  }

  public void addOrReplaceElement(final ContainerElement element) {
    for (Iterator<ContainerElement> it = myContents.iterator(); it.hasNext();) {
      ContainerElement content = it.next();
      if (content.equalsIgnoreAttributes(element)) {
        it.remove();
      }
    }
    addElement(element);
  }

  public void addElement(ContainerElement element) {
    myContents.add(element);
  }

}
