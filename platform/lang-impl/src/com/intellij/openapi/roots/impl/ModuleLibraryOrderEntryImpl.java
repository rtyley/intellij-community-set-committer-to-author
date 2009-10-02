package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Library entry for module ("in-place") libraries
 *  @author dsl
 */
public class ModuleLibraryOrderEntryImpl extends LibraryOrderEntryBaseImpl implements LibraryOrderEntry, ClonableOrderEntry, WritableOrderEntry {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.LibraryOrderEntryImpl");
  private final Library myLibrary;
  @NonNls public static final String ENTRY_TYPE = "module-library";
  private boolean myExported;
 @NonNls private static final String EXPORTED_ATTR = "exported";

  //cloning
  private ModuleLibraryOrderEntryImpl(Library library, RootModelImpl rootModel, boolean isExported, DependencyScope scope) {
    super(rootModel, ProjectRootManagerImpl.getInstanceImpl(rootModel.getProject()), VirtualFilePointerManager.getInstance());
    myLibrary = ((LibraryEx)library).cloneLibrary(getRootModel());
    init();
    myExported = isExported;
    myScope = scope;
  }

  ModuleLibraryOrderEntryImpl(RootModelImpl rootModel,
                              ProjectRootManagerImpl projectRootManager,
                              VirtualFilePointerManager filePointerManager) {
    this((String)null, rootModel, projectRootManager, filePointerManager);
  }

  ModuleLibraryOrderEntryImpl(String name,
                              RootModelImpl rootModel,
                              ProjectRootManagerImpl projectRootManager,
                              VirtualFilePointerManager filePointerManager) {
    super(rootModel, projectRootManager, filePointerManager);
    myLibrary = LibraryTableImplUtil.createModuleLevelLibrary(name, getRootModel());
    init();
  }

  private void init() {
    Disposer.register(this, myLibrary);
    init(myLibrary.getRootProvider());
  }

  ModuleLibraryOrderEntryImpl (Element element,
                               RootModelImpl rootModel,
                               ProjectRootManagerImpl projectRootManager,
                               VirtualFilePointerManager filePointerManager) throws InvalidDataException {
    super(rootModel, projectRootManager, filePointerManager);
    LOG.assertTrue(ENTRY_TYPE.equals(element.getAttributeValue(OrderEntryFactory.ORDER_ENTRY_TYPE_ATTR)));
    myExported = element.getAttributeValue(EXPORTED_ATTR) != null;
    myScope = DependencyScope.readExternal(element);
    myLibrary = LibraryTableImplUtil.loadLibrary(element, getRootModel());
    init();
  }

  public Library getLibrary() {
    return myLibrary;
  }

  public boolean isModuleLevel() {
    return true;
  }

  protected void addListenerToWrapper(RootProvider wrapper,
                                      RootProvider.RootSetChangedListener rootSetChangedListener) {
    wrapper.addRootSetChangedListener(rootSetChangedListener);
  }

  protected void removeListenerFromWrapper(RootProvider wrapper,
                                           RootProvider.RootSetChangedListener rootSetChangedListener) {
    wrapper.removeRootSetChangedListener(rootSetChangedListener);
  }

  public String getLibraryName() {
    return myLibrary.getName();
  }

  public String getLibraryLevel() {
    return LibraryTableImplUtil.MODULE_LEVEL;
  }

  public String getPresentableName() {
    final String name = myLibrary.getName();
    if (name != null) {
      return name;
    }
    else {
      if (myLibrary instanceof LibraryEx && ((LibraryEx)myLibrary).isDisposed()) {
        return "<unknown>";
      }

      final String[] urls = myLibrary.getUrls(OrderRootType.CLASSES);
      if (urls.length > 0) {
        String url = urls[0];
        return PathUtil.toPresentableUrl(url);
      }
      else {
        return null;
      }
    }
  }

  public boolean isValid() {
    return !isDisposed() && myLibrary != null;
  }

  public <R> R accept(RootPolicy<R> policy, R initialValue) {
    return policy.visitLibraryOrderEntry(this, initialValue);
  }

  public boolean isSynthetic() {
    return true;
  }

  public OrderEntry cloneEntry(RootModelImpl rootModel,
                               ProjectRootManagerImpl projectRootManager,
                               VirtualFilePointerManager filePointerManager) {
    return new ModuleLibraryOrderEntryImpl(myLibrary, rootModel, myExported, myScope);
  }

  public void writeExternal(Element rootElement) throws WriteExternalException {
    final Element element = OrderEntryFactory.createOrderEntryElement(ENTRY_TYPE);
    if (myExported) {
      element.setAttribute(EXPORTED_ATTR, "");
    }
    myScope.writeExternal(element);
    myLibrary.writeExternal(element);
    rootElement.addContent(element);
  }


  public boolean isExported() {
    return myExported;
  }

  public void setExported(boolean value) {
    myExported = value;
  }

  @NotNull
  public DependencyScope getScope() {
    return myScope;
  }

  public void setScope(@NotNull DependencyScope scope) {
    myScope = scope;
  }

  @Override
  public void dispose() {
    super.dispose();
  }
}
