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
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
public interface Library extends JDOMExternalizable, Disposable {
  String getName();

  @NotNull String[] getUrls(@NotNull OrderRootType rootType);

  @NotNull VirtualFile[] getFiles(@NotNull OrderRootType rootType);

  /**
   * As soon as you obtaining modifiable model you will have to commit it or call Disposer.dispose(model)! 
   */
  @NotNull ModifiableModel getModifiableModel();

  LibraryTable getTable();

  @NotNull RootProvider getRootProvider();

  boolean isJarDirectory(@NotNull String url);
  
  boolean isValid(@NotNull String url, @NotNull OrderRootType rootType);
  
  interface ModifiableModel extends Disposable {
    @NotNull String[] getUrls(@NotNull OrderRootType rootType);

    void setName(@NotNull String name);

    String getName();

    void addRoot(@NotNull String url, @NotNull OrderRootType rootType);
    
    void addJarDirectory(@NotNull String url, boolean recursive);

    void addRoot(@NotNull VirtualFile file, @NotNull OrderRootType rootType);
    
    void addJarDirectory(@NotNull VirtualFile file, boolean recursive);

    void moveRootUp(@NotNull String url, @NotNull OrderRootType rootType);

    void moveRootDown(@NotNull String url, @NotNull OrderRootType rootType);

    boolean removeRoot(@NotNull String url, @NotNull OrderRootType rootType);

    void commit();

    @NotNull VirtualFile[] getFiles(@NotNull OrderRootType rootType);

    boolean isChanged();
    
    boolean isJarDirectory(@NotNull String url);
    
    boolean isValid(@NotNull String url, @NotNull OrderRootType rootType);
  }
}
