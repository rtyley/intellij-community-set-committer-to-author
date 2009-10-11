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
package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.Disposable;
import com.intellij.util.SmartList;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class LibraryTablesRegistrarImpl extends LibraryTablesRegistrar implements Disposable {
  private static final Map<String, LibraryTable> myLibraryTables = new HashMap<String, LibraryTable>();

  @NotNull
  public LibraryTable getLibraryTable() {
    return ApplicationLibraryTable.getApplicationTable();
  }

  @NotNull
  public LibraryTable getLibraryTable(@NotNull Project project) {
    return ProjectLibraryTable.getInstance(project);
  }

  public LibraryTable getLibraryTableByLevel(String level, @NotNull Project project) {
    if (LibraryTablesRegistrar.PROJECT_LEVEL.equals(level)) return getLibraryTable(project);
    if (LibraryTablesRegistrar.APPLICATION_LEVEL.equals(level)) return getLibraryTable();
    return myLibraryTables.get(level);
  }

  public void registerLibraryTable(@NotNull LibraryTable libraryTable) {
    String tableLevel = libraryTable.getTableLevel();
    final LibraryTable oldTable = myLibraryTables.put(tableLevel, libraryTable);
    if (oldTable != null) {
      throw new IllegalArgumentException("Library table '" + tableLevel + "' already registered.");
    }
  }

  @NotNull
  public LibraryTable registerLibraryTable(final String customLevel) {
    LibraryTable table = new LibraryTableBase() {
      public String getTableLevel() {
        return customLevel;
      }

      public LibraryTablePresentation getPresentation() {
        return new LibraryTablePresentation() {
          public String getDisplayName(boolean plural) {
            return customLevel;
          }

          public String getDescription() {
            throw new UnsupportedOperationException("Method getDescription is not yet implemented in " + getClass().getName());
          }

          public String getLibraryTableEditorTitle() {
            throw new UnsupportedOperationException("Method getLibraryTableEditorTitle is not yet implemented in " + getClass().getName());
          }
        };
      }

      public boolean isEditable() {
        return false;
      }
    };

    registerLibraryTable(table);
    return table;
  }

  public List<LibraryTable> getCustomLibraryTables() {
    return new SmartList<LibraryTable>(myLibraryTables.values());
  }

  public void dispose() {
    myLibraryTables.clear();
  }
}