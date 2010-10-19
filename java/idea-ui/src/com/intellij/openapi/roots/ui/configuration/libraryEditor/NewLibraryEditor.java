/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.ui.LightFilePointer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class NewLibraryEditor implements LibraryEditor {
  private String myLibraryName;
  private final MultiMap<OrderRootType, LightFilePointer> myRoots;
  private final Map<String, Boolean> myJarDirectories = new HashMap<String, Boolean>();
  private final LibraryType myType;
  private LibraryProperties myProperties;

  public NewLibraryEditor() {
    this(null, null);
  }

  public NewLibraryEditor(LibraryType type, LibraryProperties properties) {
    myType = type;
    myProperties = properties;
    myRoots = new MultiMap<OrderRootType, LightFilePointer>();
  }

  @Override
  @Nullable
  public LibraryType getType() {
    return myType;
  }

  @Override
  public LibraryProperties getProperties() {
    return myProperties;
  }

  @Override
  public String getName() {
    return myLibraryName;
  }

  @Override
  public String[] getUrls(OrderRootType rootType) {
    final Collection<LightFilePointer> pointers = myRoots.get(rootType);
    List<String> urls = new ArrayList<String>();
    for (LightFilePointer pointer : pointers) {
      urls.add(pointer.getUrl());
    }
    return ArrayUtil.toStringArray(urls);
  }

  @Override
  public VirtualFile[] getFiles(OrderRootType rootType) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (LightFilePointer pointer : myRoots.get(rootType)) {
      final VirtualFile file = pointer.getFile();
      if (file.isDirectory()) {
        final Boolean recursively = myJarDirectories.get(file.getUrl());
        if (recursively != null) {
          LibraryImpl.collectJarFiles(file, result, recursively);
          continue;
        }
      }
      result.add(file);
    }
    return VfsUtil.toVirtualFileArray(result);
  }

  @Override
  public void setName(String name) {
    myLibraryName = name;
  }

  @Override
  public void addRoot(VirtualFile file, OrderRootType rootType) {
    myRoots.putValue(rootType, new LightFilePointer(file));
  }

  @Override
  public void addRoot(String url, OrderRootType rootType) {
    myRoots.putValue(rootType, new LightFilePointer(url));
  }

  @Override
  public void addJarDirectory(VirtualFile file, boolean recursive) {
    addJarDirectory(file.getUrl(), recursive);
  }

  @Override
  public void addJarDirectory(final String url, boolean recursive) {
    addRoot(url, OrderRootType.CLASSES);
    myJarDirectories.put(url, recursive);
  }

  @Override
  public void removeRoot(String url, OrderRootType rootType) {
    myRoots.removeValue(rootType, new LightFilePointer(url));
    myJarDirectories.remove(url);
  }

  @Override
  public boolean hasChanges() {
    return true;
  }

  @Override
  public boolean isJarDirectory(String url) {
    return myJarDirectories.containsKey(url);
  }

  @Override
  public boolean isValid(String url, OrderRootType orderRootType) {
    final Collection<LightFilePointer> pointers = myRoots.get(orderRootType);
    for (LightFilePointer pointer : pointers) {
      if (pointer.getUrl().equals(url)) {
        return pointer.isValid();
      }
    }
    return false;
  }

  public void apply(@NotNull Library.ModifiableModel model) {
    model.setName(myLibraryName);
    applyRoots(model);
  }

  public void applyRoots(Library.ModifiableModel model) {
    for (OrderRootType type : myRoots.keySet()) {
      for (LightFilePointer pointer : myRoots.get(type)) {
        model.addRoot(pointer.getUrl(), type);
      }
    }
    for (Map.Entry<String, Boolean> entry : myJarDirectories.entrySet()) {
      model.addJarDirectory(entry.getKey(), entry.getValue());
    }
  }

  public void copyRoots(ExistingLibraryEditor editor) {
    for (OrderRootType type : myRoots.keySet()) {
      for (LightFilePointer pointer : myRoots.get(type)) {
        editor.addRoot(pointer.getUrl(), type);
      }
    }
    for (Map.Entry<String, Boolean> entry : myJarDirectories.entrySet()) {
      editor.addJarDirectory(entry.getKey(), entry.getValue());
    }
  }
}
