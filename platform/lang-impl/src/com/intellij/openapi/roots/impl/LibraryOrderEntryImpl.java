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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *  @author dsl
 */
class LibraryOrderEntryImpl extends LibraryOrderEntryBaseImpl implements LibraryOrderEntry, ClonableOrderEntry, WritableOrderEntry {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.LibraryOrderEntryImpl");
  private Library myLibrary;
  private String myLibraryName; // is non-null if myLibrary == null
  private String myLibraryLevel; // is non-null if myLibraryLevel == null
  private boolean myExported;
  @NonNls static final String ENTRY_TYPE = "library";
  @NonNls private static final String NAME_ATTR = "name";
  @NonNls private static final String LEVEL_ATTR = "level";
  private final MyOrderEntryLibraryTableListener myLibraryListener = new MyOrderEntryLibraryTableListener();
  @NonNls private static final String EXPORTED_ATTR = "exported";

  LibraryOrderEntryImpl (Library library,
                         RootModelImpl rootModel,
                         ProjectRootManagerImpl projectRootManager,
                         VirtualFilePointerManager virtualFilePointerManager) {
    super(rootModel, projectRootManager, virtualFilePointerManager);
    LOG.assertTrue(library.getTable() != null);
    myLibrary = library;
    init(getRootProvider());
    addListeners();
  }

  LibraryOrderEntryImpl (Element element,
                         RootModelImpl rootModel,
                         ProjectRootManagerImpl projectRootManager,
                         VirtualFilePointerManager filePointerManager) throws InvalidDataException {
    super(rootModel, projectRootManager, filePointerManager);
    LOG.assertTrue(ENTRY_TYPE.equals(element.getAttributeValue(OrderEntryFactory.ORDER_ENTRY_TYPE_ATTR)));
    myExported = element.getAttributeValue(EXPORTED_ATTR) != null;
    myScope = DependencyScope.readExternal(element);
    String level = element.getAttributeValue(LEVEL_ATTR);
    String name = element.getAttributeValue(NAME_ATTR);
    if (name == null) throw new InvalidDataException();
    if (level == null) throw new InvalidDataException();
    searchForLibrary(level, name);
    init(getRootProvider());
    addListeners();
  }

  private LibraryOrderEntryImpl(LibraryOrderEntryImpl that,
                                RootModelImpl rootModel,
                                ProjectRootManagerImpl projectRootManager,
                                VirtualFilePointerManager filePointerManager) {
    super (rootModel, projectRootManager, filePointerManager);
    if (that.myLibrary == null) {
      myLibraryName = that.myLibraryName;
      myLibraryLevel = that.myLibraryLevel;
    }
    else {
      myLibrary = that.myLibrary;
    }
    myExported = that.myExported;
    myScope = that.myScope;
    init(getRootProvider());
    addListeners();
  }

  public LibraryOrderEntryImpl(@NotNull String name,
                               @NotNull String level,
                               RootModelImpl rootModel,
                               ProjectRootManagerImpl projectRootManager,
                               VirtualFilePointerManager filePointerManager) {
    super(rootModel, projectRootManager, filePointerManager);
    searchForLibrary(level, name);
    init(getRootProvider());
    addListeners();
  }

  private void searchForLibrary(@NotNull String level, @NotNull String name) {
    if (myLibrary != null) return;
    final LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(level, getRootModel().getModule().getProject());
    final Library library = libraryTable != null ? libraryTable.getLibraryByName(name) : null;
    if (library == null) {
      myLibraryName = name;
      myLibraryLevel = level;
      myLibrary = null;
    }
    else {
      myLibraryName = null;
      myLibraryLevel = null;
      myLibrary = library;
    }
  }

  public boolean isExported() {
    return myExported;
  }

  public void setExported(boolean exported) {
    myExported = exported;
  }

  @NotNull
  public DependencyScope getScope() {
    return myScope;
  }

  public void setScope(@NotNull DependencyScope scope) {
    myScope = scope;
  }

