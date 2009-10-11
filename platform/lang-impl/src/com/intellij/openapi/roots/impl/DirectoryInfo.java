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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.OrderedSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DirectoryInfo {
  public Module module; // module to which content it belongs or null
  public boolean isInModuleSource; // true if files in this directory belongs to sources of the module (if field 'module' is not null)
  public boolean isTestSource; // (makes sense only if isInModuleSource is true)
  public boolean isInLibrarySource; // true if it's a directory with sources of some library
  public String packageName; // package name; makes sense only when at least one of isInModuleSource, isInLibrary or isInLibrarySource is true
  public VirtualFile libraryClassRoot; // class root in library
  public VirtualFile contentRoot;
  public VirtualFile sourceRoot;
  public final VirtualFile directory;

  public DirectoryInfo(final VirtualFile directory) {
    this.directory = directory;
  }

  /**
   *  orderEntry to (classes of) which a directory belongs
   */
  private List<OrderEntry> orderEntries = null;
  private volatile MultiMap<Module, OrderEntry> moduleOrderEntries = null;

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DirectoryInfo)) return false;

    final DirectoryInfo info = (DirectoryInfo)o;

    if (!Comparing.equal(directory, info.directory)) return false;
    if (isInLibrarySource != info.isInLibrarySource) return false;
    if (isInModuleSource != info.isInModuleSource) return false;
    if (isTestSource != info.isTestSource) return false;
    if (module != null ? !module.equals(info.module) : info.module != null) return false;
    if (packageName != null ? !packageName.equals(info.packageName) : info.packageName != null) return false;
    if (orderEntries != null ? !orderEntries.equals(info.orderEntries) : info.orderEntries != null) return false;
    if (!Comparing.equal(libraryClassRoot, info.libraryClassRoot)) return false;
    if (!Comparing.equal(contentRoot, info.contentRoot)) return false;
    if (!Comparing.equal(sourceRoot, info.sourceRoot)) return false;

    return true;
  }

  public int hashCode() {
    return (packageName != null ? packageName.hashCode() : 0);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "DirectoryInfo{" +
           "module=" + module +
           ", isInModuleSource=" + isInModuleSource +
           ", isTestSource=" + isTestSource +
           ", isInLibrarySource=" + isInLibrarySource +
           ", packageName=" + packageName +
           ", libraryClassRoot=" + libraryClassRoot +
           ", contentRoot=" + contentRoot +
           ", sourceRoot=" + sourceRoot +
           "}";
  }

  public List<OrderEntry> getOrderEntries() {
    return orderEntries == null ? Collections.<OrderEntry>emptyList() : orderEntries;
  }

  public Collection<OrderEntry> getOrderEntries(@NotNull Module module) {
    if (orderEntries == null) {
      return Collections.emptyList();
    }

    MultiMap<Module, OrderEntry> tmp = moduleOrderEntries;
    if (tmp == null) {
      tmp = new MultiMap<Module, OrderEntry>();
      for (final OrderEntry orderEntry : orderEntries) {
        tmp.putValue(orderEntry.getOwnerModule(), orderEntry);
      }
      moduleOrderEntries = tmp;
    }
    return tmp.get(module);
  }

  @SuppressWarnings({"unchecked"})
  public void addOrderEntries(final List<OrderEntry> orderEntries,
                              final DirectoryInfo parentInfo,
                              final List<OrderEntry> oldParentEntries) {
    moduleOrderEntries = null;
    if (this.orderEntries == null) {
      this.orderEntries = orderEntries;
    }
    else if (parentInfo != null && oldParentEntries == this.orderEntries) {
      this.orderEntries = parentInfo.getOrderEntries();
    }
    else {
      List<OrderEntry> tmp = new OrderedSet<OrderEntry>(TObjectHashingStrategy.CANONICAL);
      tmp.addAll(this.orderEntries);
      tmp.addAll(orderEntries);
      this.orderEntries = tmp;
    }
  }
}
