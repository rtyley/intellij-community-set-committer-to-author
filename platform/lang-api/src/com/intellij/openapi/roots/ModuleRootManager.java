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
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for getting information about the contents and dependencies of a module.
 *
 * @author dsl
 */
public abstract class ModuleRootManager implements ModuleRootModel {
  /**
   * Returns the module root manager instance for the specified module.
   *
   * @param module the module for which the root manager is requested.
   * @return the root manager instance.
   */
  public static ModuleRootManager getInstance(Module module) {
    return module.getComponent(ModuleRootManager.class);
  }

  /**
   * Returns the list of roots of the specified type for the current module and all modules it depends on.
   *
   * @param type the type of roots requested.
   * @return the list of roots.
   *
   * @deprecated
   * <ul>
   *   <li> to get {@link OrderRootType#CLASSES} use <code>OrderEnumerator.orderEntries(module).getClassesRootsWithoutModuleOutputs()<code>
   *   <li> to get {@link OrderRootType#SOURCES} use <code>OrderEnumerator.orderEntries(module).getAllSourceRoots()<code>
   */
  @NotNull
  public abstract VirtualFile[] getFiles(OrderRootType type);

  /**
   * Returns the list of URLs of roots of the specified type for the current module and all modules it depends on.
   *
   * @param type the type of roots requested.
   * @return the list of root URLs.

   * @deprecated
   * <ul>
   *   <li> to get {@link OrderRootType#CLASSES} use <code>OrderEnumerator.orderEntries(module).withoutModuleSourceEntries().recursively().exportedOnly().classes().getUrls()<code>
   *   <li> to get {@link OrderRootType#SOURCES} use <code>OrderEnumerator.orderEntries(module).recursively().exportedOnly().sources().getUrls()<code>
   */
  @NotNull
  public abstract String[] getUrls(OrderRootType type);

  /**
   * Returns the file index for the current module.
   *
   * @return the file index instance.
   */
  @NotNull
  public abstract ModuleFileIndex getFileIndex();

  /**
   * Returns the interface for modifying the set of roots for this module. Must be called in a read action.
   * !!!!! ACHTUNG !!!!!: This model MUST be either committed {@link ModifiableRootModel#commit()} or disposed {@link ModifiableRootModel#dispose()}  
   *
   * @return the modifiable root model.
   */
  @NotNull
  public abstract ModifiableRootModel getModifiableModel();

  /**
   * Returns the list of modules on which the current module directly depends. The method does not traverse
   * the entire dependency structure - dependencies of dependency modules are not included in the returned list.
   *
   * @return the list of module direct dependencies.
   */
  @NotNull public abstract Module[] getDependencies();

  /**
   * Returns the list of modules on which the current module directly depends. The method does not traverse
   * the entire dependency structure - dependencies of dependency modules are not included in the returned list.
   *
   * @param includeTests whether test-only dependencies should be included
   * @return the list of module direct dependencies.
   */
  @NotNull public abstract Module[] getDependencies(boolean includeTests);

  /**
   * Checks if the current module directly depends on the specified module.
   *
   * @param module the module to ckeck.
   * @return true if <code>module</code> is contained in the list of dependencies for the current module, false otherwise.
   */
  public abstract boolean isDependsOn(Module module);

}