  @Nullable
  public Library getLibrary() {
    Library library = getRootModel().getConfigurationAccessor().getLibrary(myLibrary, myLibraryName, myLibraryLevel);
    if (library != null) { //library was not deleted
      return library;
    }
    if (myLibrary != null) {
      myLibraryName = myLibrary.getName();
      myLibraryLevel = myLibrary.getTable().getTableLevel();
    }
    myLibrary = null;
    return null;
  }

  public boolean isModuleLevel() {
    return false;
  }

  public String getPresentableName() {
    return getLibraryName();
  }

  @Nullable
  private RootProvider getRootProvider() {
    if (myLibrary != null) {
      return myLibrary.getRootProvider();
    } else {
      return null;
    }
  }

  public boolean isValid() {
    return !isDisposed() && getLibrary() != null;
  }

  public <R> R accept(RootPolicy<R> policy, R initialValue) {
    return policy.visitLibraryOrderEntry(this, initialValue);
  }

  public OrderEntry cloneEntry(RootModelImpl rootModel,
                               ProjectRootManagerImpl projectRootManager,
                               VirtualFilePointerManager filePointerManager) {
    ProjectRootManagerImpl rootManager = ProjectRootManagerImpl.getInstanceImpl(getRootModel().getModule().getProject());
    return new LibraryOrderEntryImpl(this, rootModel, rootManager, VirtualFilePointerManager.getInstance());
  }

  public void writeExternal(Element rootElement) throws WriteExternalException {
    final Element element = OrderEntryFactory.createOrderEntryElement(ENTRY_TYPE);
    final String libraryLevel = getLibraryLevel();
    if (myExported) {
      element.setAttribute(EXPORTED_ATTR, "");
    }
    myScope.writeExternal(element);
    element.setAttribute(NAME_ATTR, getLibraryName());
    element.setAttribute(LEVEL_ATTR, libraryLevel);
    rootElement.addContent(element);
  }

  public String getLibraryLevel() {
    if (myLibrary != null) {
      final LibraryTable table = myLibrary.getTable();
      return table.getTableLevel();
    } else {
      return myLibraryLevel;
    }
  }

  public String getLibraryName() {
    if (myLibrary != null) {
      return myLibrary.getName();
    }
    else {
      return myLibraryName;
    }
  }

  private void addListeners () {
    final String libraryLevel = getLibraryLevel();
    final LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(libraryLevel, getRootModel().getModule().getProject());
    if (libraryTable != null) {
      myProjectRootManagerImpl.addListenerForTable(myLibraryListener, libraryTable);
    }
  }


  public boolean isSynthetic() {
    return false;
  }

  public void dispose() {
    super.dispose();
    final LibraryTable libraryTable =
      LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(getLibraryLevel(), getRootModel().getModule().getProject());
    if (libraryTable != null) {
      myProjectRootManagerImpl.removeListenerForTable(myLibraryListener, libraryTable);
    }
  }


  private void afterLibraryAdded(Library newLibrary) {
    if (myLibrary == null) {
      if (Comparing.equal(myLibraryName, newLibrary.getName())) {
        myLibrary = newLibrary;
        myLibraryName = null;
        myLibraryLevel = null;
        updateFromRootProviderAndSubscribe(getRootProvider());
      }
    }
  }

  private void beforeLibraryRemoved(Library library) {
    if (library == myLibrary) {
      myLibraryName = myLibrary.getName();
      myLibraryLevel = myLibrary.getTable().getTableLevel();
      myLibrary = null;
      updateFromRootProviderAndSubscribe(getRootProvider());
    }
  }

  private class MyOrderEntryLibraryTableListener implements LibraryTable.Listener {
    public MyOrderEntryLibraryTableListener() {
    }

    public void afterLibraryAdded(Library newLibrary) {
      LibraryOrderEntryImpl.this.afterLibraryAdded(newLibrary);
    }

    public void afterLibraryRenamed(Library library) {
      afterLibraryAdded(library);
    }

    public void beforeLibraryRemoved(Library library) {
      LibraryOrderEntryImpl.this.beforeLibraryRemoved(library);
    }

    public void afterLibraryRemoved(Library library) {
    }
  }

}
