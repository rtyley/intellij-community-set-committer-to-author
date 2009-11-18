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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;

/**
 *  @author dsl
 */
abstract class LibraryOrderEntryBaseImpl extends OrderEntryBaseImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.LibraryOrderEntryBaseImpl");
  private final Map<OrderRootType, VirtualFilePointerContainer> myRootContainers;
  private final MyRootSetChangedListener myRootSetChangedListener = new MyRootSetChangedListener();
  private RootProvider myCurrentlySubscribedRootProvider = null;
  protected final ProjectRootManagerImpl myProjectRootManagerImpl;
  @NotNull protected DependencyScope myScope = DependencyScope.COMPILE;

  LibraryOrderEntryBaseImpl(RootModelImpl rootModel, ProjectRootManagerImpl instanceImpl, VirtualFilePointerManager filePointerManager) {
    super(rootModel);
    myRootContainers = new HashMap<OrderRootType, VirtualFilePointerContainer>();
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      myRootContainers.put(type, filePointerManager.createContainer(this, rootModel.myVirtualFilePointerListener));
    }
    myProjectRootManagerImpl = instanceImpl;
  }

  protected final void init(RootProvider rootProvider) {
    if (rootProvider == null) return;
    updatePathsFromProviderAndSubscribe(rootProvider);
  }

  private void updatePathsFromProviderAndSubscribe(final RootProvider rootProvider) {
    updatePathsFromProvider(rootProvider);
    resubscribe(rootProvider);
  }

  private void updatePathsFromProvider(final RootProvider rootProvider) {
    final OrderRootType[] allTypes = OrderRootType.getAllTypes();
    for (OrderRootType type : allTypes) {
      final VirtualFilePointerContainer container = myRootContainers.get(type);
      container.clear();
      if (rootProvider != null) {
        final String[] urls = rootProvider.getUrls(type);
        for (String url : urls) {
          container.add(url);
        }
      }
    }
  }

  private boolean needUpdateFromProvider(final RootProvider rootProvider) {
    final OrderRootType[] allTypes = OrderRootType.getAllTypes();
    for (OrderRootType type : allTypes) {
      final VirtualFilePointerContainer container = myRootContainers.get(type);
      final String[] urls = container.getUrls();
      final String[] providerUrls = rootProvider.getUrls(type);
      if (!Arrays.equals(urls, providerUrls)) return true;
    }
    return false;
  }

  @NotNull
  public VirtualFile[] getFiles(OrderRootType type) {
    if (type == OrderRootType.COMPILATION_CLASSES) {
      return myRootContainers.get(OrderRootType.CLASSES).getDirectories();
    }
    else if (type == OrderRootType.PRODUCTION_COMPILATION_CLASSES) {
      if (myScope == DependencyScope.RUNTIME || myScope == DependencyScope.TEST) {
        return VirtualFile.EMPTY_ARRAY;
      }
      return myRootContainers.get(OrderRootType.CLASSES).getDirectories();
    }
    else if (type == OrderRootType.CLASSES_AND_OUTPUT) {
      return myScope == DependencyScope.PROVIDED ? VirtualFile.EMPTY_ARRAY : myRootContainers.get(OrderRootType.CLASSES).getDirectories();
    }
    return myRootContainers.get(type).getDirectories();
  }

  @NotNull
  public String[] getUrls(OrderRootType type) {
    LOG.assertTrue(!getRootModel().getModule().isDisposed());
    if (type == OrderRootType.COMPILATION_CLASSES) {
      return myRootContainers.get(OrderRootType.CLASSES).getUrls();
    }
    else if (type == OrderRootType.PRODUCTION_COMPILATION_CLASSES) {
      if (myScope == DependencyScope.RUNTIME || myScope == DependencyScope.TEST) {
        return ArrayUtil.EMPTY_STRING_ARRAY;
      }
      return myRootContainers.get(OrderRootType.CLASSES).getUrls();
    }
    else if (type == OrderRootType.CLASSES_AND_OUTPUT) {
      return myScope == DependencyScope.PROVIDED ? ArrayUtil.EMPTY_STRING_ARRAY : myRootContainers.get(OrderRootType.CLASSES).getUrls();
    }
    return myRootContainers.get(type).getUrls();
  }

  public VirtualFile[] getRootFiles(OrderRootType type) {
    return myRootContainers.get(type).getDirectories();
  }

  public String[] getRootUrls(OrderRootType type) {
    return myRootContainers.get(type).getUrls();
  }

  @NotNull
  public final Module getOwnerModule() {
    return getRootModel().getModule();
  }

  protected void updateFromRootProviderAndSubscribe(RootProvider wrapper) {
    getRootModel().fireBeforeExternalChange();
    updatePathsFromProviderAndSubscribe(wrapper);
    getRootModel().fireAfterExternalChange();
  }

  private void updateFromRootProvider(RootProvider wrapper) {
    getRootModel().fireBeforeExternalChange();
    updatePathsFromProvider(wrapper);
    getRootModel().fireAfterExternalChange();
  }

  private void resubscribe(RootProvider wrapper) {
    unsubscribe();
    subscribe(wrapper);
  }

  private void subscribe(RootProvider wrapper) {
    if (wrapper != null) {
      addListenerToWrapper(wrapper, myRootSetChangedListener);
    }
    myCurrentlySubscribedRootProvider = wrapper;
  }

  protected void addListenerToWrapper(final RootProvider wrapper,
                                      final RootProvider.RootSetChangedListener rootSetChangedListener) {
    myProjectRootManagerImpl.addRootSetChangedListener(rootSetChangedListener, wrapper);
  }


  private void unsubscribe() {
    if (myCurrentlySubscribedRootProvider != null) {
      final RootProvider wrapper = myCurrentlySubscribedRootProvider;
      removeListenerFromWrapper(wrapper, myRootSetChangedListener);
    }
    myCurrentlySubscribedRootProvider = null;
  }

  protected void removeListenerFromWrapper(final RootProvider wrapper,
                                           final RootProvider.RootSetChangedListener rootSetChangedListener) {
    myProjectRootManagerImpl.removeRootSetChangedListener(rootSetChangedListener, wrapper);
  }


  public void dispose() {
    super.dispose();
    //for (VirtualFilePointerContainer virtualFilePointerContainer : new THashSet<VirtualFilePointerContainer>(myRootContainers.values())) {
    //  virtualFilePointerContainer.killAll();
    //}
    unsubscribe();
  }

  private class MyRootSetChangedListener implements RootProvider.RootSetChangedListener {

    public MyRootSetChangedListener() {
    }

    public void rootSetChanged(RootProvider wrapper) {
      if (needUpdateFromProvider(wrapper)) {
        updateFromRootProvider(wrapper);
      }
    }
  }
}
